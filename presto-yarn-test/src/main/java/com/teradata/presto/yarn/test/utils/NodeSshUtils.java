/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.presto.yarn.test.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.teradata.tempto.context.State;
import com.teradata.tempto.ssh.SshClient;
import com.teradata.tempto.ssh.SshClientFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.CharMatcher.anyOf;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newHashSet;
import static com.teradata.presto.yarn.test.utils.TimeUtils.retryUntil;
import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class NodeSshUtils
        implements State
{
    private static final Logger log = LoggerFactory.getLogger(NodeSshUtils.class);

    private final SshClientFactory sshClientFactory;
    private final SshClient yarnSshClient;

    public NodeSshUtils(SshClientFactory sshClientFactory, SshClient yarnSshClient)
    {
        this.yarnSshClient = yarnSshClient;
        this.sshClientFactory = sshClientFactory;
    }

    public int countOfPrestoProcesses(String host)
    {
        return withSshClient(host, sshClient -> {
            String prestoProcessesCountRow = sshClient.command("ps aux | grep PrestoServer | grep -v grep || true").trim();
            int prestoProcessesCount = prestoProcessesCountRow.split("\n").length;
            if (StringUtils.isEmpty(prestoProcessesCountRow)) {
                prestoProcessesCount = 0;
            }

            log.info("Presto processes count on {}: {}", host, prestoProcessesCount);
            return prestoProcessesCount;
        });
    }

    public void killPrestoProcesses(String host)
    {
        runOnNode(host, singletonList("pkill -9 -f 'java.*PrestoServer.*'"));
        retryUntil(() -> countOfPrestoProcesses(host) == 0, TimeUnit.SECONDS.toMillis(10));
    }

    public long getPrestoJvmMemory(String host)
    {
        return withSshClient(host, sshClient -> {
            String prestoServerPid = sshClient.command("pgrep -f PrestoServer").trim();
            long prestoProcessJvm = parseLong(sshClient.command("jmap -heap " + prestoServerPid + " | grep capacity | awk 'NR == 1' | awk '{print $3}'"));
            log.info("Presto jvm memory " + host + ": " + prestoProcessJvm);
            return prestoProcessJvm;
        });
    }

    public String getPrestoJvmProcess(String host)
    {
        return withSshClient(host, sshClient -> sshClient.command("ps aux | grep PrestoServer | grep -v grep").trim());
    }

    public String createLabels(Map<String, String> labels)
    {
        return commandOnYarn("yarn rmadmin -addToClusterNodeLabels " + Joiner.on(",").join(newHashSet(labels.values())));
    }

    public void labelNodes(Map<String, String> labels)
    {
        waitForNodeManagers(labels.size());
        List<String> nodeIds = getNodeIds();

        Map<String, String> nodeToNodeIds = labels.keySet().stream()
                .collect(toMap(
                        node -> node,
                        node -> nodeIds.stream().filter(nodeId -> nodeId.contains(node)).findFirst().get()));

        String replaceLabelsArgument = labels.keySet().stream().map(node -> node + "," + labels.get(node)).reduce(joinOn(" ")).get();
        commandOnYarn("yarn rmadmin -replaceLabelsOnNode \'" + replaceLabelsArgument + "\'");
        commandOnYarn("yarn rmadmin -refreshQueues");

        checkThatLabelsAreSetCorrectly(labels, nodeToNodeIds);
    }

    private static BinaryOperator<String> joinOn(String separator)
    {
        return (first, second) -> first + separator + second;
    }

    private void checkThatLabelsAreSetCorrectly(Map<String, String> labels, Map<String, String> nodeToNodeIds)
    {
        String clusterNodeLabels = commandOnYarn("yarn queue -status default | grep 'Accessible Node Labels'");
        labels.values().forEach(label -> checkState(clusterNodeLabels.contains(label), "Cluster node labels '{}', does not contain label '{}'", clusterNodeLabels, label));
        labels.entrySet().stream().forEach(entry -> {
            String node = entry.getKey();
            String label = entry.getValue();
            String nodeLabels = commandOnYarn("yarn node -status " + nodeToNodeIds.get(node) + " | grep \'Node-Labels\'");
            checkState(nodeLabels.contains(label), "Node labels '{}' on node '{}' does not contain label '{}'", nodeLabels, node, label);
        });
    }

    private void waitForNodeManagers(int numberOfNodes)
    {
        log.info("Waiting for NodeManagers...");
        retryUntil(() -> getNodeIds().size() >= numberOfNodes, MINUTES.toMillis(2));
    }

    public List<String> getNodeIds()
    {
        return Stream.of(commandOnYarn("yarn node -list").split("\n"))
                .filter(line -> line.contains("RUNNING"))
                .map(line -> Splitter.on(anyOf(" \t")).omitEmptyStrings().trimResults().split(line).iterator().next())
                .collect(toList());
    }

    public String commandOnYarn(String command)
    {
        return yarnSshClient.command("source /etc/profile && " + command).trim();
    }

    public void runOnNode(String node, String command)
    {
        runOnNode(node, singletonList(command));
    }

    public List<String> runOnNode(String node, List<String> commands)
    {
        return withSshClient(node, sshClient -> {
            return commands.stream()
                    .map(sshClient::command)
                    .collect(toList());
        });
    }

    public <T> List<T> withSshClient(Collection<String> hosts, Function<SshClient, T> closure)
    {
        return hosts.stream()
                .map(host -> withSshClient(host, closure))
                .collect(toList());
    }

    public <T> T withSshClient(String host, Function<SshClient, T> function)
    {
        try (SshClient sshClient = sshClientFactory.create(host)) {
            return function.apply(sshClient);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<String> getName()
    {
        return Optional.empty();
    }
}

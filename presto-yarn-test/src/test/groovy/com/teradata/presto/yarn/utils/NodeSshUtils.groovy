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

package com.teradata.presto.yarn.utils

import com.teradata.tempto.context.State
import com.teradata.tempto.ssh.SshClient
import com.teradata.tempto.ssh.SshClientFactory
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import static com.google.common.base.Preconditions.checkState
import static com.google.common.collect.Sets.newHashSet
import static com.teradata.presto.yarn.utils.TimeUtils.retryUntil
import static java.util.concurrent.TimeUnit.MINUTES

@Slf4j
public class NodeSshUtils
        implements State
{

  private final SshClientFactory sshClientFactory;
  private final SshClient yarnSshClient

  public NodeSshUtils(SshClientFactory sshClientFactory, SshClient yarnSshClient)
  {
    this.yarnSshClient = yarnSshClient
    this.sshClientFactory = sshClientFactory
  }

  public int countOfPrestoProcesses(String host)
  {
    return withSshClient(host, { sshClient ->
      def prestoProcessesCountRow = sshClient.command("ps aux | grep PrestoServer | grep -v grep || true").trim()
      def prestoProcessesCount = prestoProcessesCountRow.split('\n').size()
      if (StringUtils.isEmpty(prestoProcessesCountRow)) {
        prestoProcessesCount = 0
      }
      log.info("Presto processes count on ${host}: ${prestoProcessesCount}")
      return prestoProcessesCount
    })
  }

  public void killPrestoProcesses(String host)
  {
    runOnNode(host, ["pkill -9 -f 'java.*PrestoServer.*'"])
  }

  public String getPrestoJvmMemory(String host)
  {
    return withSshClient(host, { sshClient ->
      def prestoServerPid = sshClient.command("pgrep -f PrestoServer").trim()
      def prestoProcessJvm = sshClient.command("jmap -heap ${prestoServerPid} | grep capacity | " +
              "awk 'NR == 1' | awk '{print \$4}' | cut -d '(' -f 2 | cut -d ')' -f 1").trim()
      log.info("Presto jvm memory ${host}: ${prestoProcessJvm}")
      return prestoProcessJvm
    })
  }

  public String createLabels(Map<String, String> labels)
  {
    commandOnYarn("yarn rmadmin -addToClusterNodeLabels ${newHashSet(labels.values()).join(',')}")
  }

  public void labelNodes(Map<String, String> labels)
  {
    waitForNodeManagers()
    List<String> nodeIds = getNodeIds()

    Map<String, String> nodeToNodeIds =  labels.keySet().collectEntries({ node ->
      [(node): nodeIds.find {
        it.contains(node)
      }]
    })

    String replaceLabelsArgument = labels.collect({ node, label ->
      return "${nodeToNodeIds[node]},${label}"
    }).join(' ')
    commandOnYarn("yarn rmadmin -replaceLabelsOnNode '${replaceLabelsArgument}'")
    commandOnYarn('yarn rmadmin -refreshQueues')

    checkThatLabelsAreSetCorrectly(labels, nodeToNodeIds)
  }

  private void checkThatLabelsAreSetCorrectly(Map<String, String> labels, Map<String, String> nodeToNodeIds)
  {
    def clusterNodeLabels = commandOnYarn("yarn queue -status default | grep 'Accessible Node Labels'")
    labels.values().each {
      checkState(clusterNodeLabels.contains(it), "Cluster node labels '{}', does not contain label '{}'", clusterNodeLabels, it)
    }
    labels.each { node, label ->
      def nodeLabels = commandOnYarn("yarn node -status ${nodeToNodeIds[node]} | grep 'Node-Labels'")
      checkState(nodeLabels.contains(label), "Node labels '{}' on node '{}' does not contain label '{}'", nodeLabels, node, label)
    }
  }

  private waitForNodeManagers()
  {
    retryUntil({
      getNodeIds().size() == 4
    }, MINUTES.toMillis(2))
  }

  private List<String> getNodeIds()
  {
    return commandOnYarn('yarn node -list')
            .split('\n')
            .findAll { it.contains('RUNNING') }
            .collect { it.split(' ')[0].trim() }
  }

  public String commandOnYarn(String command)
  {
    return yarnSshClient.command(command).trim()
  }

  public void runOnNode(String node, List<String> commands)
  {
    withSshClient(node, { SshClient sshClient ->
      commands.each { command ->
        sshClient.command(command).trim()
      }
    })
  }

  public <T> T withSshClient(String host, Closure<T> closure)
  {
    SshClient sshClient = sshClientFactory.create(host)
    try {
      return closure(sshClient)
    }
    finally {
      sshClient.close()
    }
  }

  @Override
  public Optional<String> getName() {
    return Optional.empty();
  }
}

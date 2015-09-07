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

import static com.google.common.collect.Sets.newHashSet

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
      def prestoProcessesCountRaw = runCommand(sshClient, "pgrep -f 'java.*PrestoServer.*' | wc -l")
      def prestoProcessesCount = Integer.parseInt(prestoProcessesCountRaw)
      prestoProcessesCount -= 1 // because pgrep finds itself
      log.info("Presto processes count on ${host}: ${prestoProcessesCount}")
      return prestoProcessesCount
    })
  }

  public void killPrestoProcesses(String host)
  {
    runOnNode(host, ["pkill -9 -f 'java.*PrestoServer.*'"])
  }

  public Map<String, String> labelNodes(Map<String, String> labels)
  {
    List<String> nodeIds = commandOnYarn('yarn node -list')
            .split('\n')
            .findAll { it.contains('RUNNING') }
            .collect { it.split(' ')[0].trim() }

    commandOnYarn("yarn rmadmin -addToClusterNodeLabels ${newHashSet(labels.values()).join(',')}")
    String replaceLabelsArgument = labels.collect({ node, label ->
      String nodeId = nodeIds.find { it.contains(node) }
      return "${nodeId},${label}"
    }).join(' ')
    commandOnYarn("yarn rmadmin -replaceLabelsOnNode '${replaceLabelsArgument}'")

    // just for debugging, to see (in logs) that label has ben set correctly
    commandOnYarn('yarn rmadmin -refreshQueues')
    commandOnYarn('yarn cluster -lnl')
    nodeIds.forEach({
      commandOnYarn("yarn node -status ${it}")
    })
  }

  public String commandOnYarn(String command)
  {
    return runCommand(yarnSshClient, command)
  }

  public void runOnNode(String node, List<String> commands)
  {
    withSshClient(node, { SshClient sshClient ->
      commands.each { command ->
        runCommand(sshClient, command)
      }
    })
  }

  private String runCommand(SshClient sshClient, String command)
  {
    log.info('Execution on {}@{}: {}', sshClient.user, sshClient.host, command)
    return sshClient.command(command).trim()
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

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
      def prestoProcessesCountRaw = sshClient.command("ps aux | grep PrestoServer | grep -v grep || true").trim()
      def prestoProcessesCount = prestoProcessesCountRaw.split('\n').size()
      if (StringUtils.isEmpty(prestoProcessesCountRaw)) {
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

  public void labelNodes(Map<String, String> labels)
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
    commandOnYarn('yarn rmadmin -refreshQueues')
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

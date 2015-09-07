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

package com.teradata.presto.yarn.fulfillment

import com.facebook.presto.jdbc.internal.guava.collect.ImmutableSet
import com.google.inject.Inject
import com.teradata.presto.yarn.utils.NodeSshUtils
import com.teradata.tempto.Requirement
import com.teradata.tempto.context.State
import com.teradata.tempto.fulfillment.RequirementFulfiller
import com.teradata.tempto.ssh.SshClient
import com.teradata.tempto.ssh.SshClientFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.inject.Named
import java.nio.file.Paths

import static com.teradata.presto.yarn.PrestoCluster.COORDINATOR_COMPONENT
import static com.teradata.presto.yarn.PrestoCluster.WORKER_COMPONENT
import static com.teradata.presto.yarn.slider.Slider.LOCAL_CONF_DIR

@RequirementFulfiller.AutoSuiteLevelFulfiller(priority = 1)
@CompileStatic
@Slf4j
public class PrerequisitesClusterFulfiller
        implements RequirementFulfiller
{

  private static Map<String, String> NODE_LABELS = [
          master: COORDINATOR_COMPONENT.toLowerCase(),
          slave1: WORKER_COMPONENT.toLowerCase(),
          slave2: WORKER_COMPONENT.toLowerCase(),
          slave3: WORKER_COMPONENT.toLowerCase()
  ]
  public static final String REMOTE_HADOOP_CONF_DIR = '/etc/hadoop/conf/'

  private final SshClientFactory sshClientFactory
  private final NodeSshUtils nodeSshUtils;

  @Inject
  public PrerequisitesClusterFulfiller(SshClientFactory sshClientFactory, @Named('yarn') SshClient yarnSshClient)
  {
    this.sshClientFactory = sshClientFactory
    this.nodeSshUtils = new NodeSshUtils(sshClientFactory, yarnSshClient)
  }

  @Override
  Set<State> fulfill(Set<Requirement> requirements)
  {
    nodeSshUtils.withSshClient('master', { SshClient sshClient ->
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'yarn', 'yarn-site.xml'), REMOTE_HADOOP_CONF_DIR)
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'yarn', 'capacity-scheduler.xml'), REMOTE_HADOOP_CONF_DIR)
    })

    runOnMaster([
            '/etc/init.d/hadoop-yarn-resourcemanager restart',
            "su - hdfs -c 'hadoop fs -mkdir -p /user/yarn'",
            "su - hdfs -c 'hadoop fs -chown yarn:yarn /user/yarn'",
    ])

    runOnAll([
            'yum upgrade openssl -y',
            'mkdir -p /var/presto',
            'chown yarn:yarn /var/presto',
            '/etc/init.d/hadoop-yarn-nodemanager start || true'
    ])

    nodeSshUtils.labelNodes(NODE_LABELS)

    return ImmutableSet.of(nodeSshUtils)
  }

  private void runOnMaster(List<String> commands)
  {
    nodeSshUtils.runOnNode('master', commands)
  }

  private void runOnAll(List<String> commands)
  {
    NODE_LABELS.keySet().each { String node ->
      nodeSshUtils.runOnNode(node, commands)
    }
  }

  @Override
  void cleanup()
  {
  }
}

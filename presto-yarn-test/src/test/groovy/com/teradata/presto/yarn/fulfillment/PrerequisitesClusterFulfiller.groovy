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

  @Inject
  @Named("cluster.master")
  private String master

  @Inject
  @Named("cluster.slaves")
  private String slaves

  private static final String REMOTE_HADOOP_CONF_DIR = '/etc/hadoop/conf/'
  private static final String RESTART_RM_CMD = '/etc/init.d/hadoop-yarn-resourcemanager restart'

  private final SshClientFactory sshClientFactory
  private final NodeSshUtils nodeSshUtils

  @Inject
  public PrerequisitesClusterFulfiller(SshClientFactory sshClientFactory, @Named('yarn') SshClient yarnSshClient)
  {
    this.sshClientFactory = sshClientFactory
    this.nodeSshUtils = new NodeSshUtils(sshClientFactory, yarnSshClient)
  }

  @Override
  Set<State> fulfill(Set<Requirement> requirements)
  {
    setupYarnResourceManager()

    restartResourceManager()

    Map<String, String> node_labels = getNodeLabels()
    
    nodeSshUtils.createLabels(node_labels)

    useLabelsForSchedulerQueues()

    restartResourceManager()

    nodeSshUtils.labelNodes(node_labels)

    runOnAll([
            'mkdir -p /var/presto',
            'chown yarn:yarn /var/presto',
            '/etc/init.d/hadoop-yarn-nodemanager start || true'
    ])

    return ImmutableSet.of(nodeSshUtils)
  }

  private Map<String, String> getNodeLabels() {
    Map<String, String> node_labels = new HashMap<String, String>()
    
    node_labels.put(master, COORDINATOR_COMPONENT.toLowerCase())
    String[] workerNodes = slaves.split(",")
    workerNodes.each { String worker ->
      node_labels.put(worker, WORKER_COMPONENT.toLowerCase())
    }
    return node_labels
  }

  private void setupYarnResourceManager()
  {
    nodeSshUtils.withSshClient(master, { SshClient sshClient ->
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'yarn', 'yarn-site.xml'), REMOTE_HADOOP_CONF_DIR)
    })

    runOnMaster([
            "su - hdfs -c 'hadoop fs -mkdir -p /user/yarn'",
            "su - hdfs -c 'hadoop fs -chown yarn:yarn /user/yarn'",
    ])
  }

  private void useLabelsForSchedulerQueues()
  {
    nodeSshUtils.withSshClient(master, { SshClient sshClient ->
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'yarn', 'capacity-scheduler.xml'), REMOTE_HADOOP_CONF_DIR)
    })
  }

  private void restartResourceManager()
  {
    runOnMaster([RESTART_RM_CMD])
  }

  private void runOnMaster(List<String> commands)
  {
    nodeSshUtils.runOnNode(master, commands)
  }

  private void runOnAll(List<String> commands)
  {
    runOnMaster(commands)

    String[] slaveNodes = slaves.split(",")
    slaveNodes.each { String node ->
      nodeSshUtils.runOnNode(node, commands)
    }
  }

  @Override
  void cleanup()
  {
  }
}

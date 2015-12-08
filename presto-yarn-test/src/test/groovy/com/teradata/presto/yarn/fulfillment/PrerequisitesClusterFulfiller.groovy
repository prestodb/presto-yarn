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
  private List<String> slaves

  @Inject
  @Named("cluster.prepared")
  private boolean prepared

  @Inject
  @Named("ssh.roles.yarn.password")
  private String yarnPassword

  private static final String REMOTE_HADOOP_CONF_DIR = '/etc/hadoop/conf/'
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
    if (prepared) {
      log.info("Skipping cluster prerequisites fulfillment")
      return ImmutableSet.of(nodeSshUtils)
    }

    runOnMaster(["echo '${yarnPassword}' | passwd --stdin yarn"] as List<String>)

    setupCgroup()

    setupYarnResourceManager()

    restartResourceManager()

    Map<String, String> node_labels = getNodeLabels()

    nodeSshUtils.createLabels(node_labels)

    useLabelsForSchedulerQueues()

    restartResourceManager()

    runOnAll([
            'mkdir -p /var/lib/presto',
            'chown yarn:yarn /var/lib/presto',
            '/etc/init.d/hadoop-yarn-nodemanager restart'
    ])

    nodeSshUtils.labelNodes(node_labels)

    return ImmutableSet.of(nodeSshUtils)
  }

  private Map<String, String> getNodeLabels() {
    Map<String, String> node_labels = new HashMap<String, String>()

    node_labels.put(master, COORDINATOR_COMPONENT.toLowerCase())
    slaves.each { String worker ->
      node_labels.put(worker, WORKER_COMPONENT.toLowerCase())
    }
    return node_labels
  }

  private void setupYarnResourceManager()
  {
    nodeSshUtils.withSshClient(allNodes, { SshClient sshClient ->
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'yarn', 'yarn-site.xml'), REMOTE_HADOOP_CONF_DIR)
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'yarn', 'container-executor.cfg'), REMOTE_HADOOP_CONF_DIR)
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
    runOnMaster(['/etc/init.d/hadoop-yarn-resourcemanager restart'])
  }

  private void setupCgroup()
  {
    runOnAll([
            'find / -name container-executor | xargs chown root:yarn',
            'find / -name container-executor | xargs chmod 6050'
    ])

    nodeSshUtils.withSshClient(allNodes, { SshClient sshClient ->
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'cgroup', 'cgrules.conf'), '/etc/')
      sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'cgroup', 'cgconfig.conf'), '/etc/')
    })

    runOnAll([
            '/etc/init.d/cgconfig restart',
            'chmod -R 777 /cgroup'
    ]);
  }

  private void runOnMaster(List<String> commands)
  {
    nodeSshUtils.runOnNode(master, commands)
  }

  private void runOnAll(List<String> commands)
  {
    allNodes.each { String node ->
      nodeSshUtils.runOnNode(node, commands)
    }
  }

  private List<String> getAllNodes() {
    return [master] + slaves as List<String>
  }

  @Override
  void cleanup()
  {
  }
}

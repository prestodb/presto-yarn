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

package com.teradata.presto.yarn

import com.google.inject.Inject
import com.teradata.tempto.ProductTest
import com.teradata.tempto.Requirement
import com.teradata.tempto.RequirementsProvider
import com.teradata.tempto.configuration.Configuration
import com.teradata.tempto.hadoop.hdfs.HdfsClient
import com.teradata.tempto.ssh.SshClient
import com.teradata.tempto.ssh.SshClientFactory
import groovy.util.logging.Slf4j
import org.testng.annotations.Test

import javax.inject.Named

import static PrestoCluster.COORDINATOR_COMPONENT
import static PrestoCluster.WORKER_COMPONENT
import static com.teradata.presto.yarn.fulfillment.SliderClusterFulfiller.SliderClusterRequirement.SLIDER_CLUSTER
import static org.assertj.core.api.Assertions.assertThat

@Slf4j
class PrestoClusterTest
        extends ProductTest
        implements RequirementsProvider
{

  public static final String TEMPLATE = 'appConfig.json'

  @Inject
  @Named('yarn')
  private SshClient yarnSshClient

  @Inject
  private HdfsClient hdfsClient

  @Inject
  public SshClientFactory sshClientFactory;

  @Test
  void 'single node - create and stop'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(yarnSshClient, hdfsClient, 'resources-singlenode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.waitForComponentsCount(COORDINATOR_COMPONENT, 1)

      prestoCluster.assertThatPrestoIsUpAndRunning()

      List<String> coordinatorHosts = prestoCluster.getComponentHosts(COORDINATOR_COMPONENT)
      assertThat(coordinatorHosts).hasSize(1)

      coordinatorHosts.each {
        assertThat(countOfPrestoProcesses(it)).isEqualTo(1)
      }

      prestoCluster.stop()

      coordinatorHosts.each {
        assertThat(countOfPrestoProcesses(it)).isEqualTo(0)
      }
    }
  }

  @Test
  void 'multi node - create and stop'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(yarnSshClient, hdfsClient, 'resources-multinode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.waitForComponentsCount(COORDINATOR_COMPONENT, 1)
      prestoCluster.waitForComponentsCount(WORKER_COMPONENT, 3)

      prestoCluster.assertThatPrestoIsUpAndRunning()

      List<String> coordinatorHosts = prestoCluster.getComponentHosts(COORDINATOR_COMPONENT)
      assertThat(coordinatorHosts).hasSize(1)

      List<String> workerHosts = prestoCluster.getComponentHosts(WORKER_COMPONENT)
      assertThat(workerHosts).hasSize(3)

      Collection<String> allHosts = coordinatorHosts + workerHosts
      allHosts.each {
        assertThat(countOfPrestoProcesses(it)).isEqualTo(1)
      }

      prestoCluster.stop()

      allHosts.each {
        assertThat(countOfPrestoProcesses(it)).isEqualTo(0)
      }
    }
  }

  private int countOfPrestoProcesses(String host)
  {
    SshClient sshClient = sshClientFactory.create(host);
    try {
      def prestoProcessesCount = Integer.parseInt(sshClient.command("pgrep -f 'java.*PrestoServer.*' | wc -l").trim())
      prestoProcessesCount -= 1 // because pgrep finds itself
      log.info("Presto processes count on ${host}: ${prestoProcessesCount}")
      return prestoProcessesCount
    }
    finally {
      sshClient.close()
    }
  }

  @Override
  Requirement getRequirements(Configuration configuration)
  {
    return SLIDER_CLUSTER;
  }
}

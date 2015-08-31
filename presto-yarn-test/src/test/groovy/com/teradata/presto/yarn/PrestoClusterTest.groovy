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
import groovy.transform.CompileStatic
import org.testng.annotations.Test

import javax.inject.Named

import static PrestoCluster.COORDINATOR_COMPONENT
import static PrestoCluster.WORKER_COMPONENT
import static com.teradata.presto.yarn.fulfillment.SliderClusterFulfiller.SliderClusterRequirement.SLIDER_CLUSTER

@CompileStatic
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

  @Test
  void 'single node'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(yarnSshClient, hdfsClient, 'resources-singlenode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning()
    }
  }

  @Test
  void 'multi node'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(yarnSshClient, hdfsClient, 'resources-multinode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.waitForComponentsCount(COORDINATOR_COMPONENT, 1)
      prestoCluster.waitForComponentsCount(WORKER_COMPONENT, 3)

      prestoCluster.assertThatPrestoIsUpAndRunning()
    }
  }

  @Override
  Requirement getRequirements(Configuration configuration)
  {
    return SLIDER_CLUSTER;
  }
}

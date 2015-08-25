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

import com.teradata.tempto.ProductTest
import com.teradata.tempto.Requirement
import com.teradata.tempto.RequirementsProvider
import com.teradata.tempto.configuration.Configuration
import groovy.transform.CompileStatic
import org.testng.annotations.Test

import static com.teradata.presto.yarn.PrestoClusterManager.COORDINATOR_COMPONENT
import static com.teradata.presto.yarn.PrestoClusterManager.WORKER_COMPONENT
import static com.teradata.presto.yarn.fulfillment.SliderClusterFulfiller.SliderClusterRequirement.SLIDER_CLUSTER
import static org.assertj.core.api.Assertions.assertThat

@CompileStatic
class PrestoClusterTest
        extends ProductTest
        implements RequirementsProvider
{

  public static final String TEMPLATE = 'appConfig.json'

  @Test
  void 'single node'()
  {
    PrestoClusterManager prestoClusterManager = new PrestoClusterManager('resources-singlenode.json', TEMPLATE)
    prestoClusterManager.withPrestoCluster {
      prestoClusterManager.assertThatPrestoIsUpAndRunning()
    }
  }

  @Test
  void 'multi node'()
  {
    PrestoClusterManager prestoClusterManager = new PrestoClusterManager('resources-multinode.json', TEMPLATE)
    prestoClusterManager.withPrestoCluster {
      prestoClusterManager.assertThatPrestoIsUpAndRunning()

      prestoClusterManager.waitForComponentsCount(COORDINATOR_COMPONENT, 1)
      prestoClusterManager.waitForComponentsCount(WORKER_COMPONENT, 3)

      def liveClusterStatus = prestoClusterManager.sliderClient.clusterDescription.status['live']

      Map<String, Map> coordinatorStatuses = liveClusterStatus[COORDINATOR_COMPONENT] as Map<String, Map>
      assertThat(coordinatorStatuses).hasSize(1)

      Map<String, Map> workerStatuses = liveClusterStatus[WORKER_COMPONENT] as Map<String, Map>
      assertThat(workerStatuses).hasSize(3)

    }
  }

  @Override
  Requirement getRequirements(Configuration configuration)
  {
    return SLIDER_CLUSTER;
  }
}

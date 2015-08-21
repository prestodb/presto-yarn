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

import groovy.transform.CompileStatic
import org.apache.slider.client.SliderClient
import org.junit.BeforeClass
import org.junit.Test

@CompileStatic
class MultiNodePrestoClusterIT
        extends PrestoClusterTestBase
{

  @BeforeClass
  static void setConfiguration()
  {
    setTemplate('appConfig.json')
    setResources('resources-multinode.json')
  }

  @Test
  void 'install and check if presto works'()
  {
    describe "Create a working presto multi-node cluster on $clusterName"

    SliderClient sliderClient = createPrestoCluster()

    assertThatPrestoIsUpAndRunning(sliderClient)

    def liveClusterStatus = sliderClient.clusterDescription.status['live']

    Map<String, Map> coordinatorStatuses = liveClusterStatus[COORDINATOR_COMPONENT] as Map<String, Map>
    assertTrue("Expected only one coordinator to be up and running", coordinatorStatuses.size() == 1)

    Map<String, Map> workerStatuses = liveClusterStatus[WORKER_COMPONENT] as Map<String, Map>
    assertTrue("Expected three workers to be up and running", workerStatuses.size() == 3)
  }

  @Override
  protected String getClusterName()
  {
    return 'multi_node_presto_cluster'
  }
}

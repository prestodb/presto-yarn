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
import org.springframework.jdbc.core.JdbcTemplate

@CompileStatic
class SingleNodePrestoClusterIT
        extends PrestoClusterTestBase
{

  @BeforeClass
  static void setConfiguration() {
    setTemplate('appConfig.json')
    setResources('resources-singlenode.json')
  }

  @Test
  void 'install and check if presto works'()
  {
    describe "Create a working presto single-node cluster on $clusterName"

    SliderClient sliderClient = createPrestoCluster()

    assertThatPrestoIsUpAndRunning(sliderClient)
  }

  @Override
  protected String getClusterName()
  {
    return 'single_node_presto_cluster'
  }
}

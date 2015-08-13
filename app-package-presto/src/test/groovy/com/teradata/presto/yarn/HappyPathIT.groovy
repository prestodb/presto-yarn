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

import groovy.util.logging.Slf4j
import org.apache.slider.api.ClusterDescription
import org.apache.slider.client.SliderClient
import org.apache.slider.funtest.framework.AgentCommandTestBase
import org.apache.slider.funtest.framework.SliderShell
import org.junit.After
import org.junit.Before
import org.junit.Test

@Slf4j
class HappyPathIT
        extends AgentCommandTestBase
{
  private static CLUSTER_NAME = 'presto_cluster'

  @Before
  public void setupCluster()
  {
    setupCluster(CLUSTER_NAME)
  }

  @After
  public void cleanup()
  {
    cleanup(CLUSTER_NAME)
  }

  @Test
  void 'happy path'()
  {
    describe "Create a working presto cluster on $CLUSTER_NAME"

    def path = buildClusterPath(CLUSTER_NAME)
    assert !clusterFS.exists(path)

    SliderShell shell = slider(EXIT_SUCCESS,
            [
                    ACTION_CREATE, CLUSTER_NAME,
                    ARG_TEMPLATE, APP_TEMPLATE,
                    ARG_RESOURCES, APP_RESOURCE
            ])

    logShell(shell)

    ensureApplicationIsUp(CLUSTER_NAME)

    //get a slider client against the cluster
    SliderClient sliderClient = bondToCluster(SLIDER_CONFIG, CLUSTER_NAME)
    ClusterDescription cd = sliderClient.clusterDescription
    assert CLUSTER_NAME == cd.name


    log.info("Connected via Client {}", sliderClient.toString())
  }
}

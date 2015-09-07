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
import com.teradata.presto.yarn.fulfillment.ImmutableNationTable
import com.teradata.presto.yarn.slider.Slider
import com.teradata.presto.yarn.utils.NodeSshUtils
import com.teradata.tempto.ProductTest
import com.teradata.tempto.Requires
import com.teradata.tempto.assertions.QueryAssert
import com.teradata.tempto.hadoop.hdfs.HdfsClient
import com.teradata.tempto.query.QueryResult
import groovy.util.logging.Slf4j
import org.testng.annotations.Test

import static PrestoCluster.COORDINATOR_COMPONENT
import static PrestoCluster.WORKER_COMPONENT
import static com.teradata.tempto.assertions.QueryAssert.Row.row
import static java.sql.JDBCType.BIGINT
import static org.assertj.core.api.Assertions.assertThat

@Slf4j
class PrestoClusterTest
        extends ProductTest
{

  private static final String TEMPLATE = 'appConfig.json'

  @Inject
  private HdfsClient hdfsClient

  @Inject
  private Slider slider

  @Inject
  private NodeSshUtils nodeSshUtils

  @Test
  void 'single node with stop'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-singlenode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(0)
      assertThatApplicationIsStoppable(prestoCluster, 0)
    }
  }

  @Test
  @Requires(ImmutableNationTable.class)
  void 'multi node - create and stop'()
  {

    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-multinode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(3)

      assertThatApplicationIsStoppable(prestoCluster, 3)
    }
  }

  @Test
  void 'multi node with placement'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-multinode-placement.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(3)

      prestoCluster.getComponentHosts(COORDINATOR_COMPONENT).each { host ->
        assertThat(host).contains('master')
      }
      prestoCluster.getComponentHosts(WORKER_COMPONENT).each { host ->
        assertThat(host).contains('slave')
      }

      assertThatCountFromNationWorks(prestoCluster, 'tpch.tiny.nation')
      assertThatCountFromNationWorks(prestoCluster, 'hive.default.nation')

      assertThatApplicationIsStoppable(prestoCluster, 3)
    }
  }

  private void assertThatCountFromNationWorks(PrestoCluster prestoCluster, String nationTable)
  {
    QueryResult queryResult = prestoCluster.runPrestoQuery("select count(*) from ${nationTable}")

    QueryAssert.assertThat(queryResult)
            .hasColumns(BIGINT)
            .containsExactly(
            row(25))
  }

  private void assertThatApplicationIsStoppable(PrestoCluster prestoCluster, int workersCount)
  {
    List<String> coordinatorHosts = prestoCluster.getComponentHosts(COORDINATOR_COMPONENT)
    assertThat(coordinatorHosts).hasSize(1)

    List<String> workerHosts = prestoCluster.getComponentHosts(WORKER_COMPONENT)
    assertThat(workerHosts).hasSize(workersCount)

    Collection<String> allHosts = coordinatorHosts + workerHosts
    allHosts.each {
      assertThat(nodeSshUtils.countOfPrestoProcesses(it)).isEqualTo(1)
    }

    prestoCluster.stop()

    allHosts.each {
      assertThat(nodeSshUtils.countOfPrestoProcesses(it)).isEqualTo(0)
    }
  }
}

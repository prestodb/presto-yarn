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
import static com.teradata.presto.yarn.utils.TimeUtils.retryUntil
import static java.util.concurrent.TimeUnit.MINUTES
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
  void 'single node presto app lifecycle'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-singlenode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(0)

      assertThatKilledProcessesRespawn(prestoCluster)

      assertThatApplicationIsStoppable(prestoCluster, 0)
    }
  }


  private void assertThatKilledProcessesRespawn(PrestoCluster prestoCluster)
  {
    String coordinatorHost = getCoordinatorHost(prestoCluster)
    int processesCount = nodeSshUtils.countOfPrestoProcesses(coordinatorHost)
    nodeSshUtils.killPrestoProcesses(coordinatorHost)

    assertThat(nodeSshUtils.countOfPrestoProcesses(coordinatorHost)).isZero()

    retryUntil({nodeSshUtils.countOfPrestoProcesses(coordinatorHost) == processesCount }, MINUTES.toMillis(2))
  }

  @Test
  @Requires(ImmutableNationTable.class)
  void 'multi node with placement'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-multinode-placement.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(3)

      assertThatKilledProcessesRespawn(prestoCluster)

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
    String coordinatorHost = getCoordinatorHost(prestoCluster)

    List<String> workerHosts = prestoCluster.getComponentHosts(WORKER_COMPONENT)
    assertThat(workerHosts).hasSize(workersCount)

    Collection<String> allHosts = workerHosts + coordinatorHost
    Map<String, Integer> prestoProcessesCountOnHosts = allHosts
            .groupBy {it}
            .collectEntries{ key, value -> [(key): value.size()]}
    prestoProcessesCountOnHosts.each { host, count ->
      assertThat(nodeSshUtils.countOfPrestoProcesses(host)).isEqualTo(count)
    }

    prestoCluster.stop()

    allHosts.each {
      assertThat(nodeSshUtils.countOfPrestoProcesses(it)).isEqualTo(0)
    }
  }

  private String getCoordinatorHost(PrestoCluster prestoCluster)
  {
    List<String> coordinatorHosts = prestoCluster.getComponentHosts(COORDINATOR_COMPONENT)
    assertThat(coordinatorHosts).hasSize(1)
    return coordinatorHosts[0]
  }
}

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

import static com.teradata.presto.yarn.utils.TimeUtils.retryUntil
import static com.teradata.tempto.assertions.QueryAssert.Row.row
import static java.sql.JDBCType.BIGINT
import static java.util.concurrent.TimeUnit.MINUTES
import static org.assertj.core.api.Assertions.assertThat

@Slf4j
class PrestoClusterTest
        extends ProductTest
{

  private static final String TEMPLATE = 'appConfig.json'
  private static final String JVM_HEAPSIZE = "1024.0MB"

  private static final long TIMEOUT = MINUTES.toMillis(2)

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

      assertThatAllProcessesAreRunning(prestoCluster)
      
      assertThatMemorySettingsAreCorrect(prestoCluster)

      assertThatKilledProcessesRespawn(prestoCluster)

      assertThatApplicationIsStoppable(prestoCluster)
    }
  }

  @Test
  void 'limit single node failures'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-singlenode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(0)

      String coordinatorHost = prestoCluster.coordinatorHost

      5.times {
        assertThat(nodeSshUtils.countOfPrestoProcesses(coordinatorHost)).isEqualTo(1)
        nodeSshUtils.killPrestoProcesses(coordinatorHost)
        assertThat(nodeSshUtils.countOfPrestoProcesses(coordinatorHost)).isZero()

        retryUntil({
          nodeSshUtils.countOfPrestoProcesses(coordinatorHost) == 1
        }, TIMEOUT)

        assertThat(prestoCluster.status().isPresent()).isTrue()
      }

      // presto cluster should fail after 5 failures in a row
      nodeSshUtils.killPrestoProcesses(coordinatorHost)
      retryUntil({
        !prestoCluster.status().isPresent()
      }, TIMEOUT)
    }
  }

  @Test
  @Requires(ImmutableNationTable.class)
  void 'multi node with placement'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-multinode.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(3)

      assertThatAllProcessesAreRunning(prestoCluster)

      assertThatKilledProcessesRespawn(prestoCluster)

      // check connector settings
      assertThatCountFromNationWorks(prestoCluster, 'tpch.tiny.nation')
      assertThatCountFromNationWorks(prestoCluster, 'hive.default.nation')

      // check placement policy
      assertThat(prestoCluster.coordinatorHost).contains('master')
      prestoCluster.workerHosts.each { host ->
        assertThat(host).contains('slave')
      }

      assertThatApplicationIsStoppable(prestoCluster, 3)
    }
  }

  @Test
  void 'multi node with placement - labeling subset of nodes - single cordinatoor@master'()
  {
    PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, 'resources-single-coordinator@master.json', TEMPLATE)
    prestoCluster.withPrestoCluster {
      prestoCluster.assertThatPrestoIsUpAndRunning(0)

      // check placement policy
      assertThat(prestoCluster.coordinatorHost).contains('master')
      assertThat(prestoCluster.workerHosts).isEmpty()
    }
  }

  private void assertThatMemorySettingsAreCorrect(PrestoCluster prestoCluster)
  {
    String coordinatorHost = prestoCluster.coordinatorHost
    def prestoJvmMemory = nodeSshUtils.getPrestoJvmMemory(coordinatorHost)

    assertThat(prestoJvmMemory).isEqualTo(JVM_HEAPSIZE)
  }

  private void assertThatAllProcessesAreRunning(PrestoCluster prestoCluster)
  {
    Map<String, Integer> prestoProcessesCountOnHosts = prestoCluster.allNodes
            .groupBy { it }
            .collectEntries { key, value -> [(key): value.size()] }

    log.info("Presto processes distribution: ${prestoProcessesCountOnHosts}")
    prestoProcessesCountOnHosts.each { host, count ->
      assertThat(nodeSshUtils.countOfPrestoProcesses(host)).isEqualTo(count)
    }
  }

  private void assertThatKilledProcessesRespawn(PrestoCluster prestoCluster)
  {
    String coordinatorHost = prestoCluster.coordinatorHost
    int processesCount = nodeSshUtils.countOfPrestoProcesses(coordinatorHost)
    nodeSshUtils.killPrestoProcesses(coordinatorHost)

    assertThat(nodeSshUtils.countOfPrestoProcesses(coordinatorHost)).isZero()

    retryUntil({
      nodeSshUtils.countOfPrestoProcesses(coordinatorHost) == processesCount
    }, TIMEOUT)
  }

  private void assertThatCountFromNationWorks(PrestoCluster prestoCluster, String nationTable)
  {
    QueryResult queryResult = prestoCluster.runPrestoQuery("select count(*) from ${nationTable}")

    QueryAssert.assertThat(queryResult)
            .hasColumns(BIGINT)
            .containsExactly(
            row(25))
  }

  private void assertThatApplicationIsStoppable(PrestoCluster prestoCluster)
  {
    def allNodes = prestoCluster.allNodes

    prestoCluster.stop()

    allNodes.each {
      assertThat(nodeSshUtils.countOfPrestoProcesses(it)).isEqualTo(0)
    }
  }
}

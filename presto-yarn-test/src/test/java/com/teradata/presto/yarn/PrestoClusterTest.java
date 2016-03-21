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
package com.teradata.presto.yarn;

import com.google.inject.Inject;
import com.teradata.presto.yarn.fulfillment.ImmutableNationTable;
import com.teradata.presto.yarn.slider.Slider;
import com.teradata.presto.yarn.utils.NodeSshUtils;
import com.teradata.tempto.BeforeTestWithContext;
import com.teradata.tempto.ProductTest;
import com.teradata.tempto.Requires;
import com.teradata.tempto.hadoop.hdfs.HdfsClient;
import com.teradata.tempto.query.QueryExecutor;
import com.teradata.tempto.query.QueryResult;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import javax.inject.Named;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.facebook.presto.hive.$internal.com.google.common.base.Preconditions.checkState;
import static com.teradata.presto.yarn.PrestoCluster.WORKER_COMPONENT;
import static com.teradata.presto.yarn.utils.TimeUtils.retryUntil;
import static com.teradata.tempto.assertions.QueryAssert.Row.row;
import static com.teradata.tempto.assertions.QueryAssert.assertThat;
import static java.sql.JDBCType.BIGINT;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.data.Offset.offset;

public class PrestoClusterTest
        extends ProductTest
{
    private final static Logger log = LoggerFactory.getLogger(PrestoCluster.class);

    private static final String APP_CONFIG_WITHOUT_CATALOG_TEMPLATE = "appConfig-test-no-catalog.json";
    private static final String APP_CONFIG_TEST_TEMPLATE = "appConfig-test.json";
    private static final String JVM_HEAPSIZE = "1024.0MB";
    private static final String JVM_ARGS = "-DHADOOP_USER_NAME=hdfs -Duser.timezone=UTC";
    private static final String ADDITIONAL_NODE_PROPERTY = "-Dplugin.dir=";
    private static final long TIMEOUT = MINUTES.toMillis(4);
    private static final long FLEX_RETRY_TIMEOUT = MINUTES.toMillis(10);

    @Inject
    private HdfsClient hdfsClient;
    @Inject
    private Slider slider;
    @Inject
    private NodeSshUtils nodeSshUtils;

    @Inject
    @Named("hive")
    private QueryExecutor hiveQueryExecutor;
    @Inject
    @Named("cluster.master")
    private String master;
    @Inject
    @Named("cluster.vcores")
    private int vcoresPerNode;
    @Inject
    @Named("cluster.slaves")
    private List<String> workers;

    @BeforeTestWithContext
    public void setUp()
    {
        int nodesCount = workers.size() + 1;
        if (nodeSshUtils.getNodeIds().size() == nodesCount) {
            log.info("All nodemanagers are running..Skip restart");
        }
        else {
            restartNodeManagers();
            retryUntil(() -> nodeSshUtils.getNodeIds().size() == nodesCount, MINUTES.toMillis(2));
        }
    }

    public void restartNodeManagers()
    {
        String restartNodeManagerCommand = "/etc/init.d/hadoop-yarn-nodemanager restart || true";
        nodeSshUtils.runOnNode(master, restartNodeManagerCommand);
        workers.forEach(worker -> nodeSshUtils.runOnNode(worker, restartNodeManagerCommand));

        nodeSshUtils.runOnNode(master, "/etc/init.d/hadoop-yarn-resourcemanager restart || true");
    }

    @Test
    public void singleNodePrestoAppLifecycle()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-singlenode.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            assertThatAllProcessesAreRunning(prestoCluster);

            assertThatMemorySettingsAreCorrect(prestoCluster);

            assertThatJvmArgsAreCorrect(prestoCluster);

            assertThatAdditionalPropertiesAreAdded(prestoCluster);

            assertThatKilledProcessesRespawn(prestoCluster);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    @Test
    public void singleNodePrestoAppMissingCatalog()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-singlenode.json", APP_CONFIG_WITHOUT_CATALOG_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            assertThatAllProcessesAreRunning(prestoCluster);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    @Test
    public void singleNodePrestoAppAddingPlugin()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-singlenode.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            assertThatAllProcessesAreRunning(prestoCluster);

            QueryExecutor queryExecutor = prestoCluster.getQueryExecutor();
            waitForNodesToBeActive(queryExecutor, prestoCluster);
            assertThat(queryExecutor.executeQuery(
                    "SELECT classify(features(1, 2), model) FROM " +
                            "(SELECT learn_classifier(labels, features) AS model FROM " +
                            "(VALUES ('cat', features(1, 2))) t(labels, features)) t2"))
                    .containsExactly(row("cat"));
        });
    }

    @Test
    public void limitSingleNodeFailures()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-singlenode-label.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            final String coordinatorHost = prestoCluster.getCoordinatorHost();

            for (int i = 0; i < 5; i++) {
                Assertions.assertThat(nodeSshUtils.countOfPrestoProcesses(coordinatorHost)).isEqualTo(1);
                nodeSshUtils.killPrestoProcesses(coordinatorHost);

                retryUntil(() -> nodeSshUtils.countOfPrestoProcesses(coordinatorHost) == 1, TIMEOUT);

                Assertions.assertThat(prestoCluster.status().isPresent()).isTrue();
            }

            // presto cluster should fail after 5 failures in a row
            nodeSshUtils.killPrestoProcesses(coordinatorHost);
            retryUntil(() -> !prestoCluster.status().isPresent(), TIMEOUT);
        });
    }

    @Test
    public void multiNodeWithPlacementLifecycle()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-multinode.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(workersCount());

            assertThatAllProcessesAreRunning(prestoCluster);

            assertThatKilledProcessesRespawn(prestoCluster);

            assertThatPrestoYarnContainersUsesCgroup(prestoCluster);

            // check placement policy
            Assertions.assertThat(prestoCluster.getCoordinatorHost()).contains(master);
            Assertions.assertThat(prestoCluster.getWorkerHosts()).containsAll(workers);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    private int workersCount()
    {
        return workers.size();
    }

    @Test
    @Requires(ImmutableNationTable.class)
    public void multiNodeWithPlacementCheckingConnectors()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-multinode.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(workersCount());

            QueryExecutor queryExecutor = prestoCluster.getQueryExecutor();
            waitForPrestoConnectors(queryExecutor, Arrays.asList("hive", "tpch"));
            waitForNodesToBeActive(queryExecutor, prestoCluster);
            assertThatCountFromNationWorks(queryExecutor, "tpch.tiny.nation");
            assertThatCountFromNationWorks(queryExecutor, "hive.default.nation");
            assertThatCountFromNationWorks(hiveQueryExecutor, "nation");
        });
    }

    public void waitForNodesToBeActive(final QueryExecutor queryExecutor, final PrestoCluster prestoCluster)
    {
        retryUntil(() ->
        {
            QueryResult result = queryExecutor.executeQuery("select count(*) from system.runtime.nodes");
            log.debug("Number of active nodes: " + String.valueOf(result.rows()));
            return result.rows().equals(singletonList(prestoCluster.getAllNodes().size()));
        }, TIMEOUT);
    }

    public void waitForPrestoConnectors(final QueryExecutor queryExecutor, List<String> connectors)
    {
        List<List<String>> connectorRows = connectors.stream()
                .map(Collections::singletonList)
                .collect(toList());
        retryUntil(() -> {
            QueryResult result = queryExecutor.executeQuery("select connector_id from system.metadata.catalogs");
            log.debug("Current presto connectors: " + result.rows());
            return result.rows().containsAll(connectorRows);
        }, TIMEOUT);
    }

    private void assertThatCountFromNationWorks(QueryExecutor queryExecutor, final String nationTable)
    {
        assertThat(queryExecutor.executeQuery("select count(*) from " + nationTable)).hasColumns(BIGINT).containsExactly(row(25));
    }

    @Test
    public void labelingSubsetOfNodesSingleCoordinatorAtMaster()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-single-coordinator@master.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            // check placement policy
            Assertions.assertThat(prestoCluster.getCoordinatorHost()).contains(master);
            Assertions.assertThat(prestoCluster.getWorkerHosts()).isEmpty();
        });
    }

    @Test
    public void flexSetOfWorkersMultinodeFlexWorker()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, "resources-multinode-single-worker.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(1);
            assertThatAllProcessesAreRunning(prestoCluster);

            checkState(workers.size() >= 2, "Number of slaves set in the test yaml configuration should be atleast 3");
            flexWorkersAndAssertThatComponentsAreRunning(2, prestoCluster);
            flexWorkersAndAssertThatComponentsAreRunning(1, prestoCluster);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    public void waitForWorkers(final int nodeCount, final String component, final PrestoCluster prestoCluster)
    {
        retryUntil(() -> {
            int liveContainers = prestoCluster.getLiveContainers(component);
            log.info("Number of live containers after 'flex'ing: " + liveContainers);
            return liveContainers == nodeCount;
        }, FLEX_RETRY_TIMEOUT);
    }

    private void flexWorkersAndAssertThatComponentsAreRunning(int workersCount, PrestoCluster prestoCluster)
    {
        prestoCluster.flex(WORKER_COMPONENT, workersCount);
        waitForWorkers(workersCount, WORKER_COMPONENT, prestoCluster);

        prestoCluster.waitForComponents(workersCount);
    }

    private void assertThatMemorySettingsAreCorrect(PrestoCluster prestoCluster)
    {
        String coordinatorHost = prestoCluster.getCoordinatorHost();
        String prestoJvmMemory = nodeSshUtils.getPrestoJvmMemory(coordinatorHost);

        Assertions.assertThat(prestoJvmMemory).isEqualTo(JVM_HEAPSIZE);
    }

    private void assertThatAdditionalPropertiesAreAdded(PrestoCluster prestoCluster)
    {
        String coordinatorHost = prestoCluster.getCoordinatorHost();
        String prestoProcess = nodeSshUtils.getPrestoJvmProcess(coordinatorHost);

        Assertions.assertThat(prestoProcess).contains(ADDITIONAL_NODE_PROPERTY);
    }

    private void assertThatJvmArgsAreCorrect(PrestoCluster prestoCluster)
    {
        String coordinatorHost = prestoCluster.getCoordinatorHost();
        String prestoProcess = nodeSshUtils.getPrestoJvmProcess(coordinatorHost);

        Assertions.assertThat(prestoProcess).contains(JVM_ARGS);
    }

    private void assertThatAllProcessesAreRunning(PrestoCluster prestoCluster)
    {
        log.info("Presto processes distribution: %s", prestoCluster.getAllNodes());
        prestoCluster.getAllNodes().forEach(node -> Assertions.assertThat(nodeSshUtils.countOfPrestoProcesses(node)).isEqualTo(1));
    }

    private void assertThatKilledProcessesRespawn(PrestoCluster prestoCluster)
    {
        final String coordinatorHost = prestoCluster.getCoordinatorHost();
        final int processesCount = nodeSshUtils.countOfPrestoProcesses(coordinatorHost);
        nodeSshUtils.killPrestoProcesses(coordinatorHost);

        retryUntil(() -> nodeSshUtils.countOfPrestoProcesses(coordinatorHost) == processesCount, TIMEOUT);
    }

    private void assertThatApplicationIsStoppable(PrestoCluster prestoCluster)
    {
        Collection<String> allNodes = prestoCluster.getAllNodes();

        prestoCluster.stop();

        log.debug("Checking if presto process is stopped");
        allNodes.forEach(node -> retryUntil(() -> nodeSshUtils.countOfPrestoProcesses(node) == 0, TIMEOUT));
    }

    private void assertThatPrestoYarnContainersUsesCgroup(PrestoCluster prestoCluster)
    {
        nodeSshUtils.withSshClient(prestoCluster.getAllNodes(), sshClient -> {
            List<Integer> cpuQuotas = linesToInts(sshClient.command("cat /cgroup/cpu/yarn/container*/cpu.cfs_quota_us"));
            List<Integer> cpuPeriods = linesToInts(sshClient.command("cat /cgroup/cpu/yarn/container*/cpu.cfs_period_us"));
            Assertions.assertThat(cpuPeriods.size()).isEqualTo(cpuQuotas.size());
            for (int i = 0; i < cpuQuotas.size(); i++) {
                double cpuUsed = (double) cpuQuotas.get(i) / cpuPeriods.get(i);
                Assertions.assertThat(cpuUsed).isCloseTo(1.0 / vcoresPerNode, offset(0.1));
            }
            return null;
        });
    }

    private List<Integer> linesToInts(String lines)
    {
        return Stream.of(lines.split("\n"))
                .map(line -> Integer.parseInt(line))
                .collect(toList());
    }
}

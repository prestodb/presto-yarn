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
package com.teradata.presto.yarn.test;

import com.google.inject.Inject;
import com.teradata.presto.yarn.test.fulfillment.ImmutableNationTable;
import com.teradata.presto.yarn.test.slider.Slider;
import com.teradata.presto.yarn.test.utils.NodeSshUtils;
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

import static com.google.common.base.Preconditions.checkState;
import static com.teradata.presto.yarn.test.PrestoCluster.WORKER_COMPONENT;
import static com.teradata.presto.yarn.test.utils.TimeUtils.retryUntil;
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
    private static final long JVM_HEAPSIZE = 1024 * 1024 * 1024;
    private static final String JVM_ARGS = "-DHADOOP_USER_NAME=hdfs -Duser.timezone=UTC";
    private static final String ADDITIONAL_NODE_PROPERTY = "-Dplugin.dir=";
    private static final long TIMEOUT = MINUTES.toMillis(4);
    private static final long FLEX_RETRY_TIMEOUT = MINUTES.toMillis(10);
    private static final String HDP2_3_QUARANTINE = "hdp2.3_quarantine";

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
    @Named("cluster.slaves")
    private List<String> workers;
    @Inject
    @Named("tests.slider.conf_dir")
    private String sliderConfDirPath;

    @BeforeTestWithContext
    public void setUp()
    {
        int nodesCount = workers.size() + 1;
        int actualNodesCount = nodeSshUtils.getNodeIds().size();
        if (actualNodesCount == nodesCount) {
            log.info("All nodemanagers are running..Skip restart");
        }
        else {
            log.info(
                    "There are {} nodemanagers are running, missing {}. Clearing Yarn cache and restarting nodemanagers.",
                    actualNodesCount,
                    nodesCount - actualNodesCount);
            nodeSshUtils.runOnNode(master, singletonList("rm -rf /tmp/hadoop-yarn/nm-local-dir"));
            restartYarn();
            retryUntil(() -> nodeSshUtils.getNodeIds().size() >= nodesCount, MINUTES.toMillis(2));
        }
    }

    public void restartYarn()
    {
        String restartNodeManagerCommand = "supervisorctl restart yarn-nodemanager";
        nodeSshUtils.runOnNode(master, restartNodeManagerCommand);
        workers.forEach(worker -> nodeSshUtils.runOnNode(worker, restartNodeManagerCommand));

        nodeSshUtils.runOnNode(master, "supervisorctl restart yarn-resourcemanager");
    }

    @Test
    public void singleNodePrestoAppLifecycle()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-singlenode.json", APP_CONFIG_TEST_TEMPLATE);
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
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-singlenode.json", APP_CONFIG_WITHOUT_CATALOG_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            assertThatAllProcessesAreRunning(prestoCluster);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    @Test
    public void singleNodePrestoAppAddingPlugin()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-singlenode.json", APP_CONFIG_TEST_TEMPLATE);
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

    @Test(groups = HDP2_3_QUARANTINE)
    public void limitSingleNodeFailures()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath,  "resources-singlenode-label.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(0);

            String coordinatorHost = prestoCluster.getCoordinatorHost();
            for (int i = 0; i < 5; i++) {
                Assertions.assertThat(nodeSshUtils.isPrestoProcessRunning(coordinatorHost)).isEqualTo(true);
                nodeSshUtils.killPrestoProcesses(coordinatorHost);

                Assertions.assertThat(prestoCluster.status().isPresent()).isTrue();
                retryUntil(() -> nodeSshUtils.isPrestoProcessRunning(coordinatorHost), TIMEOUT);
            }

            // presto cluster should fail after 5 failures in a row
            nodeSshUtils.killPrestoProcesses(coordinatorHost);
            retryUntil(() -> !prestoCluster.status().isPresent(), TIMEOUT);
        });
    }

    @Test
    public void multiNodeWithPlacementLifecycle()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-multinode.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(workersCount());

            assertThatAllProcessesAreRunning(prestoCluster);

            assertThatPrestoYarnContainersUsesCgroup(prestoCluster);

            // check placement policy
            Assertions.assertThat(prestoCluster.getCoordinatorHost()).contains(master);
            Assertions.assertThat(prestoCluster.getWorkerHosts()).containsAll(workers);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    @Test(groups = HDP2_3_QUARANTINE)
    public void multiNodeWithPlacementRespawn()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-multinode.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(workersCount());

            assertThatAllProcessesAreRunning(prestoCluster);

            assertThatKilledProcessesRespawn(prestoCluster);

            // check placement policy of respawned process
            Assertions.assertThat(prestoCluster.getCoordinatorHost()).contains(master);
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
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-multinode.json", APP_CONFIG_TEST_TEMPLATE);
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
    public void waitForNodesToBeActive(QueryExecutor queryExecutor, PrestoCluster prestoCluster)
    {
        retryUntil(() ->
        {
            QueryResult result = queryExecutor.executeQuery("select * from system.runtime.nodes");
            Collection<String> allNodes = prestoCluster.getAllNodes()   ;
            log.info("Active nodes: {}, presto nodes: {}", result.rows(), allNodes);
            result = queryExecutor.executeQuery("select count(*) from system.runtime.nodes");
            return result.rows().contains(singletonList((long)allNodes.size()));
        }, TIMEOUT);
    }

    public void waitForPrestoConnectors(QueryExecutor queryExecutor, List<String> connectors)
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

    private void assertThatCountFromNationWorks(QueryExecutor queryExecutor, String nationTable)
    {
        assertThat(queryExecutor.executeQuery("select count(*) from " + nationTable)).hasColumns(BIGINT).containsExactly(row(25));
    }

    @Test
    public void labelingSubsetOfNodesSingleCoordinatorAtMaster()
    {
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-single-coordinator@master.json", APP_CONFIG_TEST_TEMPLATE);
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
        PrestoCluster prestoCluster = new PrestoCluster(slider, hdfsClient, sliderConfDirPath, "resources-multinode-single-worker.json", APP_CONFIG_TEST_TEMPLATE);
        prestoCluster.withPrestoCluster(() -> {
            prestoCluster.assertThatPrestoIsUpAndRunning(1);
            assertThatAllProcessesAreRunning(prestoCluster);

            checkState(workers.size() >= 2, "Number of slaves set in the test yaml configuration should be atleast 3");
            flexWorkersAndAssertThatComponentsAreRunning(2, prestoCluster);
            flexWorkersAndAssertThatComponentsAreRunning(1, prestoCluster);

            assertThatApplicationIsStoppable(prestoCluster);
        });
    }

    public void waitForWorkers(int nodeCount, String component, PrestoCluster prestoCluster)
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
        long prestoJvmMemory = nodeSshUtils.getPrestoJvmMemory(coordinatorHost);

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
        log.info("Presto processes distribution: {}", prestoCluster.getAllNodes());
        prestoCluster.getAllNodes().forEach(node -> Assertions.assertThat(nodeSshUtils.isPrestoProcessRunning(node)).isEqualTo(true));
    }

    private void assertThatKilledProcessesRespawn(PrestoCluster prestoCluster)
    {
        String coordinatorHost = prestoCluster.getCoordinatorHost();
        nodeSshUtils.killPrestoProcesses(coordinatorHost);

        retryUntil(() -> nodeSshUtils.isPrestoProcessRunning(prestoCluster.getCoordinatorHost()), TIMEOUT);
    }

    private void assertThatApplicationIsStoppable(PrestoCluster prestoCluster)
    {
        Collection<String> allNodes = prestoCluster.getAllNodes();

        prestoCluster.stop();

        log.debug("Checking if presto process is stopped");
        allNodes.forEach(node -> retryUntil(() -> !nodeSshUtils.isPrestoProcessRunning(node), TIMEOUT));
    }

    private void assertThatPrestoYarnContainersUsesCgroup(PrestoCluster prestoCluster)
    {
        nodeSshUtils.withSshClient(prestoCluster.getAllNodes(), sshClient -> {
            String cgroupContainers = sshClient.command("ls /sys/fs/cgroup/cpu/yarn/container*");
            List<Integer> cpuQuotas = linesToInts(sshClient.command("cat /sys/fs/cgroup/cpu/yarn/container*/cpu.cfs_quota_us"));
            List<Integer> cpuPeriods = linesToInts(sshClient.command("cat /sys/fs/cgroup/cpu/yarn/container*/cpu.cfs_period_us"));
            log.info("CPU cgroup configuration: containers: {}, quota: {}, periods: {}", cgroupContainers, cpuQuotas, cpuPeriods);
            Assertions.assertThat(cpuPeriods.size()).isEqualTo(cpuQuotas.size());
            for (int i = 0; i < cpuQuotas.size(); i++) {
                double cpuUsed = (double) cpuQuotas.get(i) / cpuPeriods.get(i);
                Assertions.assertThat(cpuUsed).isCloseTo(1.0, offset(0.01));
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

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

import com.facebook.presto.jdbc.PrestoDriver;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import com.teradata.presto.yarn.test.slider.Slider;
import com.teradata.presto.yarn.test.slider.SliderStatus;
import com.teradata.presto.yarn.test.utils.SimpleJdbcQueryExecutor;
import com.teradata.tempto.assertions.QueryAssert;
import com.teradata.tempto.hadoop.hdfs.HdfsClient;
import com.teradata.tempto.query.QueryExecutionException;
import com.teradata.tempto.query.QueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkState;
import static com.teradata.presto.yarn.test.utils.Closures.withMethodHelper;
import static com.teradata.presto.yarn.test.utils.TimeUtils.retryUntil;
import static java.util.concurrent.TimeUnit.MINUTES;

public class PrestoCluster
{
    private static final Logger log = LoggerFactory.getLogger(PrestoCluster.class);

    public static final String COORDINATOR_COMPONENT = "COORDINATOR";
    public static final String WORKER_COMPONENT = "WORKER";
    public static final String APP_NAME = "presto_cluster";

    private final Path resource;
    private final Path template;
    private final Slider slider;
    private final HdfsClient hdfsClient;

    public PrestoCluster(Slider slider, HdfsClient hdfsClient, String sliderConfDir, String resource, String template)
    {
        this.hdfsClient = hdfsClient;
        this.slider = slider;
        this.resource = Paths.get(sliderConfDir, resource);
        this.template = Paths.get(sliderConfDir, template);
    }

    public void withPrestoCluster(Runnable closure)
    {
        withMethodHelper(this::create, closure, this::cleanup);
    }

    public void create()
    {
        cleanup();
        checkState(!hdfsClient.exist(".slider/cluster/" + APP_NAME));

        slider.create(APP_NAME, template, resource);
    }

    public void cleanup()
    {
        slider.cleanup(APP_NAME);
    }

    public void assertThatPrestoIsUpAndRunning(int workersCount)
    {
        waitForComponents(workersCount);

        QueryExecutor queryExecutor = waitForPrestoServer();
        QueryAssert.assertThat(queryExecutor.executeQuery("SELECT 1")).containsExactly(QueryAssert.Row.row(1));
    }

    public void waitForComponents(int workersCount)
    {
        waitForComponentsCount(COORDINATOR_COMPONENT, 1);
        waitForComponentsCount(WORKER_COMPONENT, workersCount);
    }

    private void waitForComponentsCount(final String component, final int expectedCount)
    {
        retryUntil(() -> getComponentHosts(component).size() == expectedCount, MINUTES.toMillis(3));
    }

    public List<String> getComponentHosts(String component)
    {
        Optional<SliderStatus> status = status();
        if (status.isPresent()) {
            return status.get().getLiveComponentsHost(component);
        }
        else {
            return ImmutableList.of();
        }
    }

    public Integer getLiveContainers(String component)
    {
        Optional<SliderStatus> status = status();
        if (status.isPresent()) {
            return status.get().getLiveContainers(component);
        }
        else {
            return 0;
        }
    }

    public QueryExecutor waitForPrestoServer()
    {
        QueryExecutor queryExecutor = getQueryExecutor();
        retryUntil(() -> isPrestoAccessible(queryExecutor), MINUTES.toMillis(5));
        return queryExecutor;
    }

    public QueryExecutor getQueryExecutor()
    {
        String url = "jdbc:presto://" + getCoordinatorHost() + ":8080";
        log.info("Waiting for Presto at connection url: " + url + "...");

        return new SimpleJdbcQueryExecutor(getPrestoConnection(url));
    }

    private Connection getPrestoConnection(String url)
    {
        PrestoDriver prestoDriver = new PrestoDriver();
        Properties properties = new Properties();
        properties.setProperty("user", "user");
        properties.setProperty("password", "password");

        try {
            return prestoDriver.connect(url, properties);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isPrestoAccessible(QueryExecutor queryExecutor)
    {
        try {
            log.debug("Trying to connect presto...");
            queryExecutor.executeQuery("SELECT 1");
            log.debug("Connected");
            return true;
        }
        catch (QueryExecutionException ex) {
            return false;
        }
    }

    public Optional<SliderStatus> status()
    {
        return slider.status(APP_NAME);
    }

    public void stop()
    {
        slider.stop(APP_NAME);
    }

    public void flex(String component_name, int component_count)
    {
        slider.flex(APP_NAME, component_name, component_count);
    }

    public Collection<String> getAllNodes()
    {
        return ImmutableList.<String>builder().addAll(getWorkerHosts()).add(getCoordinatorHost()).build();
    }

    public List<String> getWorkerHosts()
    {
        return getComponentHosts(WORKER_COMPONENT);
    }

    public String getCoordinatorHost()
    {
        String[] coordinatorHost = new String[1];
        retryUntil(() -> {
            List<String> componentHosts = getComponentHosts(COORDINATOR_COMPONENT);
            if (componentHosts.size() == 1) {
                coordinatorHost[0] = componentHosts.get(0);
                return true;
            }
            return false;
        }, MINUTES.toMillis(4));
        return coordinatorHost[0];
    }
}

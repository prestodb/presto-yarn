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

import com.facebook.presto.jdbc.PrestoDriver
import com.google.common.collect.ImmutableList
import com.teradata.presto.yarn.slider.Slider
import com.teradata.presto.yarn.slider.SliderStatus
import com.teradata.tempto.hadoop.hdfs.HdfsClient
import com.teradata.tempto.query.JdbcQueryExecutor
import com.teradata.tempto.query.QueryExecutionException
import com.teradata.tempto.query.QueryExecutor
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection

import static com.google.common.base.Preconditions.checkState
import static com.teradata.presto.yarn.utils.Closures.withMethodHelper
import static com.teradata.presto.yarn.utils.TimeUtils.retryUntil
import static com.teradata.tempto.assertions.QueryAssert.Row.row
import static com.teradata.tempto.assertions.QueryAssert.assertThat
import static java.util.concurrent.TimeUnit.MINUTES

@CompileStatic
@Slf4j
public class PrestoCluster
{
  public static final String COORDINATOR_COMPONENT = "COORDINATOR"
  public static final String WORKER_COMPONENT = "WORKER"
  public static final String PACKAGE_DIR = 'target/package/'
  public static final String APP_NAME = 'presto_cluster'

  private final Path resource;
  private final Path template;
  private final Slider slider
  private final HdfsClient hdfsClient

  public PrestoCluster(Slider slider, HdfsClient hdfsClient, String resource, String template)
  {
    this.hdfsClient = hdfsClient
    this.slider = slider
    this.resource = Paths.get(PACKAGE_DIR, resource)
    this.template = Paths.get(PACKAGE_DIR, template)
  }

  public void withPrestoCluster(Closure closure)
  {
    withMethodHelper(this.&create, closure, this.&cleanup)
  }

  public void create()
  {
    cleanup()
    checkState(!hdfsClient.exist(".slider/cluster/${APP_NAME}", 'yarn'))

    slider.create(APP_NAME, template, resource)
  }

  public void cleanup()
  {
    slider.cleanup(APP_NAME)
  }

  public void assertThatPrestoIsUpAndRunning(int workersCount)
  {
    waitForComponentsCount(COORDINATOR_COMPONENT, 1)
    waitForComponentsCount(WORKER_COMPONENT, workersCount)

    QueryExecutor queryExecutor = waitForPrestoServer()
    assertThat(queryExecutor.executeQuery('SELECT 1')).containsExactly(row(1))
  }

  private void waitForComponentsCount(String component, int expectedCount)
  {
    retryUntil({
      getComponentHosts(component).size() == expectedCount
    }, MINUTES.toMillis(2))
  }

  public List<String> getComponentHosts(String component)
  {
    Optional<SliderStatus> status = status()
    if (status.isPresent()) {
      return status.get().getLiveComponentsHost(component)
    }
    else {
      return ImmutableList.of()
    }
  }

  public QueryExecutor waitForPrestoServer()
  {
    QueryExecutor queryExecutor = queryExecutor

    retryUntil({ isPrestoAccessible(queryExecutor) }, MINUTES.toMillis(5))

    return queryExecutor
  }

  public QueryExecutor getQueryExecutor() {
    def url = "jdbc:presto://${coordinatorHost}:8080"
    log.info("Waiting for Presto at connection url: ${url}...")

    return new JdbcQueryExecutor(getPrestoConnection(url), url)
  }

  private Connection getPrestoConnection(String url)
  {
    PrestoDriver prestoDriver = new PrestoDriver()
    Properties properties = new Properties()
    properties.setProperty('user', 'user')
    properties.setProperty('password', 'password')

    return prestoDriver.connect(url, properties)
  }

  private boolean isPrestoAccessible(QueryExecutor queryExecutor)
  {
    try {
      log.debug("Trying to connect presto...")
      queryExecutor.executeQuery('SELECT 1')
      log.debug("Connected")
      return true
    }
    catch (QueryExecutionException ex) {
      return false
    }
  }

  public Optional<SliderStatus> status()
  {
    slider.status(APP_NAME)
  }

  public void stop()
  {
    slider.stop(APP_NAME)
  }

  public void flex(String component_name, int component_count)
  {
    slider.flex(APP_NAME, component_name, component_count)
  }

  public Collection<String> getAllNodes()
  {
    return workerHosts + coordinatorHost
  }

  public List<String> getWorkerHosts()
  {
    getComponentHosts(WORKER_COMPONENT)
  }

  public String getCoordinatorHost()
  {
    List<String> coordinatorHosts = getComponentHosts(COORDINATOR_COMPONENT)
    checkState(coordinatorHosts.size() == 1, "Expected only one coordinator to be up and running (got: %s)", coordinatorHosts)
    return coordinatorHosts[0]
  }
}

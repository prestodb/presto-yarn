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
import com.teradata.tempto.query.JdbcQueryExecutor
import com.teradata.tempto.query.QueryExecutionException
import com.teradata.tempto.query.QueryExecutor
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.slider.client.SliderClient
import org.apache.slider.common.params.Arguments
import org.apache.slider.common.params.SliderActions
import org.apache.slider.core.main.LauncherExitCodes
import org.apache.slider.funtest.framework.AgentCommandTestBase
import org.apache.slider.funtest.framework.SliderShell

import java.sql.Connection

import static com.google.common.base.Preconditions.checkState
import static com.google.common.collect.Iterables.getOnlyElement
import static com.teradata.presto.yarn.fulfillment.SliderClusterFulfiller.CLUSTER_NAME
import static com.teradata.presto.yarn.utils.AccessProtectedMethodsFromAgentCommandTestBase.cleanup
import static com.teradata.presto.yarn.utils.AccessProtectedMethodsFromAgentCommandTestBase.ensureApplicationIsUp
import static com.teradata.presto.yarn.utils.TimeUtils.retryUntil
import static com.teradata.tempto.assertions.QueryAssert.Row.row
import static com.teradata.tempto.assertions.QueryAssert.assertThat
import static java.util.concurrent.TimeUnit.MINUTES

@CompileStatic
@Slf4j
public class PrestoClusterManager
{
  public static final String COORDINATOR_COMPONENT = "COORDINATOR"
  public static final String WORKER_COMPONENT = "WORKER"

  private final String resource;
  private final String template;
  private final AgentCommandTestBase agentCommandTestBase

  public PrestoClusterManager(String resource, String template)
  {
    this.resource = "target/package/${resource}"
    this.template = "target/package/${template}"
    System.properties.setProperty("test.app.resource", resource)
    System.properties.setProperty("test.app.template", template)
    // AgentCommandTestBase can be created after system properties are set for template and resource
    this.agentCommandTestBase = new AgentCommandTestBase()
  }

  public void withPrestoCluster(Closure closure)
  {
    createPrestoCluster()
    try {
      closure()
    }
    finally {
      cleanupPrestoCluster()
    }
  }

  public void createPrestoCluster()
  {
    def path = agentCommandTestBase.buildClusterPath(CLUSTER_NAME)
    assert !agentCommandTestBase.clusterFS.exists(path)

    slider(SliderActions.ACTION_CREATE, CLUSTER_NAME,
            Arguments.ARG_TEMPLATE, template,
            Arguments.ARG_RESOURCES, resource
    )

    ensureApplicationIsUp(agentCommandTestBase, CLUSTER_NAME)
  }

  private void slider(String... args)
  {
    SliderShell shell = agentCommandTestBase.slider(LauncherExitCodes.EXIT_SUCCESS, args as List<String>)
    agentCommandTestBase.logShell(shell)
  }

  public void cleanupPrestoCluster()
  {
    cleanup(agentCommandTestBase, CLUSTER_NAME)
  }

  public QueryExecutor waitForPrestoServer()
  {
    waitForComponentsCount(COORDINATOR_COMPONENT, 1)

    Map<String, Map> coordinatorStatuses = sliderClient.clusterDescription.status['live'][COORDINATOR_COMPONENT] as Map<String, Map>
    checkState(coordinatorStatuses.size() == 1, "Expected only one coordinator to be up and running")
    String prestoCoordinatorHost = getOnlyElement(coordinatorStatuses.values())['host']

    def url = "jdbc:presto://${prestoCoordinatorHost}:8080"
    log.info("Presto connection url: ${url}")

    JdbcQueryExecutor queryExecutor = new JdbcQueryExecutor(getPrestoConnection(url), url)

    retryUntil({ isPrestoAccessible(queryExecutor) }, MINUTES.toMillis(3))

    return queryExecutor
  }

  private Connection getPrestoConnection(GString url)
  {
    PrestoDriver prestoDriver = new PrestoDriver()
    Properties properties = new Properties()
    properties.setProperty('user', 'user')
    properties.setProperty('password', 'password')

    return prestoDriver.connect(url, properties)
  }

  public SliderClient getSliderClient()
  {
    SliderClient sliderClient = agentCommandTestBase.bondToCluster(AgentCommandTestBase.SLIDER_CONFIG, CLUSTER_NAME)

    log.info("Connected via Client {}", sliderClient.toString())

    return sliderClient
  }

  private boolean isPrestoAccessible(QueryExecutor queryExecutor)
  {
    try {
      log.info("Trying to connect presto...")
      queryExecutor.executeQuery('SELECT 1')
      log.info("Connected")
      return true
    }
    catch (QueryExecutionException ex) {
      return false
    }
  }

  public void assertThatPrestoIsUpAndRunning()
  {
    QueryExecutor queryExecutor = waitForPrestoServer()
    assertThat(queryExecutor.executeQuery('SELECT 1')).containsExactly(row(1))
  }

  public void waitForComponentsCount(String component, int expectedCount)
  {
    agentCommandTestBase.waitForRoleCount(sliderClient, component, expectedCount, MINUTES.toMillis(2) as int)

  }
}

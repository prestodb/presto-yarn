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
import groovy.util.logging.Slf4j
import org.apache.slider.client.SliderClient
import org.apache.slider.funtest.framework.AgentCommandTestBase
import org.apache.slider.funtest.framework.SliderShell
import org.junit.After
import org.junit.Before
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource

import static com.google.common.base.Preconditions.checkState
import static com.google.common.collect.Iterables.getOnlyElement
import static com.teradata.presto.yarn.TimeUtils.retryUntil
import static java.util.concurrent.TimeUnit.MINUTES

@Slf4j
abstract class PrestoClusterTestBase
        extends AgentCommandTestBase
{
  protected static final String COORDINATOR_COMPONENT = "COORDINATOR"
  protected static final String WORKER_COMPONENT = "WORKER"

  static setResources(String resource)
  {
    System.properties.setProperty('test.app.resource', "target/test-classes/${resource}")
  }

  static setTemplate(String template)
  {
    System.properties.setProperty('test.app.template', "target/test-classes/${template}")
  }

  @Before
  public void setupCluster()
  {
    setupCluster(clusterName)
  }

  protected abstract String getClusterName()

  @After
  public void cleanup()
  {
    cleanup(clusterName)
  }

  protected SliderClient createPrestoCluster()
  {
    def path = buildClusterPath(clusterName)
    assert !clusterFS.exists(path)

    SliderShell shell = slider(EXIT_SUCCESS,
            [
                    ACTION_CREATE, clusterName,
                    ARG_TEMPLATE, APP_TEMPLATE,
                    ARG_RESOURCES, APP_RESOURCE
            ])

    logShell(shell)

    ensureApplicationIsUp(clusterName)

    SliderClient sliderClient = bondToCluster(SLIDER_CONFIG, clusterName)

    log.info("Connected via Client {}", sliderClient.toString())

    return sliderClient
  }

  protected JdbcTemplate waitForPrestoServer(SliderClient sliderClient)
  {
    waitForRoleCount(sliderClient, COORDINATOR_COMPONENT, 1, MINUTES.toMillis(2) as int)

    Map<String, Map> coordinatorStatuses = sliderClient.clusterDescription.status['live'][COORDINATOR_COMPONENT] as Map<String, Map>
    checkState(coordinatorStatuses.size() == 1, "Expected only one coordinator to be up and running")
    String prestoCoordinatorHost = getOnlyElement(coordinatorStatuses.values())['host']
    JdbcTemplate jdbcTemplate = new JdbcTemplate()

    def url = "jdbc:presto://${prestoCoordinatorHost}:8080"
    log.info("Presto connection url: ${url}")

    jdbcTemplate.dataSource = new SimpleDriverDataSource(
            new PrestoDriver(),
            url,
            'user',
            'password'
    )

    retryUntil(isPrestoAccessibleClosure(jdbcTemplate), MINUTES.toMillis(2))

    return jdbcTemplate
  }

  private Closure<Boolean> isPrestoAccessibleClosure(JdbcTemplate prestoJdbcTemplate)
  {
    return {
      try {
        log.info("Trying to connect presto...")
        prestoJdbcTemplate.queryForObject('SELECT 1', Integer)
        log.info("Connected")
        return true
      }
      catch (UncategorizedSQLException ex) {
        return false
      }
    }
  }

  protected void assertThatPrestoIsUpAndRunning(SliderClient sliderClient)
  {
    JdbcTemplate prestoJdbcTemplate = waitForPrestoServer(sliderClient)
    assertTrue(prestoJdbcTemplate.queryForObject('SELECT 1', Integer) == 1)
  }
}

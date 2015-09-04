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
import com.teradata.presto.yarn.slider.Slider
import com.teradata.presto.yarn.slider.SliderStatus
import com.teradata.tempto.hadoop.hdfs.HdfsClient
import com.teradata.tempto.query.JdbcQueryExecutor
import com.teradata.tempto.query.QueryExecutionException
import com.teradata.tempto.query.QueryExecutor
import com.teradata.tempto.query.QueryResult
import com.teradata.tempto.ssh.SshClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection

import static com.google.common.base.Preconditions.checkState
import static com.teradata.presto.yarn.fulfillment.SliderClusterFulfiller.PACKAGE_NAME
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
  private HdfsClient hdfsClient

  public PrestoCluster(SshClient sshClient, HdfsClient hdfsClient, String resource, String template)
  {
    this.hdfsClient = hdfsClient
    this.slider = new Slider(sshClient)
    this.resource = Paths.get(PACKAGE_DIR, resource)
    this.template = Paths.get(PACKAGE_DIR, template)
  }

  public void withPrestoCluster(Closure closure)
  {
    create()
    boolean clousureThrownException = true
    try {
      closure()
      clousureThrownException = false
    }
    finally {
      try {
        cleanup()
      }
      catch (RuntimeException e) {
        if (clousureThrownException) {
          log.error('Caught exception during presto cluster cleanup', e)
        }
        else {
          throw e
        }
      }
    }
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

  public QueryExecutor waitForPrestoServer()
  {
    JdbcQueryExecutor queryExecutor = getJdbcQueryExecutor()

    retryUntil({ isPrestoAccessible(queryExecutor) }, MINUTES.toMillis(5))

    return queryExecutor
  }

  private JdbcQueryExecutor getJdbcQueryExecutor() {
    waitForComponentsCount(COORDINATOR_COMPONENT, 1)

    List<String> coordinatorHosts = getComponentHosts(COORDINATOR_COMPONENT)
    checkState(coordinatorHosts.size() == 1, "Expected only one coordinator to be up and running")
    String prestoCoordinatorHost = coordinatorHosts[0]

    def url = "jdbc:presto://${prestoCoordinatorHost}:8080"
    log.info("Presto connection url: ${url}")

    JdbcQueryExecutor queryExecutor = new JdbcQueryExecutor(getPrestoConnection(url), url)
    return queryExecutor
  }

  public List<String> getComponentHosts(String component)
  {
    return status().getLiveComponentsHost(component)
  }

  private Connection getPrestoConnection(GString url)
  {
    PrestoDriver prestoDriver = new PrestoDriver()
    Properties properties = new Properties()
    properties.setProperty('user', 'user')
    properties.setProperty('password', 'password')

    return prestoDriver.connect(url, properties)
  }

  public QueryResult runPrestoQuery(String query)
  {
    JdbcQueryExecutor queryExecutor = getJdbcQueryExecutor()
    
    log.info("Trying to query presto...")
    QueryResult result = queryExecutor.executeQuery(query)
    log.info("Executed query " + query)
    return result
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
    retryUntil({
      try {
        status().getLiveComponentsHost(component).size() == expectedCount
      }
      catch (RuntimeException e) {
        log.warn('Unable to retrieve status, application could be not yet running', e)
        return false
      }
    }, MINUTES.toMillis(2))
  }

  public SliderStatus status()
  {
    slider.status(APP_NAME)
  }

  public void stop()
  {
    slider.stop(APP_NAME)
  }
}

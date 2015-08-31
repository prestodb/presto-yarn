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

package com.teradata.presto.yarn.fulfillment

import com.facebook.presto.jdbc.internal.guava.collect.ImmutableSet
import com.google.inject.Inject
import com.teradata.presto.yarn.slider.Slider
import com.teradata.tempto.Requirement
import com.teradata.tempto.context.State
import com.teradata.tempto.fulfillment.RequirementFulfiller
import com.teradata.tempto.ssh.SshClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.inject.Named
import java.nio.file.Path
import java.nio.file.Paths

import static com.teradata.presto.yarn.fulfillment.SliderClusterFulfiller.SliderClusterRequirement.SLIDER_CLUSTER

@RequirementFulfiller.AutoSuiteLevelFulfiller
@CompileStatic
@Slf4j
public class SliderClusterFulfiller
        implements RequirementFulfiller
{

  public static final String CLUSTER_NAME = 'presto_cluster'

  private static final Path SLIDER_BINARY = Paths.get('target/package/slider-assembly-0.80.0-incubating-all.zip')
  private static final Path PRESTO_PACKAGE = Paths.get('target/package/presto-yarn-package-1.0.0-SNAPSHOT.zip')

  public static enum SliderClusterRequirement
          implements Requirement {
    SLIDER_CLUSTER;
  }

  private final Slider slider

  @Inject
  public SliderClusterFulfiller(@Named('yarn') SshClient yarnSshClient)
  {
    this.slider = new Slider(yarnSshClient)
  }

  @Override
  Set<State> fulfill(Set<Requirement> requirements)
  {
    if (requirements.contains(SLIDER_CLUSTER)) {
      log.info('fulfilling slider cluster')
      slider.install(SLIDER_BINARY)

      slider.cleanupCluster(CLUSTER_NAME)
      slider.installLocalPackage(PRESTO_PACKAGE, CLUSTER_NAME)
    }

    return ImmutableSet.of()
  }

  @Override
  void cleanup()
  {
    slider.cleanupCluster(CLUSTER_NAME)
  }
}

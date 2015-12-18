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
import org.apache.commons.io.filefilter.WildcardFileFilter

import javax.inject.Named
import java.nio.file.Path
import java.nio.file.Paths

@RequirementFulfiller.AutoSuiteLevelFulfiller
@CompileStatic
@Slf4j
public class SliderClusterFulfiller
        implements RequirementFulfiller
{

  public static final String PACKAGE_NAME = 'PRESTO'

  private static final Path SLIDER_BINARY = Paths.get('target/package/slider-assembly-0.80.0-incubating-all.zip')
  @Inject
  @Named("tests.app_package.path")
  private String presto_package_path

  private final Slider slider

  @Inject
  public SliderClusterFulfiller(@Named('yarn') SshClient yarnSshClient)
  {
    this.slider = new Slider(yarnSshClient)
  }

  @Override
  Set<State> fulfill(Set<Requirement> requirements)
  {
    log.info('fulfilling slider cluster')
    slider.install(SLIDER_BINARY)
    
    Path presto_app_package = Paths.get(getPrestoAppPackagePath())
    log.info("Using Presto package from: " + presto_app_package)
    slider.installLocalPackage(presto_app_package, PACKAGE_NAME)

    return ImmutableSet.of(slider)
  }

  private String getPrestoAppPackagePath() {
    File dir = new File(presto_package_path)
    FileFilter fileFilter = new WildcardFileFilter("presto-yarn-package*.zip")
    File[] files = dir.listFiles((FileFilter) fileFilter)
    return files[0].getPath()
  }

  @Override
  void cleanup()
  {
    slider.uninstallPackage(PACKAGE_NAME)
  }
}

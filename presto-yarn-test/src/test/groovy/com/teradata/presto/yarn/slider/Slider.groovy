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

package com.teradata.presto.yarn.slider

import com.google.common.base.Joiner
import com.teradata.tempto.context.State
import com.teradata.tempto.process.CommandExecutionException
import com.teradata.tempto.ssh.SshClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.nio.file.Paths

import static com.teradata.presto.yarn.utils.FileDigesters.md5sum

@CompileStatic
@Slf4j
class Slider
        implements State
{
  private static final String SLIDER_REMOTE_CONF_DIR = 'slider-0.80.0-incubating/conf/'
  public static final String LOCAL_CONF_DIR = 'target/test-classes/conf'

  private final SshClient sshClient

  public Slider(SshClient sshClient)
  {
    this.sshClient = sshClient
  }

  public void install(Path sliderBinary)
  {
    if (isInstalled()) {
      log.info('Slider is already installed on cluster')
      return
    }

    sshClient.command("unzip ${upload(sliderBinary)}")

    sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'slider', 'log4j.properties'), SLIDER_REMOTE_CONF_DIR)
    sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'slider', 'slider-client.xml'), SLIDER_REMOTE_CONF_DIR)
    sshClient.upload(Paths.get(LOCAL_CONF_DIR, 'slider', 'slider-env.sh'), SLIDER_REMOTE_CONF_DIR)
  }

  private Path upload(Path path)
  {
    sshClient.upload(path, '.')
    return path.fileName
  }

  private boolean isInstalled()
  {
    try {
      action('help')
      return true
    }
    catch (CommandExecutionException e) {
      log.debug('Checking if slider is installed', e)
      return false
    }
  }

  public void installLocalPackage(Path clusterPackage, String packageName)
  {
    def remotePackage = uploadIfNeeded(clusterPackage)
    action("package --install --name ${packageName} --package ${remotePackage} --replacepkg")
  }

  public void uninstallPackage(String packageName)
  {
    action("package --delete --name ${packageName}")
  }

  private Path uploadIfNeeded(Path clusterPackage)
  {
    String localMd5sum = md5sum(clusterPackage)

    def packageName = clusterPackage.fileName
    String remoteMd5sum = sshClient.command("md5sum ${packageName} || echo 0").split(' ')[0]

    if (localMd5sum == remoteMd5sum) {
      log.info("Package ${packageName} is already uploaded. Md5sum checksums match.")
    }
    else {
      log.debug("md5sum checksums mismatch: ${localMd5sum}, ${remoteMd5sum}")
      upload(clusterPackage)
    }
    return packageName
  }

  public void cleanup(String appName)
  {
    try {
      stop(appName, true)
    }
    catch (CommandExecutionException e) {
      if (e.exitStatus == 69) {
        log.warn('Unable to stop cluster (it is not started)')
      }
      else {
        throw e
      }
    }
    try {
      action("destroy ${appName}")
    }
    catch (CommandExecutionException e) {
      log.warn('Unable to destroy cluster (is it not created?)', e)
    }
  }

  public void create(String appName, Path template, Path resource)
  {
    action("create ${appName} --template ${upload(template)} --resources ${upload(resource)}")
    action("exists ${appName} --live")
  }

  public Optional<SliderStatus> status(String appName)
  {
    try {
      action("status ${appName} --out status_file")
    }
    catch (CommandExecutionException e) {
      if (e.exitStatus == 70) {
        log.warn('Unable to retrieve status, application is not yet running')
        return Optional.empty()
      }
      else {
        throw e
      }
    }
    return Optional.of(new SliderStatus(sshClient.command("cat status_file")))
  }

  public void stop(String clusterName, boolean force = false)
  {
    def forceArgument = force ? '--force' : ''
    action("stop ${clusterName} --wait 10000 ${forceArgument}")
  }

  public void action(String... args)
  {
    sshClient.command('slider-0.80.0-incubating/bin/slider ' + Joiner.on(' ').join(args))
  }

  @Override
  public Optional<String> getName()
  {
    return Optional.empty();
  }
}

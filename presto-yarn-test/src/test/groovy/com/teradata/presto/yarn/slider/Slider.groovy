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
import com.teradata.tempto.ssh.SshClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.nio.file.Paths

import static com.teradata.presto.yarn.utils.FileDigesters.md5sum

@CompileStatic
@Slf4j
class Slider
{
  private static final String SLIDER_REMOTE_CONF_DIR = 'slider-0.80.0-incubating/conf/'
  private static final String SLIDER_LOCAL_CONF_DIR = 'target/test-classes/slider/conf/'

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

    command("unzip ${upload(sliderBinary)}")

    sshClient.upload(Paths.get(SLIDER_LOCAL_CONF_DIR, 'log4j.properties'), SLIDER_REMOTE_CONF_DIR)
    sshClient.upload(Paths.get(SLIDER_LOCAL_CONF_DIR, 'slider-client.xml'), SLIDER_REMOTE_CONF_DIR)
    sshClient.upload(Paths.get(SLIDER_LOCAL_CONF_DIR, 'slider-env.sh'), SLIDER_REMOTE_CONF_DIR)
  }

  private Path upload(Path path)
  {
    log.info("Uploading ${path}")
    sshClient.upload(path, '.')
    return path.fileName
  }

  private boolean isInstalled()
  {
    try {
      action('help')
      return true
    }
    catch (RuntimeException e) {
      log.debug('Checking if slider is installed', e)
      return false
    }
  }

  private String command(String... args)
  {
    String command = Joiner.on(' ').join(args)
    log.info('Execution on {}@{}: {}', sshClient.user, sshClient.host, command)

    return sshClient.execute(command)
  }

  public void installLocalPackage(Path clusterPackage, String clusterName)
  {
    def remotePackage = uploadIfNeeded(clusterPackage)
    action("package --install --name ${clusterName} --package ${remotePackage} --replacepkg")
  }

  private Path uploadIfNeeded(Path clusterPackage)
  {
    String localMd5sum = md5sum(clusterPackage)

    def packageName = clusterPackage.fileName
    String remoteMd5sum = command("md5sum ${packageName} || echo 0").split(' ')[0]

    if (localMd5sum == remoteMd5sum) {
      log.info("Package ${packageName} is already uploaded. Md5sum checksums match.")
    }
    else {
      upload(clusterPackage)
    }
    return packageName
  }

  public void cleanupCluster(String clusterName)
  {
    try {
      action("stop ${clusterName} --force --wait 10000")
      action("destroy ${clusterName}")
    }
    catch (RuntimeException e) {
      log.warn('Unable to cleanup cluster (is it not registered?)', e)
    }
  }

  public void create(String clusterName, Path template, Path resource)
  {
    action("create ${clusterName} --template ${upload(template)} --resources ${upload(resource)}")
    action("exists ${clusterName} --live")
  }

  public SliderStatus status(String clusterName)
  {
    String tempFile = 'status.' + command('date +%Y-%m-%dT%H:%M:%S')
    action("status ${clusterName} --out ${tempFile}")
    return new SliderStatus(command("cat ${tempFile}"))
  }

  public void action(String... args)
  {
    command('slider-0.80.0-incubating/bin/slider ' + Joiner.on(' ').join(args))
  }
}

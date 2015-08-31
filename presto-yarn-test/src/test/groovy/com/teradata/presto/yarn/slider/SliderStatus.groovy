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

import com.google.common.collect.ImmutableMap
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

@CompileStatic
public class SliderStatus
{
  private final Map<String, Object> status;

  public SliderStatus(String statusJson)
  {
    JsonSlurper jsonSlurper = new JsonSlurper()
    status = jsonSlurper.parseText(statusJson) as Map<String, Object>
  }

  public List<String> getLiveComponentsHost(String component)
  {
    Map<String, Map<String, String>> liveComponents = getLiveComponents(component)
    return liveComponents.values().collect { it['host'] }
  }

  public Map<String, Map<String, String>> getLiveComponents(String component)
  {
    if (liveStatus.containsKey(component)) {
      return liveStatus[component] as Map<String, Map<String, String>>
    } else {
      return ImmutableMap.of();
    }
  }

  public Map<String, Map<String, Object>> getLiveStatus()
  {
    return (status.status as Map<String, Object>).live as Map<String, Map<String, Object>>
  }
}

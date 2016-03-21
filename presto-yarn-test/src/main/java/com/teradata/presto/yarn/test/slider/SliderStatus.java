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
package com.teradata.presto.yarn.test.slider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.teradata.presto.yarn.test.utils.Streams.stream;

public class SliderStatus
{
    private final JsonNode status;

    public SliderStatus(String statusJson)
    {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            this.status = objectMapper.readTree(statusJson);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getLiveComponentsHost(String component)
    {
        return stream(getLiveComponents(component)).
                map(liveComponent -> liveComponent.path("host").asText())
                .collect(Collectors.toList());
    }

    private Iterator<JsonNode> getLiveComponents(String component)
    {
        return getLiveStatus().path(component).elements();
    }

    private JsonNode getLiveStatus()
    {
        return status.path("status").path("live");
    }

    public int getLiveContainers(String component)
    {
        return status.path("statistics").path(component).path("containers.live").asInt();
    }
}

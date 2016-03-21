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

import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class SliderStatusTest
{
    @Test
    public void testSliderStatus()
            throws IOException
    {
        String statusJson = IOUtils.toString(getClass().getResourceAsStream("/status_file"));

        SliderStatus sliderStatus = new SliderStatus(statusJson);

        assertThat(sliderStatus.getLiveComponentsHost("UNKNOWN")).isEmpty();
        assertThat(sliderStatus.getLiveContainers("UNKNOWN")).isEqualTo(0);

        assertThat(sliderStatus.getLiveContainers("COORDINATOR")).isEqualTo(1);
        assertThat(sliderStatus.getLiveComponentsHost("COORDINATOR")).containsExactly("kogut-vsphere-default-master");

        assertThat(sliderStatus.getLiveComponentsHost("WORKER")).containsExactly(
                "kogut-vsphere-default-slave2",
                "kogut-vsphere-default-slave1",
                "kogut-vsphere-default-slave3");
        assertThat(sliderStatus.getLiveContainers("WORKER")).isEqualTo(3);
    }
}

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
package com.teradata.presto.yarn.test.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class TimeUtils
{
    public static void retryUntil(Callable<Boolean> condition, long timeoutInMilliseconds)
    {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutInMilliseconds) {
            if (call(condition)) {
                return;
            }
            sleep();
        }

        throw new RuntimeException("exceeded timeout");
    }

    private static Boolean call(Callable<Boolean> condition)
    {
        try {
            return condition.call();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sleep()
    {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(4));
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private TimeUtils() {}
}

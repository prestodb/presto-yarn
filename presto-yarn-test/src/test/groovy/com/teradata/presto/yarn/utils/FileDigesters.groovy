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

package com.teradata.presto.yarn.utils

import java.nio.file.Path
import java.security.MessageDigest

/**
 * Utility class to help digest (calculate md5sum etc.) files data.
 */
class FileDigesters
{
  public static String md5sum(Path path)
  {
    MessageDigest messageDigest = MessageDigest.getInstance("MD5")
    byte[] buffer = new byte[4096]
    path.withInputStream { inputStream ->
      int read
      while ((read = inputStream.read(buffer)) > 0) {
        messageDigest.update(buffer, 0, read)
      }
    }
    return String.format("%032x",new BigInteger(1, messageDigest.digest()));
  }
}

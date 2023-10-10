/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devrel.gmscore.tools.apk.arsc

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

/** Provides utility methods for package names. */
object PackageUtils {

    const val PACKAGE_NAME_SIZE = 256

    /**
     * Reads the package name from the buffer and repositions the buffer to point directly after
     * the package name.
     * @param buffer The buffer containing the package name.
     * @param offset The offset in the buffer to read from.
     * @return The package name.
     */
    fun readPackageName(buffer: ByteBuffer, offset: Int): String {
        val data = buffer.array()
        var length = 0
        // Look for the null terminator for the string instead of using the entire buffer.
        // It's UTF-16 so check 2 bytes at a time to see if its double 0.
        for (i in offset until min(data.size, offset + PACKAGE_NAME_SIZE) step 2) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                length = i - offset
                break
            }
        }
        return String(data, offset, length, StandardCharsets.UTF_16LE).also {
            buffer.position(offset + PACKAGE_NAME_SIZE)
        }
    }

    /**
     * Writes the provided package name to the buffer in UTF-16.
     * @param buffer The buffer that will be written to.
     * @param packageName The package name that will be written to the buffer.
     */
    fun writePackageName(buffer: ByteBuffer, packageName: String) {
        val nameBytes = packageName.toByteArray(StandardCharsets.UTF_16LE)
        buffer.put(nameBytes, 0, nameBytes.size.coerceAtMost(PACKAGE_NAME_SIZE))
        if (nameBytes.size < PACKAGE_NAME_SIZE) {
            // pad out the remaining space with an empty array.
            buffer.put(ByteArray(PACKAGE_NAME_SIZE - nameBytes.size))
        }
    }
}

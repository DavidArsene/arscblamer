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

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Utility class for loading a resource table from an `apk`. */
object ArscUtils {

    private const val RESOURCES_ARSC = "resources.arsc"

    /** Get the resources.arsc resource table in the `apk`. */
    @Throws(IOException::class)
    fun getResources(apk: File): ResourceTableChunk {
        val resourceBytes = getFile(apk, RESOURCES_ARSC)
            ?: throw IOException("Unable to find $RESOURCES_ARSC in APK.")

        val chunks = BinaryResourceFile(resourceBytes).chunks
        check(chunks.size == 1) { "$RESOURCES_ARSC should only have one root chunk." }

        val resourceTable = chunks[0]
        check(resourceTable is ResourceTableChunk) { "$RESOURCES_ARSC root chunk must be a ResourceTableChunk." }

        return resourceTable
    }

    private fun getFile(apkFile: File, filename: String): ByteArray? {
        ZipFile(apkFile).use { apkZip ->
            val zipEntry = apkZip.getEntry(filename) ?: return null
            apkZip.getInputStream(zipEntry).use { return it.readAllBytes() }
        }
    }
}

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

import java.io.DataOutput
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Contains a list of package-id to package name mappings for any shared libraries used in this
 * [ResourceTableChunk]. The package-id's encoded in this resource table may be different
 * than the id's assigned at runtime
 */
class LibraryChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    /** The libraries used in this chunk (package id + name). */
    private val entries = MutableList(size = buffer.getInt()) {
        Entry(buffer, offset + headerSize + Entry.SIZE * it)
    }

    override fun getType() = Type.TABLE_LIBRARY

    override fun writeHeader(output: ByteBuffer) {
        output.putInt(entries.size)
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        for (entry in entries) {
            output.write(entry.toByteArray(options))
        }
    }

    /** A shared library package-id to package name entry. */
    private class Entry(buffer: ByteBuffer, offset: Int): SerializableResource() {

        /** The id assigned to the shared library at build time. */
        private val packageId: Int = buffer.getInt(offset)

        /** The package name of the shared library. */
        private val packageName: String = PackageUtils.readPackageName(buffer, offset + 4)

        companion object {
            /** Library entries only contain a package ID (4 bytes) and a package name. */
            const val SIZE = 4 + PackageUtils.PACKAGE_NAME_SIZE
        }

        @Throws(IOException::class)
        override fun toByteArray(options: Int): ByteArray {
            val buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(packageId)
            PackageUtils.writePackageName(buffer, packageName)
            return buffer.array()
        }
    }
}

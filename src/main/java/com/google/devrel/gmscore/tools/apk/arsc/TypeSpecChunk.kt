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

/** A chunk that contains a collection of resource entries for a particular resource data type. */
class TypeSpecChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    companion object {
        /** Flag indicating that a resource entry is public. */
        private const val SPEC_PUBLIC = 0x40000000
    }

    /** The (1-based) type id of the resource that this [TypeSpecChunk] has configuration masks for. */
    var id: Int = buffer.get().unsignedToInt()
        set(value) {
            check(value >= 1) // Ids are 1-based.
            field = value
        }

    init {
        buffer.position(buffer.position() + 3) // Skip 3 bytes for packing
    }

    /** Resource configuration masks. */
    @JvmField var resources = IntArray(size = buffer.getInt(), init = { buffer.getInt() })

    /** Returns the number of resource entries that this chunk has configuration masks for. */
    val resourceCount: Int
        get() = resources.size

    /** Returns the name of the type this chunk represents (e.g. string, attr, id). */
    val typeName: String
        get() = checkNotNull(packageChunk) {
            "$javaClass has no parent package."
        }.getTypeString(id)

    /** Returns the package enclosing this chunk, if any. Else, returns null. */
    val packageChunk: PackageChunk?
        get() {
            var chunk = parent
            while (chunk != null && chunk !is PackageChunk) {
                chunk = chunk.parent
            }
            return chunk as? PackageChunk
        }

    override fun getType() = Type.TABLE_TYPE_SPEC

    override fun writeHeader(output: ByteBuffer) {
        // id is an unsigned byte in the range [0-255]. It is guaranteed to be non-negative.
        // Because our output is in little-endian, we are making use of the 4 byte packing here
        output.putInt(id)
        output.putInt(resources.size)
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        val resourceMask = if (options and PRIVATE_RESOURCES != 0) SPEC_PUBLIC.inv() else 0.inv()
        for (resource in resources) {
            output.writeInt(resource and resourceMask)
        }
    }
}

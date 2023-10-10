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

/**
 * Represents an XML resource map chunk.
 *
 * This chunk maps attribute ids to the resource ids of the attribute resource that defines the
 * attribute (e.g. type, enum values, etc.).
 */
class XmlResourceMapChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    companion object {
        /** The size of a resource reference for `resources` in bytes. */
        private const val RESOURCE_SIZE = 4
    }

    /**
     * Contains a mapping of attributeID to resourceID. For example, the attributeID 2 refers to the
     * resourceID returned by `resources.get(2)`.
     */
    private val resources: List<Int> = run {
        val resourceCount = (originalChunkSize - headerSize) / RESOURCE_SIZE
        val offset = offset + headerSize
        buffer.mark().position(offset)

        MutableList(resourceCount) {
            buffer.getInt()
        }.also { buffer.reset() }
    }

    /**
     * Returns the resource ID that `attributeId` maps to iff [hasResourceId] returns
     * true for the given `attributeId`.
     */
    fun getResourceId(attributeId: Int): ResourceIdentifier {
        check(hasResourceId(attributeId)) { "Attribute ID is not a valid index." }
        return ResourceIdentifier(resources[attributeId])
    }

    /** Returns true if a resource ID exists for the given `attributeId`. */
    fun hasResourceId(attributeId: Int) = attributeId in resources.indices

    override fun getType() = Type.XML_RESOURCE_MAP

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        for (resource in resources) {
            output.writeInt(resource)
        }
    }
}

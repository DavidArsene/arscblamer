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
import java.util.*

/** Represents a chunk whose payload is a list of sub-chunks. */
abstract class ChunkWithChunks(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    protected val chunks = LinkedHashMap<Int, Chunk>().also {
        val start = this.offset + headerSize
        var offset = start
        val end = this.offset + originalChunkSize
        val position = buffer.position()
        buffer.position(start)
        while (offset < end) {
            val chunk = createChildInstance(buffer)
            it[offset] = chunk
            offset += chunk.originalChunkSize
        }
        buffer.position(position)
    }

    /**
     * Allows subclasses to decide how child instances should be instantiated, e.g., compressed chunks
     * might use a different method to extract compressed data first.
     *
     * @param buffer The buffer to read from
     * @return The child instance.
     */
    protected fun createChildInstance(buffer: ByteBuffer) = newInstance(buffer, this)

    /** Removes the `chunk` from the list of sub-chunks. */
    protected fun remove(chunk: Chunk) {
        val deleted = chunks.remove(chunk.offset)
        check(chunk === deleted) { "Can't remove ${chunk.javaClass}." }
    }

    protected fun add(chunk: Chunk) {
        val offset = if (chunks.isEmpty()) 0 else {
            val oldMax = Collections.max(chunks.keys)
            val oldChunk = checkNotNull(chunks[oldMax])
            oldMax + oldChunk.originalChunkSize
        }
        chunks[offset] = chunk
        check(chunk.parent === this)
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        for (chunk in chunks.values) {
            val chunkBytes = chunk.toByteArray(options)
            output.write(chunkBytes)
            writePad(output, chunkBytes.size)
        }
    }
}

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

import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/** Represents a generic chunk. */
abstract class Chunk protected constructor(
    buffer: ByteBuffer,
    /** The parent to this chunk, if any. A parent is a chunk whose payload contains this chunk. */
    val parent: Chunk?
) : SerializableResource() {

    /** Types of chunks that can exist. */
    // See https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/libs/androidfw/include/androidfw/ResourceTypes.h
    enum class Type(val code: Short) {
        NULL(0x0000),
        STRING_POOL(0x0001),
        TABLE(0x0002),
        XML(0x0003),
        XML_START_NAMESPACE(0x0100),
        XML_END_NAMESPACE(0x0101),
        XML_START_ELEMENT(0x0102),
        XML_END_ELEMENT(0x0103),
        XML_CDATA(0x0104),
        XML_RESOURCE_MAP(0x0180),
        TABLE_PACKAGE(0x0200),
        TABLE_TYPE(0x0201),
        TABLE_TYPE_SPEC(0x0202),
        TABLE_LIBRARY(0x0203),
        TABLE_OVERLAYABLE(0x204),
        TABLE_OVERLAYABLE_POLICY(0x205);

        companion object {
            private val map = entries.associateBy(Type::code)
            operator fun get(code: Short) = checkNotNull(map[code]) { "Unknown chunk type: $code" }
        }
    }

    /** Offset of this chunk from the start of the file. */
    val offset: Int = buffer.position() - 2

    /** Size of the chunk header in bytes. */
    val headerSize: Int = buffer.getShort().unsignedToInt()

    /**
     * The size of this chunk when it was first read from a buffer (headerSize + dataSize).
     * A chunk's size can deviate from this value when its data is modified
     * (e.g. adding an entry, changing a string).
     *
     * A chunk's current size can be determined from the length of the byte array returned from
     * [toByteArray].
     */
    val originalChunkSize: Int = buffer.getInt()

    /**
     * Allows overwriting what value gets written as the type of a chunk. Subclasses may use a
     * specialized type value schema.
     *
     * @return The type value for this chunk.
     */
    protected abstract fun getType(): Type

    /**
     * Reposition the buffer after this chunk. Use this at the end of a Chunk constructor.
     *
     * @param buffer The buffer to be repositioned.
     */
    protected fun seekToEndOfChunk(buffer: ByteBuffer) {
        buffer.position(offset + originalChunkSize)
    }

    /**
     * Writes the type and header size. We don't know how big this chunk will be (it could be
     * different since the last time we checked), so this needs to be passed in.
     *
     * @param output The buffer that will be written to.
     * @param chunkSize The total size of this chunk in bytes, including the header.
     */
    protected fun writeHeader(output: ByteBuffer, chunkSize: Int) {
        val start = output.position()
        output.putShort(getType().code)
        output.putShort(headerSize.toShort())
        output.putInt(chunkSize)
        writeHeader(output)
        val headerBytes = output.position() - start
        check(headerBytes == headerSize) {
            "Written header is wrong size. Got $headerBytes, want $headerSize"
        }
    }

    /**
     * Writes the remaining header (after the type, `headerSize`, and `chunkSize`).
     *
     * @param output The buffer that the header will be written to.
     */
    protected open fun writeHeader(output: ByteBuffer) {}

    /**
     * Writes the chunk payload. The payload is data in a chunk which is not in the first
     * `headerSize` bytes of the chunk.
     *
     * @param output The stream that the payload will be written to.
     * @param header The already-written header. This can be modified to fix payload offsets.
     * @param options The serialization options to be applied to the result.
     * @throws IOException Thrown if `output` could not be written to (out of memory).
     */
    @Throws(IOException::class)
    protected abstract fun writePayload(output: DataOutput, header: ByteBuffer, options: Int)

    /**
     * Converts this chunk into an array of bytes representation. Normally you will not need to
     * override this method unless your header changes based on the contents / size of the payload.
     */
    @Throws(IOException::class)
    override fun toByteArray(options: Int): ByteArray {
        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(header, 0) // The chunk size isn't known yet. This will be filled in later.
        val baos = ByteArrayOutputStream()
        LittleEndianDataOutputStream(baos).use { payload ->
            writePayload(payload, header, options)
        }
        val payloadBytes = baos.toByteArray()
        val chunkSize = headerSize + payloadBytes.size
        header.putInt(CHUNK_SIZE_OFFSET, chunkSize)

        // Combine results
        val result = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        result.put(header.array())
        result.put(payloadBytes)
        return result.array()
    }

    companion object {
        /** The byte boundary to pad chunks on. */
        const val PAD_BOUNDARY = 4

        /** The number of bytes in every chunk that describes chunk type, header size, and chunk size. */
        const val METADATA_SIZE = 8

        /** The number of bytes in every chunk that describes header size and chunk size, but not the type. */
        const val METADATA_SIZE_NO_TYPE = METADATA_SIZE - 2

        /** The offset in bytes, from the start of the chunk, where the chunk size can be found. */
        private const val CHUNK_SIZE_OFFSET = 4

        /**
         * Pads `output` until `currentLength` is on a 4-byte boundary.
         *
         * @param output The [DataOutput] that will be padded.
         * @param length The current length, in bytes, of `output`
         * @return The new length of `output`
         * @throws IOException Thrown if `output` could not be written to.
         */
        @JvmStatic
        @Throws(IOException::class)
        protected fun writePad(output: DataOutput, length: Int): Int {
            var currentLength = length
            while (currentLength % PAD_BOUNDARY != 0) {
                output.write(0)
                ++currentLength
            }
            return currentLength
        }
        /**
         * Creates a new chunk whose contents start at `buffer`'s current position.
         *
         * @param buffer A buffer positioned at the start of a chunk.
         * @param parent The parent to this chunk (or null if there's no parent).
         * @return new chunk
         */
        fun newInstance(buffer: ByteBuffer, parent: Chunk? = null): Chunk {
            return when (Type[buffer.getShort()]) {
                Type.STRING_POOL -> StringPoolChunk(buffer, parent)
                Type.TABLE -> ResourceTableChunk(buffer, parent)
                Type.XML -> XmlChunk(buffer, parent)
                Type.XML_START_NAMESPACE -> XmlNamespaceStartChunk(buffer, parent)
                Type.XML_END_NAMESPACE -> XmlNamespaceEndChunk(buffer, parent)
                Type.XML_START_ELEMENT -> XmlStartElementChunk(buffer, parent)
                Type.XML_END_ELEMENT -> XmlEndElementChunk(buffer, parent)
                Type.XML_CDATA -> XmlCdataChunk(buffer, parent)
                Type.XML_RESOURCE_MAP -> XmlResourceMapChunk(buffer, parent)
                Type.TABLE_PACKAGE -> PackageChunk(buffer, parent)
                Type.TABLE_TYPE -> TypeChunk(buffer, parent)
                Type.TABLE_TYPE_SPEC -> TypeSpecChunk(buffer, parent)
                Type.TABLE_LIBRARY -> LibraryChunk(buffer, parent)
                else -> UnknownChunk(buffer, parent)
            }.apply {
                seekToEndOfChunk(buffer)
            }
        }
    }
}

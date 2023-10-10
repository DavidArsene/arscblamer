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
 * A chunk whose contents are unknown. This is a placeholder until we add a proper chunk for the
 * unknown type.
 */
class UnknownChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    private val type: Type = Type[buffer.getShort(offset)]
    private val header = ByteArray(headerSize - METADATA_SIZE).also { buffer.get(it) }
    private val payload = ByteArray(originalChunkSize - headerSize).also { buffer.get(it) }

    override fun writeHeader(output: ByteBuffer) {
        output.put(header)
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        output.write(payload)
    }

    override fun getType() = type
}

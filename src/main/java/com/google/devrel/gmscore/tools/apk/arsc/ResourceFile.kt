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
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/** Given an arsc file, maps the contents of the file. */
class ResourceFile(buffer: ByteBuffer) : SerializableResource() {

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    /** The chunks contained in this resource file. */
    val chunks = ArrayList<Chunk>().also {
        while (buffer.remaining() > 0) {
            it.add(Chunk.newInstance(buffer))
        }
    }

    constructor(buf: ByteArray) : this(ByteBuffer.wrap(buf))

    @Throws(IOException::class)
    override fun toByteArray(options: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val output = DataOutputStream(baos)
        for (chunk in chunks) {
            output.write(chunk.toByteArray(options))
        }
        return baos.toByteArray()
    }
}

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

/** Represents an XML cdata node. */
class XmlCdataChunk(buffer: ByteBuffer, parent: Chunk?) : XmlNodeChunk(buffer, parent) {

    /** A string reference to a string containing the raw character data. */
    private val rawValueIndex: Int = buffer.getInt()

    /** A [BinaryResourceValue] instance containing the parsed value. */
    val resourceValue = BinaryResourceValue(buffer)

    /** Returns a string containing the raw character data of this chunk. */
    val rawValue: String
        get() = getString(rawValueIndex)

    override fun getType() = Type.XML_CDATA

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        output.writeInt(rawValueIndex)
        output.write(resourceValue.toByteArray())
    }
}

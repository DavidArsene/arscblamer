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

/** Represents the start/end of a namespace in an XML document. */
abstract class XmlNamespaceChunk(buffer: ByteBuffer, parent: Chunk?) : XmlNodeChunk(buffer, parent) {

    /** A string reference to the namespace prefix. */
    private val prefixIndex: Int = buffer.getInt()

    /** A string reference to the namespace URI. */
    private val uriIndex: Int = buffer.getInt()

    /** Returns the namespace prefix. */
    val prefix: String
        get() = getString(prefixIndex)

    /** Returns the namespace URI. */
    val uri: String
        get() = getString(uriIndex)

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        output.writeInt(prefixIndex)
        output.writeInt(uriIndex)
    }
}

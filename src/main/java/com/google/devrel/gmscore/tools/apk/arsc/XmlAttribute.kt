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

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Represents an XML attribute and value. */
data class XmlAttribute(

    /** A string reference to the namespace URI, or -1 if not present. */
    val namespaceIndex: Int,

    /** A string reference to the attribute name. */
    val nameIndex: Int,

    /** A string reference to a string containing the character value. */
    val rawValueIndex: Int,

    /** A [BinaryResourceValue] instance containing the parsed value. */
    val typedValue: BinaryResourceValue,

    /** The parent of this XML attribute; used for dereferencing the namespace and name. */
    val parent: XmlNodeChunk
) : SerializableResource() {

    companion object {
        /** The serialized size in bytes of an [XmlAttribute]. */
        const val SIZE = 12 + BinaryResourceValue.SIZE
    }

    /**
     * Creates a new [XmlAttribute] based on the bytes at the current `buffer` position.
     *
     * @param buffer A buffer whose position is at the start of a [XmlAttribute].
     * @param parent The parent chunk that contains this attribute; used for string lookups.
     */
    constructor(buffer: ByteBuffer, parent: XmlNodeChunk) : this(
        namespaceIndex = buffer.getInt(),
        nameIndex = buffer.getInt(),
        rawValueIndex = buffer.getInt(),
        typedValue = BinaryResourceValue(buffer),
        parent = parent
    )

    /** The namespace URI, or the empty string if not present. */
    val namespace: String
        get() = getString(namespaceIndex)

    /** The attribute name, or the empty string if not present. */
    val name: String
        get() = getString(nameIndex)

    /** The raw character value. */
    val rawValue: String
        get() = getString(rawValueIndex)

    private fun getString(index: Int) = parent.getString(index)

    override fun toByteArray(options: Int): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(namespaceIndex)
        buffer.putInt(nameIndex)
        buffer.putInt(rawValueIndex)
        buffer.put(typedValue.toByteArray(options))
        return buffer.array()
    }
}

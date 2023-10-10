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

/** Represents the beginning of an XML node. */
class XmlStartElementChunk(buffer: ByteBuffer, parent: Chunk?) : XmlNodeChunk(buffer, parent) {

    /** A string reference to the namespace URI, or -1 if not present. */
    private val namespaceIndex: Int = buffer.getInt()

    /** A string reference to the element name that this chunk represents. */
    private val nameIndex: Int = buffer.getInt()

    /** The offset to the start of the attributes payload. */
    private val attributeStart: Int = buffer.getShort().unsignedToInt()

    init {
        val attributeSize = buffer.getShort().unsignedToInt()
        check(attributeSize == XmlAttribute.SIZE) {
            "attributeSize is wrong size. Got $attributeSize, want ${XmlAttribute.SIZE}"
        }
    }

    /** The number of attributes in the original buffer. */
    private val attributeCount: Int = buffer.getShort().unsignedToInt()

    // The following indices are 1-based and need to be adjusted.
    /** The (0-based) index of the id attribute, or -1 if not present. */
    private val idIndex: Int = buffer.getShort().unsignedToInt() - 1

    /** The (0-based) index of the class attribute, or -1 if not present. */
    private val classIndex: Int = buffer.getShort().unsignedToInt() - 1

    /** The (0-based) index of the style attribute, or -1 if not present. */
    private val styleIndex: Int = buffer.getShort().unsignedToInt() - 1

    /** The XML attributes associated with this element. */
    private val _attributes: MutableList<XmlAttribute> = run {
        val offset = offset + headerSize + attributeStart
        buffer.mark().position(offset)

        MutableList(attributeCount) {
            XmlAttribute(buffer, this)
        }.also { buffer.reset() }
    }

    /**
     * Remaps all the attribute references using the supplied remapping. If an attribute has a
     * reference to a resourceId that is in the remapping keys, it will be updated with the
     * corresponding value from the remapping. All attributes that do not have reference to
     * a value in the remapping are left as is.
     * @param remapping The original and new resource ids.
     */
    fun remapReferences(remapping: Map<Int?, Int?>) {
        val newEntries: MutableMap<Int, XmlAttribute> = HashMap()
        var count = 0
        for (attribute in _attributes) {
            val (_, type, valueData) = attribute.typedValue
            if (type === ResourceValue.Type.REFERENCE) {
                if (ResourceIdentifier(valueData).packageId != 0x1) {
                    if (remapping.containsKey(valueData)) {
                        val data = checkNotNull(remapping[valueData])
                        val newAttribute = XmlAttribute(
                            attribute.namespaceIndex,
                            attribute.nameIndex,
                            attribute.rawValueIndex,
                            attribute.typedValue.copy(data = data),
                            attribute.parent
                        )
                        newEntries[count] = newAttribute
                    }
                }
            }
            count++
        }
        for ((key, value) in newEntries) {
            _attributes[key] = value
        }
    }

    /** Returns the namespace URI, or the empty string if not present. */
    val namespace: String
        get() = getString(namespaceIndex)

    /** Returns the element name that this chunk represents. */
    val name: String
        get() = getString(nameIndex)

    /** Returns an unmodifiable list of this XML element's attributes. */
    val attributes: List<XmlAttribute>
        get() = Collections.unmodifiableList(_attributes)

    override fun getType() = Type.XML_START_ELEMENT

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        output.writeInt(namespaceIndex)
        output.writeInt(nameIndex)
        output.writeShort(XmlAttribute.SIZE) // attribute start
        output.writeShort(XmlAttribute.SIZE)
        output.writeShort(_attributes.size)
        output.writeShort(idIndex + 1)
        output.writeShort(classIndex + 1)
        output.writeShort(styleIndex + 1)
        for (attribute in _attributes) {
            output.write(attribute.toByteArray(options))
        }
    }
}

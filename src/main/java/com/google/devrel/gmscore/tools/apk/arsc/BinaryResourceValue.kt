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

/** Represents a single typed resource value. */
data class BinaryResourceValue(

    /** The length in bytes of this value. */
    val size: Int,

    /** The raw data type of this value. */
    val type: Type,

    /** The actual 4-byte value; interpretation of the value depends on `dataType`. */
    val data: Int
) : SerializableResource() {

    /** Resource type codes. */
    enum class Type(val code: Byte) {
        /** `data` is either 0 (undefined) or 1 (empty). */
        NULL(0x00),

        /** `data` holds a [ResourceTableChunk] entry reference. */
        REFERENCE(0x01),

        /** `data` holds an attribute resource identifier. */
        ATTRIBUTE(0x02),

        /** `data` holds an index into the containing resource table's string pool. */
        STRING(0x03),

        /** `data` holds a single-precision floating point number. */
        FLOAT(0x04),

        /** `data` holds a complex number encoding a dimension value, such as "100in". */
        DIMENSION(0x05),

        /** `data` holds a complex number encoding a fraction of a container. */
        FRACTION(0x06),

        /** `data` holds a dynamic [ResourceTableChunk] entry reference. */
        DYNAMIC_REFERENCE(0x07),

        /** `data` holds a dynamic attribute resource identifier. */
        DYNAMIC_ATTRIBUTE(0x08),

        /** `data` is a raw integer value of the form n..n. */
        INT_DEC(0x10),

        /** `data` is a raw integer value of the form 0xn..n. */
        INT_HEX(0x11),

        /** `data` is either 0 (false) or 1 (true). */
        INT_BOOLEAN(0x12),

        /** `data` is a raw integer value of the form #aarrggbb. */
        INT_COLOR_ARGB8(0x1c),

        /** `data` is a raw integer value of the form #rrggbb. */
        INT_COLOR_RGB8(0x1d),

        /** `data` is a raw integer value of the form #argb. */
        INT_COLOR_ARGB4(0x1e),

        /** `data` is a raw integer value of the form #rgb. */
        INT_COLOR_RGB4(0x1f);

        companion object {
            private val map = entries.associateBy(Type::code)
            operator fun get(code: Byte) = checkNotNull(map[code]) { "Unknown resource type: $code" }
        }
    }

    companion object {
        /** The serialized size in bytes of a [BinaryResourceValue]. */
        const val SIZE = 8
    }

    constructor(buffer: ByteBuffer) : this(
        size = buffer.getShort().unsignedToInt()
            .also { buffer.get() }, // Unused
        type = Type[buffer.get()],
        data = buffer.getInt()
    )

    override fun toByteArray(options: Int): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(size.toShort())
        buffer.put(0.toByte()) // Unused
        buffer.put(type.code)
        buffer.putInt(data)
        return buffer.array()
    }

    private val dataHexString
        get() = "0x%08x".format(data)

    override fun toString(): String {
        return when (type) {
            Type.NULL -> if (data == 0) "null" else "empty"
            Type.REFERENCE -> "ref($dataHexString)"
            Type.ATTRIBUTE -> "attr($dataHexString)"
            Type.STRING -> "string($dataHexString)"
            Type.FLOAT -> "float($data)"
            Type.DIMENSION -> "dimen($data)"
            Type.FRACTION -> "frac($data)"
            Type.DYNAMIC_REFERENCE -> "dynref($dataHexString)"
            Type.DYNAMIC_ATTRIBUTE -> "dynattr($dataHexString)"
            Type.INT_DEC -> "dec($data)"
            Type.INT_HEX -> "hex($dataHexString)"
            Type.INT_BOOLEAN -> "bool($data)"
            Type.INT_COLOR_ARGB8 -> "argb8($dataHexString)"
            Type.INT_COLOR_RGB8 -> "rgb8($dataHexString)"
            Type.INT_COLOR_ARGB4 -> "argb4($dataHexString)"
            Type.INT_COLOR_RGB4 -> "rgb4($dataHexString)"
        }
    }
}

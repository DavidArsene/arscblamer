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


/** Represents a string pool structure. */
class StringPoolChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    /** Number of strings in the original buffer. Not necessarily the number of strings in `strings` */
    private val originalStringCount: Int = buffer.getInt()

    /** Number of styles in the original buffer. Not necessarily the number of styles in `styles`. */
    private val originalStyleCount: Int = buffer.getInt()

    /** Flags. */
    private val flags: Int = buffer.getInt()

    /** Index from header of the string data. */
    private val stringsStart: Int = buffer.getInt()

    /** Index from header of the style data. */
    private val stylesStart: Int = buffer.getInt()

    /**
     * The strings ordered as they appear in the arsc file. e.g. strings.get(1234) gets
     * the 1235th string in the arsc file.
     */
    private var strings: MutableList<String> = run {
        var previousOffset = -1
        val offset = offset + stringsStart
        // After the header, we now have an array of offsets for the strings in this pool.
        MutableList(originalStringCount) {
            val stringOffset = offset + buffer.getInt()
            if (stringOffset <= previousOffset) {
                alwaysDedup = true
            }
            previousOffset = stringOffset
            ResourceString.decodeString(buffer, stringOffset, stringType)
        }
    }
    /**
     * These styles have a 1:1 relationship with the strings. For example, styles.get(3) refers to the
     * string at location strings.get(3). There are never more styles than strings (though there may
     * be less). Inside of that are all of the styles referenced by that string.
     */
    private var styles = run {
        val offset = offset + stylesStart
        // After the array of offsets for the strings in the pool, we have an offset for the styles
        // in this pool.
        MutableList(originalStyleCount) {
            val styleOffset = offset + buffer.getInt()
            StringPoolStyle(buffer, styleOffset, this)
        }
    }

    /** If we should dedup strings even when shrink is set to true. */
    @JvmField var alwaysDedup = false

    /**
     * Returns the 0-based index of the first occurrence of the given string, or -1 if the string is
     * not in the pool. This runs in O(n) time.
     *
     * @param string The string to check the pool for.
     * @return Index of the string, or -1 if not found.
     */
    fun indexOf(string: String) = strings.indexOf(string)

    /**
     * Returns a string at the given (0-based) index.
     *
     * @param index The (0-based) index of the string to return.
     * @throws IndexOutOfBoundsException If the index is out of range (index < 0 || index >= size()).
     */
    fun getString(index: Int) = strings[index]

    /**
     * Sets the string at the specific index.
     *
     * @param index The index of the string to update.
     * @param value The new value.
     */
    fun setString(index: Int, value: String) {
        strings[index] = value
    }

    /**
     * Adds a string to this string pool.
     *
     * @param value The string to add.
     * @return The (0-based) index of the string.
     */
    fun addString(value: String): Int {
        strings.add(value)
        return strings.size - 1
    }

    /** Returns the number of strings in this pool. */
    val stringCount: Int
        get() = strings.size

    /**
     * Remove from the input list any indexes of strings that are referenced by styles not in the
     * input. This is required because string A's style's span may refer to string B, and removing
     * string B in this scenario would leave a dangling reference from A.
     */
    private fun removeIndexesOfStringsWithNameIndexReferencesOutstanding(
        indexesToDelete: MutableSet<Int>
    ) {
        val indexesToSave: MutableSet<Int> = HashSet()
        for (i in styles.indices) {
            if (indexesToDelete.contains(i)) {
                // Style isn't going to survive deletion, so we don't care what its spans' [nameIndex]es
                // are pointing at.
                continue
            }
            for (span in styles[i].spans) {
                // Ensure we don't delete strings references from surviving styles.
                if (indexesToDelete.contains(span.nameIndex)) {
                    indexesToSave.add(span.nameIndex)
                }
            }
        }
        indexesToDelete.removeAll(indexesToSave)
    }

    /**
     * Delete from this pool strings whose (0-based) indexes are given. Styles (if any) are deleted
     * alongside their strings. Return an array whose i'th element is the new index of the string
     * that previously lived at index |i|, or -1 if that string was deleted.
     */
    fun deleteStrings(indexesToDelete: SortedSet<Int>): IntArray {
        val previousStringCount = strings.size
        val previousStyleCount = styles.size
        removeIndexesOfStringsWithNameIndexReferencesOutstanding(indexesToDelete)
        val result = IntArray(previousStringCount)
        var resultIndex = -1 // The index of the last value added to result.
        var offset = 0 // The offset shift the result by (number of deleted strings so far).
        val newStrings: MutableList<String> = ArrayList()
        val newStyles: MutableList<StringPoolStyle> = ArrayList()
        for (index in indexesToDelete) {
            for (i in resultIndex + 1 until index) {
                result[i] = i - offset
                newStrings.add(strings[i])
                if (i < previousStyleCount) {
                    newStyles.add(styles[i])
                }
            }
            result[index] = -1
            ++offset
            resultIndex = index
        }
        // Fill in the rest of the offsets
        for (i in resultIndex + 1 until previousStringCount) {
            result[i] = i - offset
            newStrings.add(strings[i])
            if (i < previousStyleCount) {
                newStyles.add(styles[i])
            }
        }
        strings = newStrings
        styles = fixUpStyles(newStyles, result)
        return result
    }

    /**
     * Returns a style at the given (0-based) index.
     *
     * @param index The (0-based) index of the style to return.
     * @throws IndexOutOfBoundsException If the index is out of range (index < 0 || index >= size()).
     */
    fun getStyle(index: Int) = styles[index]

    /** Returns the number of styles in this pool. */
    fun getStyleCount() = styles.size

    /** Returns the type of strings in this pool. */
    val stringType: ResourceString.Type
        get() = if (isUTF8) ResourceString.Type.UTF8 else ResourceString.Type.UTF16

    override fun getType() = Type.STRING_POOL

    /** Returns the number of bytes needed for offsets based on `strings` and `styles`. */
    private val offsetSize: Int
        get() = (strings.size + styles.size) * 4

    /**
     * True if this string pool contains strings in UTF-8 format. Otherwise, strings are in UTF-16.
     *
     * @return true if `strings` are in UTF-8; false if they're in UTF-16.
     */
    val isUTF8: Boolean
        get() = flags and UTF8_FLAG != 0

    /**
     * True if this string pool contains already-sorted strings.
     *
     * @return true if `strings` are sorted.
     */
    val isSorted: Boolean
        get() = flags and SORTED_FLAG != 0

    @Throws(IOException::class)
    private fun writeStrings(payload: DataOutput, offsets: ByteBuffer, options: Int): Int {
        var stringOffset = 0
        val used: MutableMap<String, Int> = HashMap() // Keeps track of strings already written
        val shouldShrink = options and SHRINK != 0 || alwaysDedup
        for (string in strings) {
            // Dedupe everything except stylized strings, unless shrink is true (then dedupe everything)
            if (shouldShrink && used.containsKey(string)) {
                offsets.putInt(used[string] ?: 0)
            } else {
                val encodedString = ResourceString.encodeString(string, stringType)
                payload.write(encodedString)
                used[string] = stringOffset
                offsets.putInt(stringOffset)
                stringOffset += encodedString.size
            }
        }

        // ARSC files pad to a 4-byte boundary. We should do so too.
        return writePad(payload, stringOffset)
    }

    @Throws(IOException::class)
    private fun writeStyles(payload: DataOutput, offsets: ByteBuffer, options: Int): Int {
        var styleOffset = 0
        val shouldShrink = options and SHRINK != 0 || alwaysDedup
        if (styles.size > 0) {
            val used: MutableMap<StringPoolStyle, Int> = HashMap() // Keeps track of bytes already written
            for (style in styles) {
                if (shouldShrink && used.containsKey(style)) {
                    offsets.putInt(used[style] ?: 0)
                } else {
                    val encodedStyle = style.toByteArray(options)
                    payload.write(encodedStyle)
                    used[style] = styleOffset
                    offsets.putInt(styleOffset)
                    styleOffset += encodedStyle.size
                }
            }
            // The end of the spans are terminated with another sentinel value
            payload.writeInt(StringPoolStyle.RES_STRING_POOL_SPAN_END)
            styleOffset += 4
            // TODO(acornwall): There appears to be an extra SPAN_END here... why?
            payload.writeInt(StringPoolStyle.RES_STRING_POOL_SPAN_END)
            styleOffset += 4
            styleOffset = writePad(payload, styleOffset)
        }
        return styleOffset
    }

    override fun writeHeader(output: ByteBuffer) {
        output.putInt(strings.size)
        output.putInt(styles.size)
        output.putInt(flags)
        // Aapt and aapt2 specify the stringsStart as header + offset size (b/31435539). It's non-zero
        // even if there are 0 strings in the string pool (unlike the style offset).
        output.putInt(headerSize + offsetSize)
        output.putInt(0) // Placeholder. The styles starting offset cannot be computed at this point.
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        val offsets = ByteBuffer.allocate(offsetSize).order(ByteOrder.LITTLE_ENDIAN)
        val baos = ByteArrayOutputStream()
        var stringOffset: Int

        // Write to a temporary payload so we can rearrange this and put the offsets first
        LittleEndianDataOutputStream(baos).use { payload ->
            stringOffset = writeStrings(payload, offsets, options)
            writeStyles(payload, offsets, options)
        }
        output.write(offsets.array())
        output.write(baos.toByteArray())
        if (styles.isNotEmpty()) {
            header.putInt(STYLE_START_OFFSET, headerSize + offsetSize + stringOffset)
        }
    }

    /**
     * Represents all of the styles for a particular string. The string is determined by its index
     * in [StringPoolChunk].
     */
    data class StringPoolStyle(val spans: List<StringPoolSpan>) : SerializableResource() {

        companion object {
            // Styles are a list of integers with 0xFFFFFFFF serving as a sentinel value.
            const val RES_STRING_POOL_SPAN_END = -0x1
        }

        @Throws(IOException::class)
        override fun toByteArray(options: Int): ByteArray {
            val baos = ByteArrayOutputStream()
            LittleEndianDataOutputStream(baos).use { payload ->
                for (span in spans) {
                    val encodedSpan = span.toByteArray(options)
                    check(encodedSpan.size == StringPoolSpan.SPAN_LENGTH) {
                        "Encountered a span of invalid length."
                    }
                    payload.write(encodedSpan)
                }
                payload.writeInt(RES_STRING_POOL_SPAN_END)
            }
            return baos.toByteArray()
        }

        constructor(buffer: ByteBuffer, offset: Int, parent: StringPoolChunk) : this(
            Collections.unmodifiableList(ArrayList<StringPoolSpan>().apply {
                var offset = offset
                var nameIndex = buffer.getInt(offset)
                while (nameIndex != RES_STRING_POOL_SPAN_END) {
                    add(StringPoolSpan(buffer, offset, parent));
                    offset += StringPoolSpan.SPAN_LENGTH
                    nameIndex = buffer.getInt(offset)
                }
            })
        )
    }

    /** Represents a styled span associated with a specific string. */
    data class StringPoolSpan(
        val nameIndex: Int,
        val start: Int,
        val stop: Int,
        val parent: StringPoolChunk
    ) : SerializableResource() {

        companion object {
            const val SPAN_LENGTH = 12
        }

        constructor(buffer: ByteBuffer, offset: Int, parent: StringPoolChunk) : this(
            nameIndex = buffer.getInt(offset),
            start = buffer.getInt(offset + 4),
            stop = buffer.getInt(offset + 8),
            parent
        )

        override fun toByteArray(options: Int): ByteArray {
            val buffer = ByteBuffer.allocate(SPAN_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(nameIndex)
            buffer.putInt(start)
            buffer.putInt(stop)
            return buffer.array()
        }
    }

    companion object {
        // These are the defined flags for the "flags" field of ResourceStringPoolHeader
        private const val SORTED_FLAG = 1 shl 0
        private const val UTF8_FLAG = 1 shl 8

        /** The offset from the start of the header that the stylesStart field is at. */
        private const val STYLE_START_OFFSET = 24

        /** Returns a list of `styles` with spans containing remapped string indexes by `remappedIndexes`. */
        private fun fixUpStyles(
            styles: List<StringPoolStyle>, remappedIndexes: IntArray
        ) = ArrayList<StringPoolStyle>(styles.size).apply {
            for (style in styles) {
                val newSpans = MutableList(style.spans.size) { i ->
                    val span = style.spans[i]
                    val newIndex = remappedIndexes[span.nameIndex]
                    check(newIndex >= 0)
                    span.copy(nameIndex = newIndex)
                }
                add(StringPoolStyle(Collections.unmodifiableList(newSpans)))
            }
        }
    }
}

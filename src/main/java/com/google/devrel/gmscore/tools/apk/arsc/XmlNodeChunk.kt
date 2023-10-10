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

/** The common superclass for the various types of XML nodes. */
abstract class XmlNodeChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    /** The line number in the original source at which this node appeared. */
    val lineNumber: Int = buffer.getInt()

    /** A string reference of this node's comment. If this is -1, then there is no comment. */
    private val commentIndex: Int = buffer.getInt()

    /** Returns true if this XML node contains a comment. Else, returns false. */
    fun hasComment() = commentIndex != -1

    /** Returns the comment associated with this node, if any. Else, returns the empty string. */
    val comment: String
        get() = getString(commentIndex)

    /**
     * An [XmlNodeChunk] does not know by itself what strings its indices reference. In order
     * to get the actual string, the first [XmlChunk] ancestor is found. The
     * [XmlChunk] ancestor should have a string pool which `index` references.
     *
     * @param index The index of the string.
     * @return String that the given `index` references, or empty string if `index` is -1.
     */
    fun getString(index: Int): String {
        if (index == -1) { // Special case. Packed XML files use -1 for "no string entry"
            return ""
        }
        var parent = parent
        while (parent !is XmlChunk) {
            parent = checkNotNull(parent?.parent) {
                "XmlNodeChunk did not have an XmlChunk parent."
            }
        }
        return parent.getString(index)
    }

    /**
     * An [XmlNodeChunk] and anything that is itself an [XmlNodeChunk] has a header size
     * of 16. Anything else is, interestingly, considered to be a payload. For that reason, this
     * method is final.
     */
    final override fun writeHeader(output: ByteBuffer) {
        output.putInt(lineNumber)
        output.putInt(commentIndex)
    }
}

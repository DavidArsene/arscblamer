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

/**
 * Represents a type chunk, which contains the resource values for a specific resource type and
 * configuration in a [PackageChunk]. The resource values in this chunk correspond to the
 * array of type strings in the enclosing [PackageChunk].
 *
 * A [PackageChunk] can have multiple of these chunks for different (configuration,
 * resource type) combinations.
 */
class TypeChunk(buffer: ByteBuffer, parent: Chunk?) : Chunk(buffer, parent) {

    companion object {
        /**
         * If set, the entries in this chunk are sparse and encode both the entry ID and offset into each
         * entry. Available on platforms >= O. Note that this only changes how the [TypeChunk] is
         * encoded / decoded.
         */
        private const val FLAG_SPARSE = 1 shl 0

        /** The size of a TypeChunk's header in bytes. */
        const val HEADER_SIZE = METADATA_SIZE + 12 + BinaryResourceConfiguration.SIZE
    }

    /** The (1-based) type identifier of the resource type this chunk is holding. */
    var id: Int = buffer.get().unsignedToInt()
        set(value) {
            check(value >= 1) // Ids are 1-based.
            // Ensure that there is a type defined for this id.
            check(checkNotNull(packageChunk).typeStringPool.stringCount >= value)
            field = value
        }

    /** Flags for a type chunk, such as whether or not this chunk has sparse entries. */
    private var flags: Int = buffer.get().unsignedToInt()

    init {
        buffer.position(buffer.position() + 2) // Skip 2 bytes (reserved)
    }

    /** The total number of resources of this type + configuration, including null entries. */
    @JvmField var totalEntryCount: Int = buffer.getInt()

    /** The offset (from `offset`) in the original buffer where `entries` start. */
    private val entriesStart: Int = buffer.getInt()

    /** The resource configuration that these resource entries correspond to. */
    @JvmField var configuration: BinaryResourceConfiguration = BinaryResourceConfiguration(buffer)

    /** A sparse list of resource entries defined by this chunk. */
    private val _entries: SortedMap<Int, Entry> = TreeMap<Int, Entry>().also {
        val offset = offset + entriesStart
        if (hasSparseEntries()) {
            for (i in 0 until totalEntryCount) {
                // Offsets are stored as (offset / 4u).
                // (See android::ResTable_sparseTypeEntry)
                val index = buffer.getShort().unsignedToInt()
                val entryOffset = buffer.getShort().unsignedToInt() * 4
                it[index] = Entry(buffer, offset + entryOffset, this, index)
            }
        } else { // dense entries
            for (i in 0 until totalEntryCount) {
                val entryOffset = buffer.getInt()
                if (entryOffset == Entry.NO_ENTRY) {
                    continue
                }
                it[i] = Entry(buffer, offset + entryOffset, this, i)
            }
        }
    }

    fun setEntries(entries: Map<Int, Entry>?, totalCount: Int) {
        this._entries.clear()
        this._entries.putAll(entries!!)
        totalEntryCount = totalCount
    }

    /** Returns true if the entries in this chunk are encoded in a sparse array. */
    fun hasSparseEntries(): Boolean = flags and FLAG_SPARSE != 0

    /**
     * If [hasSparseEntries] is true, this chunk's entries will be encoded in a sparse array. Else,
     * this chunk's entries will be encoded in a dense array.
     */
    fun setSparseEntries(sparseEntries: Boolean) {
        flags = flags and FLAG_SPARSE.inv() or if (sparseEntries) FLAG_SPARSE else 0
    }

    /** Returns the name of the type this chunk represents (e.g. string, attr, id). */
    val typeName: String
        get() = checkNotNull(packageChunk) {
            "$javaClass has no parent package."
        }.getTypeString(id)

    /** Returns a sparse list of 0-based indices to resource entries defined by this chunk. */
    val entries: SortedMap<Int, Entry>
        get() = Collections.unmodifiableSortedMap(_entries)

    /** Returns true if this chunk contains an entry for `resourceId`. */
    fun containsResource(resourceId: BinaryResourceIdentifier): Boolean {
        val packageId = checkNotNull(packageChunk).id
        val typeId = id
        return resourceId.packageId == packageId
                && resourceId.typeId == typeId
                && _entries.containsKey(resourceId.entryId)
    }

    /**
     * Overrides the entries in this chunk at the given index:entry pairs in `entries`. For
     * example, if the current list of entries is {0: foo, 1: bar, 2: baz}, and `entries` is {1:
     * qux, 3: quux}, then the entries will be changed to {0: foo, 1: qux, 2: baz}. If an entry has an
     * index that does not exist in the dense entry list, then it is considered a no-op for that
     * single entry.
     *
     * @param entries A sparse list containing index:entry pairs to override.
     */
    fun overrideEntries(entries: Map<Int, Entry?>) {
        for ((key, value) in entries) {
            overrideEntry(key, value)
        }
    }

    /**
     * Overrides an entry at the given index. Passing null for the `entry` will remove that
     * entry from `entries`. Indices < 0 or >= [totalEntryCount] are a no-op.
     *
     * @param index The 0-based index for the entry to override.
     * @param entry The entry to override, or null if the entry should be removed at this location.
     */
    fun overrideEntry(index: Int, entry: Entry?) {
        if (index in 0 until totalEntryCount) {
            if (entry != null) {
                _entries[index] = entry
            } else {
                _entries.remove(index)
            }
        }
    }

    fun getString(index: Int): String {
        return checkNotNull(resourceTableChunk) {
            "$javaClass has no resource table."
        }.stringPool.getString(index)
    }

    private fun getKeyName(index: Int): String {
        return checkNotNull(packageChunk) {
            "$javaClass has no parent package."
        }.keyStringPool.getString(index)
    }

    private val resourceTableChunk: ResourceTableChunk?
        get() {
            var chunk = parent
            while (chunk != null && chunk !is ResourceTableChunk) {
                chunk = chunk.parent
            }
            return chunk as? ResourceTableChunk
        }

    /** Returns the package enclosing this chunk, if any. Else, returns null. */
    val packageChunk: PackageChunk?
        get() {
            var chunk = parent
            while (chunk != null && chunk !is PackageChunk) {
                chunk = chunk.parent
            }
            return chunk as? PackageChunk
        }

    override fun getType() = Type.TABLE_TYPE

    /** Returns the number of bytes needed for offsets based on `entries`. */
    private val offsetSize: Int
        get() = totalEntryCount * 4

    @Throws(IOException::class)
    private fun writeEntries(payload: DataOutput, offsets: ByteBuffer, options: Int): Int {
        var entryOffset = 0
        if (hasSparseEntries()) {
            for ((key, entry) in _entries) {
                val encodedEntry = entry.toByteArray(options)
                payload.write(encodedEntry)
                offsets.putShort((key and 0xFFFF).toShort())
                offsets.putShort((entryOffset / 4).toShort())
                entryOffset += encodedEntry.size
                // In order for sparse entries to work, entryOffset must always be a multiple of 4.
                check(entryOffset % 4 == 0)
            }
        } else {
            for (i in 0 until totalEntryCount) {
                val entry = _entries[i]
                if (entry == null) {
                    offsets.putInt(Entry.NO_ENTRY)
                } else {
                    val encodedEntry = entry.toByteArray(options)
                    payload.write(encodedEntry)
                    offsets.putInt(entryOffset)
                    entryOffset += encodedEntry.size
                }
            }
        }
        return writePad(payload, entryOffset)
    }

    override fun writeHeader(output: ByteBuffer) {
        val entriesStart = headerSize + offsetSize
        check(id shr Byte.SIZE_BITS == 0)
        output.put(id.toByte())
        check(flags shr Byte.SIZE_BITS == 0)
        output.put(flags.toByte())
        output.putShort(0.toShort()) // Write 2 bytes for padding / reserved.
        output.putInt(totalEntryCount)
        output.putInt(entriesStart)
        output.put(configuration.toByteArray())
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        val baos = ByteArrayOutputStream()
        val offsets = ByteBuffer.allocate(offsetSize).order(ByteOrder.LITTLE_ENDIAN)
        LittleEndianDataOutputStream(baos).use { payload ->
            writeEntries(payload, offsets, options)
        }
        output.write(offsets.array())
        output.write(baos.toByteArray())
    }

    /** An [Entry] in a [TypeChunk]. Contains one or more [BinaryResourceValue]. */
    data class Entry(

        /** Number of bytes in the header of the [Entry]. */
        val headerSize: Int,

        /** Resource entry flags. */
        val flags: Int,

        /** Index into [PackageChunk.keyStringPool] identifying this entry. */
        val keyIndex: Int,

        /** The value of this resource entry, if this is not a complex entry. Else, null. */
        var value: BinaryResourceValue?,

        /** The extra values in this resource entry if this [isComplex]. */
        val values: MutableMap<Int, BinaryResourceValue>,

        /**
         * Entry into [PackageChunk] that is the parent [Entry] to this entry.
         * This value only makes sense when this is complex ([isComplex] returns true).
         */
        var parentEntry: Int,

        /** The [TypeChunk] that this resource entry belongs to. */
        val parent: TypeChunk,

        /** The entry's index into the parent TypeChunk. */
        val typeChunkIndex: Int
    ) : SerializableResource() {

        /**
         * Creates a new [Entry] whose contents start at [offset] in the given [buffer].
         *
         * @param buffer The buffer to read [Entry] from.
         * @param offset Offset into the buffer where [Entry] is located.
         * @param parent The [TypeChunk] that this resource entry belongs to.
         * @param typeChunkIndex The entry's index into the parent TypeChunk.
         * @return New [Entry].
         */
        constructor(buffer: ByteBuffer, offset: Int, parent: TypeChunk?, typeChunkIndex: Int) : this(
            buffer.mark().position(offset), // Set buffer position to resource entry start
            parent!!,
            typeChunkIndex
        ) {
            buffer.reset() // Restore buffer position to mark
        }

        constructor(buffer: ByteBuffer, parent: TypeChunk, typeChunkIndex: Int) : this(
            headerSize = buffer.getShort().unsignedToInt(),
            flags = buffer.getShort().unsignedToInt(),
            keyIndex = buffer.getInt(),
            value = null,
            values = LinkedHashMap(),
            parentEntry = 0,
            parent = parent,
            typeChunkIndex = typeChunkIndex
        ) {
            if (isComplex) {
                parentEntry = buffer.getInt()
                val valueCount = buffer.getInt()
                for (i in 0 until valueCount) {
                    values[buffer.getInt()] = BinaryResourceValue(buffer)
                }
            } else {
                value = BinaryResourceValue(buffer)
            }
        }

        /** The name of the type this chunk represents (e.g. string, attr, id). */
        val typeName: String
            get() = parent.typeName

        /** The total number of bytes that this [Entry] takes up. */
        val size: Int
            get() = headerSize + if (isComplex) values.size * MAPPING_SIZE else BinaryResourceValue.SIZE

        /** The key name identifying this resource entry. */
        val key: String
            get() = parent.getKeyName(keyIndex)

        /** Returns true if this is a complex resource. */
        val isComplex: Boolean
            get() = flags and FLAG_COMPLEX != 0

        /** Returns true if this is a public resource. */
        val isPublic: Boolean
            get() = flags and FLAG_PUBLIC != 0

        companion object {
            /** An entry offset that indicates that a given resource is not present. */
            const val NO_ENTRY = -0x1

            /** Set if this is a complex resource. Otherwise, it's a simple resource. */
            private const val FLAG_COMPLEX = 1 shl 0

            /** Set if this is a public resource, which allows libraries to reference it. */
            const val FLAG_PUBLIC = 1 shl 1

            /** Size of a single resource id + value mapping entry. */
            private const val MAPPING_SIZE = 4 + BinaryResourceValue.SIZE

            /** Size of a simple resource  */
            const val SIMPLE_HEADERSIZE = 8

            /** Size of a complex resource  */
            const val COMPLEX_HEADER_SIZE = 16
        }

        override fun toByteArray(options: Int): ByteArray {
            val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putShort(headerSize.toShort())
            val flagMask = if (options and PRIVATE_RESOURCES != 0) FLAG_PUBLIC.inv() else 0.inv()
            buffer.putShort((flags and flagMask).toShort())
            buffer.putInt(keyIndex)
            if (isComplex) {
                buffer.putInt(parentEntry)
                buffer.putInt(values.size)
                for ((key, value) in values) {
                    buffer.putInt(key)
                    buffer.put(value.toByteArray(options))
                }
            } else {
                checkNotNull(value) { "A non-complex TypeChunk entry must have a value." }
                buffer.put(value!!.toByteArray())
            }
            return buffer.array()
        }
    }
}

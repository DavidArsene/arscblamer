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
import kotlin.collections.LinkedHashSet

/** A package chunk is a collection of resource data types within a package. */
class PackageChunk(buffer: ByteBuffer, parent: Chunk?) : ChunkWithChunks(buffer, parent) {

    companion object {
        /** Offset in bytes, from the start of the chunk, where `typeStringsOffset` can be found. */
        private const val TYPE_OFFSET_OFFSET = 268

        /** Offset in bytes, from the start of the chunk, where `keyStringsOffset` can be found. */
        private const val KEY_OFFSET_OFFSET = 276

        private const val HEADER_SIZE = KEY_OFFSET_OFFSET + 12
    }

    /** The package id if this is a base package, or 0 if not a base package. */
    @JvmField var id: Int = buffer.getInt()

    /** The name of the package. */
    @JvmField var packageName: String = PackageUtils.readPackageName(buffer, buffer.position())

    /** The offset (from `offset`) in the original buffer where type strings start. */
    private val typeStringsOffset: Int = buffer.getInt()

    /** The index into the type string pool of the last public type. */
    private val lastPublicType: Int = buffer.getInt()

    /** An offset to the string pool that contains the key strings for this package. */
    private val keyStringsOffset: Int = buffer.getInt()

    /** The index into the key string pool of the last public key. */
    private val lastPublicKey: Int = buffer.getInt()

    /** An offset to the type ID(s). This is undocumented in the original code. */
    private val typeIdOffset: Int = buffer.getInt()

    /** Contains a mapping of a type index to its [TypeSpecChunk]. */
    private val typeSpecs: MutableMap<Int, TypeSpecChunk> = HashMap()

    /** Contains a mapping of a type index to all of the [TypeChunk] with that index. */
    private val types: MutableMap<Int, MutableSet<TypeChunk>> = HashMap()

    /** May contain a library chunk for mapping dynamic references to resolved references. */
    private lateinit var libraryChunk: LibraryChunk

    init {
        for (chunk in chunks.values) {
            when (chunk) {
                is TypeChunk -> putIntoTypes(chunk)
                is TypeSpecChunk -> typeSpecs[chunk.id] = chunk
                is LibraryChunk -> {
                    check(!::libraryChunk.isInitialized) { "Multiple library chunks present in package chunk." }
                    // NB: this is currently unused except for the above assertion that there's <=1 chunk.
                    libraryChunk = chunk
                }
                else -> check(chunk is StringPoolChunk || chunk is UnknownChunk) {
                    "PackageChunk contains an unexpected chunk: ${chunk.javaClass}"
                }
            }
        }
    }

    /** Returns the string pool that contains the names of the resources in this package. */
    val keyStringPool: StringPoolChunk
        get() {
            val chunk = checkNotNull(chunks[keyStringsOffset + offset])
            check(chunk is StringPoolChunk) { "Key string pool not found." }
            return chunk
        }

    /**
     * Get the type string for a specific id, e.g., (e.g. string, attr, id).
     *
     * @param id The id to get the type for.
     * @return The type string.
     */
    fun getTypeString(id: Int): String {
        val typePool = typeStringPool
        check(typePool.stringCount >= id) { "No type for id: $id" }
        return typePool.getString(id - 1) // - 1 here to convert to 0-based index
    }

    /**
     * Returns the string pool that contains the type strings for this package, such as "layout",
     * "string", "color".
     */
    val typeStringPool: StringPoolChunk
        get() {
            val chunk = checkNotNull(chunks[typeStringsOffset + offset])
            check(chunk is StringPoolChunk) { "Type string pool not found." }
            return chunk
        }

    /** Returns all [TypeChunk] in this package. */
    val typeChunks: Collection<TypeChunk>
        get() = types.values.flatten()

    /**
     * For a given type id, returns the [TypeChunk] objects that match that id. The type id is
     * the 1-based index of the type in the type string pool (returned by [typeStringPool]).
     *
     * @param id The 1-based type id to return [TypeChunk] objects for.
     * @return The matching [TypeChunk] objects, or an empty collection if there are none.
     */
    fun getTypeChunks(id: Int): Collection<TypeChunk> = types[id] ?: emptySet()

    /**
     * For a given type, returns the [TypeChunk] objects that match that type
     * (e.g. "attr", "id", "string", ...).
     *
     * @param type The type to return [TypeChunk] objects for.
     * @return The matching [TypeChunk] objects, or an empty collection if there are none.
     */
    fun getTypeChunks(type: String): Collection<TypeChunk> {
        return getTypeChunks(typeStringPool.indexOf(type) + 1) // Convert 0-based index to 1-based
    }

    /** Returns all [TypeSpecChunk] in this package. */
    val typeSpecChunks: Collection<TypeSpecChunk>
        get() = typeSpecs.values

    /** For a given (1-based) type id, returns the [TypeSpecChunk] matching it. */
    fun getTypeSpecChunk(id: Int): TypeSpecChunk = checkNotNull(typeSpecs[id])

    /** For a given `type`, returns the [TypeSpecChunk] that matches it (e.g. "attr", "id", "string", ...). */
    fun getTypeSpecChunk(type: String): TypeSpecChunk {
        return getTypeSpecChunk(typeStringPool.indexOf(type) + 1) // Convert 0-based index to 1-based
    }

    /** Removes the [TypeChunk] from this package and the collection inherited from the super class. */
    fun remove(chunk: TypeChunk) {
        super.remove(chunk)
        val chunkId = chunk.id
        check(removeFromTypes(chunk)) { "Can't remove ${chunk.javaClass} from packageChunk." }
        if (!types.containsKey(chunkId)) {
            val specChunk = typeSpecs.remove(chunkId)
            checkNotNull(specChunk) { "TypeSpec chunk not found for id: $chunkId" }
            super.remove(specChunk)
        }
    }

    override fun getType() = Type.TABLE_PACKAGE

    /**
     * Delete the given keys from this package, rewrite references to these keys from |this|'s
     * TypeChunks, and remove empty TypeChunks. Returns number of TypeChunks deleted.
     */
    fun deleteKeyStrings(keyIndexesToDelete: SortedSet<Int>): Int {
        val remappedIndexes = keyStringPool.deleteStrings(keyIndexesToDelete)
        val typeChunksToDelete: MutableCollection<TypeChunk> = ArrayList()
        for (typeChunk in typeChunks) {
            var shouldDeleteTypeChunk = true
            val replacementEntries = TreeMap<Int, TypeChunk.Entry?>()
            for ((key, value) in typeChunk.entries) {
                val newIndex = remappedIndexes[value.keyIndex]
                shouldDeleteTypeChunk = shouldDeleteTypeChunk && newIndex == -1
                replacementEntries[key] = if (newIndex == -1) null else value.copy(keyIndex = newIndex)
            }
            typeChunk.overrideEntries(replacementEntries)
            if (shouldDeleteTypeChunk) {
                typeChunksToDelete.add(typeChunk)
            }
        }
        for (typeChunk in typeChunksToDelete) {
            remove(typeChunk)
        }
        return typeChunksToDelete.size
    }

    override fun writeHeader(output: ByteBuffer) {
        output.putInt(id)
        PackageUtils.writePackageName(output, packageName)
        output.putInt(0) // typeStringsOffset. This value can't be computed here.
        output.putInt(lastPublicType)
        output.putInt(0) // keyStringsOffset. This value can't be computed here.
        output.putInt(lastPublicKey)
        output.putInt(typeIdOffset)
    }

    @Throws(IOException::class)
    override fun writePayload(output: DataOutput, header: ByteBuffer, options: Int) {
        var typeOffset = typeStringsOffset
        var keyOffset = keyStringsOffset
        var payloadOffset = 0
        for (chunk in chunks.values) {
            if (chunk === typeStringPool) {
                typeOffset = payloadOffset + headerSize
            } else if (chunk === keyStringPool) {
                keyOffset = payloadOffset + headerSize
            }
            val chunkBytes = chunk.toByteArray(options)
            output.write(chunkBytes)
            payloadOffset = writePad(output, chunkBytes.size)
        }
        header.putInt(TYPE_OFFSET_OFFSET, typeOffset)
        header.putInt(KEY_OFFSET_OFFSET, keyOffset)
    }

    /** Using [types] as a `Multimap`, put a [TypeChunk] into it. The key is the id of the `typeChunk`. */
    private fun putIntoTypes(typeChunk: TypeChunk) {
        // Some tools require that the default TypeChunk is first in the list. Use a LinkedHashSet
        // to make sure that when we return the chunks they are in original order (in cases
        // where we copy and edit them this is important).
        val chunks = types[typeChunk.id] ?: LinkedHashSet<TypeChunk>().also { types[typeChunk.id] = it }
        chunks.add(typeChunk)
    }

    /** Using [types] as a `Multimap`, remove a [TypeChunk] from it. The key is the id of the `typeChunk`. */
    private fun removeFromTypes(typeChunk: TypeChunk): Boolean {
        val chunkId = typeChunk.id
        val chunks = types[chunkId]
        if (chunks == null || !chunks.remove(typeChunk)) {
            return false
        }
        if (chunks.isEmpty()) {
            types.remove(chunkId)
        }
        return true
    }
}

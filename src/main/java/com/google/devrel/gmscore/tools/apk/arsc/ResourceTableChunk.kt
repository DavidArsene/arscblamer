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
import java.util.*

/**
 * Represents a resource table structure. Its sub-chunks contain:
 *
 * - A [StringPoolChunk] containing all string values in the entire resource table. It
 * does not, however, contain the names of entries or type identifiers.
 * - One or more [PackageChunk].
 */
class ResourceTableChunk(buffer: ByteBuffer, parent: Chunk?) : ChunkWithChunks(buffer, parent) {

    /** A string pool containing all string resource values in the entire resource table. */
    lateinit var stringPool: StringPoolChunk
        private set

    /** The packages contained in this resource table. */
    private val _packages: MutableMap<String, PackageChunk> = HashMap()

    init {
        // packageCount. We ignore this, because we already know how many chunks we have.
        check(buffer.getInt() >= 1) { "ResourceTableChunk package count was < 1." }

        for (chunk in chunks.values) {
            if (chunk is PackageChunk) {
                _packages[chunk.packageName] = chunk
            } else if (chunk is StringPoolChunk) {
                stringPool = chunk
            }
        }
    }

    /** Adds the [PackageChunk] to this table. */
    fun addPackageChunk(packageChunk: PackageChunk) {
        super.add(packageChunk)
        _packages[packageChunk.packageName] = packageChunk
    }

    /**
     * Deletes from the string pool all indices in the passed in collection and remaps the references
     * in every [TypeChunk].
     */
    fun deleteStrings(indexesToDelete: SortedSet<Int>) {
        val remappedIndexes = stringPool.deleteStrings(indexesToDelete)
        for (packageChunk in packages) {
            for (typeChunk in packageChunk.typeChunks) {
                val replacementEntries = TreeMap<Int, TypeChunk.Entry?>()
                for ((index, chunkEntry) in typeChunk.entries) {
                    if (chunkEntry.isComplex) {
                        // An isComplex() Entry can have some values of type STRING and others of other types
                        // (e.g. a <style> can have one sub-<item> be a DIMENSION and another sub-<item> be a
                        // STRING) so we need to rewrite such Entry's sub-values independently.
                        val newValues = TreeMap<Int, BinaryResourceValue>()
                        for ((key, value) in chunkEntry.values.entries) {
                            newValues[key] = if (isString(value)) {
                                val newIndex = remappedIndexes[value.data]
                                check(newIndex >= 0)
                                value.copy(data = newIndex)
                            } else value
                        }
                        // Even if a chunkEntry's values are empty, it is still important and should not be
                        // removed here. It's possible that the entry is overriding another entry's values
                        // and/or has a different parentEntry.
                        replacementEntries[index] = chunkEntry.copy(values = newValues)
                    } else {
                        val value = checkNotNull(chunkEntry.value)
                        if (isString(value)) {
                            val newIndex = remappedIndexes[value.data]
                            replacementEntries[index] = if (newIndex == -1) null
                            else chunkEntry.copy(value = value.copy(data = newIndex))
                        }
                    }
                }
                typeChunk.overrideEntries(replacementEntries)
            }
        }
    }

    /** Returns the package with the given `packageName`. Else, returns null. */
    fun getPackage(packageName: String): PackageChunk? = _packages[packageName]

    /** Returns the package with the given `packageId`. Else, returns null  */
    fun getPackage(packageId: Int) = _packages.values.firstOrNull { chunk -> chunk.id == packageId }

    /** Returns the packages contained in this resource table. */
    val packages: Collection<PackageChunk>
        get() = Collections.unmodifiableCollection(_packages.values)

    override fun getType() = Type.TABLE

    override fun writeHeader(output: ByteBuffer) {
        output.putInt(_packages.size)
    }

    companion object {
        private const val HEADER_SIZE = METADATA_SIZE + 4 // +4 = package count

        private fun isString(value: BinaryResourceValue) = value.type === BinaryResourceValue.Type.STRING
    }
}

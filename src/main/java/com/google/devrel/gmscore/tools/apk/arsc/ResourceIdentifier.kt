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

/**
 * Represents a resource id in a [ResourceTableChunk] of the form `0xpptteeee`, where `pp` is the
 * [PackageChunk] id, `tt` is the [TypeChunk] id, and `eeee` is the index of the entry in
 * the [TypeChunk].
 */
data class ResourceIdentifier(

    /** The (1-based) id of the [PackageChunk] containing this resource. */
    val packageId: Int,

    /** The (1-based) id of the [TypeChunk] containing this resource. */
    val typeId: Int,

    /** The (0-based) index of the entry in a [TypeChunk] containing this resource. */
    val entryId: Int
) {

    /** Returns a [ResourceIdentifier] from a `resourceId` of the form `0xpptteeee`. */
    constructor(resourceId: Int) : this(
        packageId = resourceId and PACKAGE_ID_MASK ushr PACKAGE_ID_SHIFT,
        typeId = resourceId and TYPE_ID_MASK ushr TYPE_ID_SHIFT,
        entryId = resourceId and ENTRY_ID_MASK ushr ENTRY_ID_SHIFT
    )

    init {
        check(packageId and 0xFF == packageId) { "packageId must be <= 0xFF." }
        check(typeId and 0xFF == typeId) { "typeId must be <= 0xFF." }
        check(entryId and 0xFFFF == entryId) { "entryId must be <= 0xFFFF." }
    }

    companion object {
        /** The [PackageChunk] id mask for a packed resource id of the form `0xpptteeee`. */
        private const val PACKAGE_ID_MASK = -0x1000000
        private const val PACKAGE_ID_SHIFT = 24

        /** The [TypeChunk] id mask for a packed resource id of the form `0xpptteeee`. */
        private const val TYPE_ID_MASK = 0x00FF0000
        private const val TYPE_ID_SHIFT = 16

        /** The [TypeChunk.Entry] id mask for a packed resource id of the form `0xpptteeee`. */
        private const val ENTRY_ID_MASK = 0xFFFF
        private const val ENTRY_ID_SHIFT = 0

        /** Returns the resource id from the integer representation. */
        fun entryIdFromResourceId(resourceId: Int): Int = resourceId and ENTRY_ID_MASK

        /** Returns the resource id as an integer with an alternative entryId. */
        fun asInt(packageId: Int, typeId: Int, entryId: Int): Int {
            check(packageId and 0xFF == packageId) { "packageId must be <= 0xFF." }
            check(typeId and 0xFF == typeId) { "typeId must be <= 0xFF." }
            check(entryId and 0xFFFF == entryId) { "entryId must be <= 0xFFFF." }
            return packageId shl PACKAGE_ID_SHIFT or (typeId shl TYPE_ID_SHIFT) or entryId
        }
    }
}

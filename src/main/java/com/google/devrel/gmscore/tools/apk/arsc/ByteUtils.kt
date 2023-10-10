@file:Suppress("NOTHING_TO_INLINE")

package com.google.devrel.gmscore.tools.apk.arsc

/**
 * Converts a signed byte to an unsigned int.
 *
 * ```
 * Before: 0xFF.toInt() == -1
 * After:  0xFF.unsignedToInt() == 255
 * ```
 */
internal inline fun Byte.unsignedToInt() = this.toInt() and 0xFF

/**
 * Converts a signed short to an unsigned int.
 *
 * ```
 * Before: 0xFFFF.toInt() == -1
 * After:  0xFFFF.unsignedToInt() == 65535
 * ```
 */
internal inline fun Short.unsignedToInt() = this.toInt() and 0xFFFF

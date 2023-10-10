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
import java.nio.charset.StandardCharsets

/** Describes a particular resource configuration. */
@Suppress("EqualsOrHashCode", "ArrayInDataClass")
data class ResourceConfiguration(

    /** The size of this resource configuration in bytes. */
    val size: Int,

    val mcc: Int,
    val mnc: Int,

    /** A packed 2-byte language code. */
    val language: ByteArray,

    /** A packed 2-byte region code. */
    val region: ByteArray,

    val orientation: Int,
    val touchscreen: Int,
    val density: Int,
    val keyboard: Int,
    val navigation: Int,
    val inputFlags: Int,

    val screenWidth: Int,
    val screenHeight: Int,
    val sdkVersion: Int,

    val minorVersion: Int,
    var screenLayout: Int,

    var uiMode: Int,

    var smallestScreenWidthDp: Int,
    var screenWidthDp: Int,
    var screenHeightDp: Int,

    /** The ISO-15924 short name for the script corresponding to this configuration. */
    val localeScript: ByteArray,

    /** A single BCP-47 variant subtag. */
    val localeVariant: ByteArray,

    /** An extension to [screenLayout]. Contains round/notround qualifier. */
    var screenLayout2: Int,

    /** Wide-gamut, HDR, etc. */
    var colorMode: Int,

    /** Any remaining bytes in this resource configuration that are unaccounted for. */
    var unknown: ByteArray
) : SerializableResource() {

    /**
     * The different types of configs that can be present in a [ResourceConfiguration].
     *
     * The ordering of these types is roughly the same as `#isBetterThan`, but is not
     * guaranteed to be the same.
     */
    enum class Type {
        MCC,
        MNC,
        LANGUAGE_STRING,
        LOCALE_SCRIPT_STRING,
        REGION_STRING,
        LOCALE_VARIANT_STRING,
        SCREEN_LAYOUT_DIRECTION,
        SMALLEST_SCREEN_WIDTH_DP,
        SCREEN_WIDTH_DP,
        SCREEN_HEIGHT_DP,
        SCREEN_LAYOUT_SIZE,
        SCREEN_LAYOUT_LONG,
        SCREEN_LAYOUT_ROUND,
        // NB: COLOR_GAMUT takes priority over HDR in #isBetterThan.
        COLOR_MODE_WIDE_COLOR_GAMUT,
        COLOR_MODE_HDR,
        ORIENTATION,
        UI_MODE_TYPE,
        UI_MODE_NIGHT,
        DENSITY_DPI,
        TOUCHSCREEN,
        KEYBOARD_HIDDEN,
        KEYBOARD,
        NAVIGATION_HIDDEN,
        NAVIGATION,
        SCREEN_SIZE,
        SDK_VERSION
    }

    /** Returns [language] as an unpacked string representation. */
    val languageString: String
        get() = unpackLanguageOrRegion(language, 0x61)

    /** Returns [region] as an unpacked string representation. */
    val regionString: String
        get() = unpackLanguageOrRegion(region, 0x30)

    val keyboardHidden: Int
        get() = inputFlags and KEYBOARDHIDDEN_MASK

    val navigationHidden: Int
        get() = inputFlags and NAVIGATIONHIDDEN_MASK

    val screenLayoutDirection: Int
        get() = screenLayout and SCREENLAYOUT_LAYOUTDIR_MASK

    val screenLayoutSize: Int
        get() = screenLayout and SCREENLAYOUT_SIZE_MASK

    val screenLayoutLong: Int
        get() = screenLayout and SCREENLAYOUT_LONG_MASK

    val screenLayoutRound: Int
        get() = screenLayout2 and SCREENLAYOUT_ROUND_MASK

    val uiModeType: Int
        get() = uiMode and UI_MODE_TYPE_MASK

    val uiModeNight: Int
        get() = uiMode and UI_MODE_NIGHT_MASK

    /** Returns the [localeScript] as a string. */
    val localeScriptString: String
        get() = byteArrayToString(localeScript)

    /** Returns the [localeVariant] as a string. */
    val localeVariantString: String
        get() = byteArrayToString(localeVariant)

    /** Returns the wide color gamut section of [colorMode]. */
    val colorModeWideColorGamut: Int
        get() = colorMode and COLOR_MODE_WIDE_COLOR_GAMUT_MASK

    /** Returns the HDR section of [colorMode]. */
    val colorModeHdr: Int
        get() = colorMode and COLOR_MODE_HDR_MASK

    /** Returns a [ResourceConfiguration] with sane default properties. */
    constructor() : this(
        size = SIZE,
        mcc = 0,
        mnc = 0,
        language = ByteArray(2),
        region = ByteArray(2),
        orientation = 0,
        touchscreen = 0,
        density = 0,
        keyboard = 0,
        navigation = 0,
        inputFlags = 0,
        screenWidth = 0,
        screenHeight = 0,
        sdkVersion = 0,
        minorVersion = 0,
        screenLayout = 0,
        uiMode = 0,
        smallestScreenWidthDp = 0,
        screenWidthDp = 0,
        screenHeightDp = 0,
        localeScript = ByteArray(4),
        localeVariant = ByteArray(8),
        screenLayout2 = 0,
        colorMode = 0,
        unknown = ByteArray(0)
    )

    constructor(buffer: ByteBuffer) : this(
        size = buffer.mark().getInt().also {
            check(it >= MIN_SIZE) { "Expected minimum size of $MIN_SIZE, got $it" }
        },
        mcc = buffer.getShort().unsignedToInt(),
        mnc = buffer.getShort().unsignedToInt(),
        language = ByteArray(2).apply { buffer.get(this) },
        region = ByteArray(2).apply { buffer.get(this) },
        orientation = buffer.get().unsignedToInt(),
        touchscreen = buffer.get().unsignedToInt(),
        density = buffer.getShort().unsignedToInt(),
        keyboard = buffer.get().unsignedToInt(),
        navigation = buffer.get().unsignedToInt(),
        inputFlags = buffer.get().unsignedToInt()
            .also { buffer.get() }, // 1 byte of padding
        screenWidth = buffer.getShort().unsignedToInt(),
        screenHeight = buffer.getShort().unsignedToInt(),
        sdkVersion = buffer.getShort().unsignedToInt(),
        minorVersion = buffer.getShort().unsignedToInt(),
        screenLayout = 0,
        uiMode = 0,
        smallestScreenWidthDp = 0,
        screenWidthDp = 0,
        screenHeightDp = 0,
        localeScript = ByteArray(4),
        localeVariant = ByteArray(8),
        screenLayout2 = 0,
        colorMode = 0,
        unknown = ByteArray(0)
    ) {
        // At this point, the configuration's size needs to be taken into account as not all
        // configurations have all values.
        if (size >= SCREEN_CONFIG_MIN_SIZE) {
            screenLayout = buffer.get().unsignedToInt()
            uiMode = buffer.get().unsignedToInt()
            smallestScreenWidthDp = buffer.getShort().unsignedToInt()
        }
        if (size >= SCREEN_DP_MIN_SIZE) {
            screenWidthDp = buffer.getShort().unsignedToInt()
            screenHeightDp = buffer.getShort().unsignedToInt()
        }
        if (size >= LOCALE_MIN_SIZE) {
            buffer.get(localeScript)
            buffer.get(localeVariant)
        }
        if (size >= SCREEN_CONFIG_EXTENSION_MIN_SIZE) {
            screenLayout2 = buffer.get().unsignedToInt()
            colorMode = buffer.get().unsignedToInt()
            buffer.getShort() // More reserved padding
        }
        // After parsing everything that's known, account for anything that's unknown.
        val endPosition = buffer.position()
        val startPosition = buffer.reset().position()
        val bytesRead = endPosition - startPosition
        unknown = ByteArray(size - bytesRead).apply { buffer.position(endPosition).get(this) }
    }

    private fun byteArrayToString(data: ByteArray): String {
        val length = data.indexOf(0.toByte())
        return String(data, 0, if (length >= 0) length else data.size, StandardCharsets.US_ASCII)
    }

    /** Returns true if this is the default "any" configuration. */
    val isDefault: Boolean
        get() = DEFAULT == this && unknown.contentEquals(ByteArray(unknown.size))

    fun isDensityCompatibleWith(deviceDensityDpi: Int): Boolean {
        return when (val configDensity = density) {
            DENSITY_DPI_UNDEFINED, DENSITY_DPI_ANY, DENSITY_DPI_NONE -> true
            else -> configDensity <= deviceDensityDpi
        }
    }

    override fun toByteArray(options: Int): ByteArray {
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(size)
        buffer.putShort(mcc.toShort())
        buffer.putShort(mnc.toShort())
        buffer.put(language)
        buffer.put(region)
        buffer.put(orientation.toByte())
        buffer.put(touchscreen.toByte())
        buffer.putShort(density.toShort())
        buffer.put(keyboard.toByte())
        buffer.put(navigation.toByte())
        buffer.put(inputFlags.toByte())
        buffer.put(0.toByte()) // Padding
        buffer.putShort(screenWidth.toShort())
        buffer.putShort(screenHeight.toShort())
        buffer.putShort(sdkVersion.toShort())
        buffer.putShort(minorVersion.toShort())

        if (size >= SCREEN_CONFIG_MIN_SIZE) {
            buffer.put(screenLayout.toByte())
            buffer.put(uiMode.toByte())
            buffer.putShort(smallestScreenWidthDp.toShort())
        }
        if (size >= SCREEN_DP_MIN_SIZE) {
            buffer.putShort(screenWidthDp.toShort())
            buffer.putShort(screenHeightDp.toShort())
        }
        if (size >= LOCALE_MIN_SIZE) {
            buffer.put(localeScript)
            buffer.put(localeVariant)
        }
        if (size >= SCREEN_CONFIG_EXTENSION_MIN_SIZE) {
            buffer.put(screenLayout2.toByte())
            buffer.put(colorMode.toByte())
            buffer.putShort(0.toShort()) // Writing 2 bytes of padding
        }
        buffer.put(unknown)
        return buffer.array()
    }

    override fun toString(): String {
        if (isDefault) { // Prevent the default configuration from returning the empty string
            return "default"
        }
        val parts = toStringParts()
        mergeLocale(parts)
        val values = parts.values
        values.removeAll(setOf(""))
        return values.joinToString("-")
    }

    /**
     * Merges the locale for `parts` if necessary.
     *
     * Android supports a modified BCP 47 tag containing script and variant. If script or variant
     * are provided in the configuration, then the locale section should appear as:
     *
     * `b+language+script+region+variant`
     */
    private fun mergeLocale(parts: MutableMap<Type, String>) {
        val script = localeScriptString
        val variant = localeVariantString
        if (script.isEmpty() && variant.isEmpty()) {
            return
        }
        val locale = StringBuilder("b+").append(languageString)
        if (script.isNotEmpty()) {
            locale.append("+").append(script)
        }
        val region = regionString
        if (region.isNotEmpty()) {
            locale.append("+").append(region)
        }
        if (variant.isNotEmpty()) {
            locale.append("+").append(variant)
        }
        parts[Type.LANGUAGE_STRING] = locale.toString()
        parts.remove(Type.LOCALE_SCRIPT_STRING)
        parts.remove(Type.REGION_STRING)
        parts.remove(Type.LOCALE_VARIANT_STRING)
    }

    /**
     * Returns a map of the configuration parts for [toString].
     *
     * If a configuration part is not defined for this [ResourceConfiguration], its value
     * will be the empty string.
     */
    fun toStringParts() = linkedMapOf( // Preserve order for #toString().
        Type.MCC to if (mcc != 0) "mcc$mcc" else "",
        Type.MNC to if (mnc != 0) "mnc$mnc" else "",
        Type.LANGUAGE_STRING to languageString,
        Type.LOCALE_SCRIPT_STRING to localeScriptString,
        Type.REGION_STRING to if (regionString.isNotEmpty()) "r$regionString" else "",
        Type.LOCALE_VARIANT_STRING to localeVariantString,
        Type.SCREEN_LAYOUT_DIRECTION to (SCREENLAYOUT_LAYOUTDIR_VALUES[screenLayoutDirection] ?: ""),
        Type.SMALLEST_SCREEN_WIDTH_DP to if (smallestScreenWidthDp != 0) "sw${smallestScreenWidthDp}dp" else "",
        Type.SCREEN_WIDTH_DP to if (screenWidthDp != 0) "w${screenWidthDp}dp" else "",
        Type.SCREEN_HEIGHT_DP to if (screenHeightDp != 0) "h${screenHeightDp}dp" else "",
        Type.SCREEN_LAYOUT_SIZE to (SCREENLAYOUT_SIZE_VALUES[screenLayoutSize] ?: ""),
        Type.SCREEN_LAYOUT_LONG to (SCREENLAYOUT_LONG_VALUES[screenLayoutLong] ?: ""),
        Type.SCREEN_LAYOUT_ROUND to (SCREENLAYOUT_ROUND_VALUES[screenLayoutRound] ?: ""),
        Type.COLOR_MODE_HDR to (COLOR_MODE_HDR_VALUES[colorModeHdr] ?: ""),
        Type.COLOR_MODE_WIDE_COLOR_GAMUT to (COLOR_MODE_WIDE_COLOR_GAMUT_VALUES[colorModeWideColorGamut] ?: ""),
        Type.ORIENTATION to (ORIENTATION_VALUES[orientation] ?: ""),
        Type.UI_MODE_TYPE to (UI_MODE_TYPE_VALUES[uiModeType] ?: ""),
        Type.UI_MODE_NIGHT to (UI_MODE_NIGHT_VALUES[uiModeNight] ?: ""),
        Type.DENSITY_DPI to (DENSITY_DPI_VALUES[density] ?: "${density}dpi"),
        Type.TOUCHSCREEN to (TOUCHSCREEN_VALUES[touchscreen] ?: ""),
        Type.KEYBOARD_HIDDEN to (KEYBOARDHIDDEN_VALUES[keyboardHidden] ?: ""),
        Type.KEYBOARD to (KEYBOARD_VALUES[keyboard] ?: ""),
        Type.NAVIGATION_HIDDEN to (NAVIGATIONHIDDEN_VALUES[navigationHidden] ?: ""),
        Type.NAVIGATION to (NAVIGATION_VALUES[navigation] ?: ""),
        Type.SCREEN_SIZE to if (screenWidth != 0 || screenHeight != 0) "${screenWidth}x$screenHeight" else "",
        Type.SDK_VERSION to if (sdkVersion != 0) "v$sdkVersion${if (minorVersion != 0) ".$minorVersion" else ""}" else ""
    )

    // Ignore size and unknown when checking equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResourceConfiguration) return false

        return mcc == other.mcc &&
            mnc == other.mnc &&
            language.contentEquals(other.language) &&
            region.contentEquals(other.region) &&
            orientation == other.orientation &&
            touchscreen == other.touchscreen &&
            density == other.density &&
            keyboard == other.keyboard &&
            navigation == other.navigation &&
            inputFlags == other.inputFlags &&
            screenWidth == other.screenWidth &&
            screenHeight == other.screenHeight &&
            sdkVersion == other.sdkVersion &&
            minorVersion == other.minorVersion &&
            screenLayout == other.screenLayout &&
            uiMode == other.uiMode &&
            smallestScreenWidthDp == other.smallestScreenWidthDp &&
            screenWidthDp == other.screenWidthDp &&
            screenHeightDp == other.screenHeightDp &&
            localeScript.contentEquals(other.localeScript) &&
            localeVariant.contentEquals(other.localeVariant) &&
            screenLayout2 == other.screenLayout2 &&
            colorMode == other.colorMode
    }

    companion object {
        /**
         * The default configuration. This configuration acts as a "catch-all" for looking up resources
         * when no better configuration can be found.
         */
        val DEFAULT = ResourceConfiguration()

        // The below constants are from android.content.res.Configuration.

        const val COLOR_MODE_WIDE_COLOR_GAMUT_MASK = 0x03
        const val COLOR_MODE_WIDE_COLOR_GAMUT_UNDEFINED = 0
        const val COLOR_MODE_WIDE_COLOR_GAMUT_NO = 0x01
        const val COLOR_MODE_WIDE_COLOR_GAMUT_YES = 0x02

        private val COLOR_MODE_WIDE_COLOR_GAMUT_VALUES = mapOf(
            COLOR_MODE_WIDE_COLOR_GAMUT_UNDEFINED to "",
            COLOR_MODE_WIDE_COLOR_GAMUT_NO to "nowidecg",
            COLOR_MODE_WIDE_COLOR_GAMUT_YES to "widecg"
        )

        const val COLOR_MODE_HDR_MASK = 0x0C
        const val COLOR_MODE_HDR_UNDEFINED = 0
        const val COLOR_MODE_HDR_NO = 0x04
        const val COLOR_MODE_HDR_YES = 0x08

        private val COLOR_MODE_HDR_VALUES = mapOf(
            COLOR_MODE_HDR_UNDEFINED to "",
            COLOR_MODE_HDR_NO to "lowdr",
            COLOR_MODE_HDR_YES to "highdr"
        )

        const val DENSITY_DPI_UNDEFINED = 0
        const val DENSITY_DPI_LDPI = 120
        const val DENSITY_DPI_MDPI = 160
        const val DENSITY_DPI_TVDPI = 213
        const val DENSITY_DPI_HDPI = 240
        const val DENSITY_DPI_XHDPI = 320
        const val DENSITY_DPI_XXHDPI = 480
        const val DENSITY_DPI_XXXHDPI = 640
        const val DENSITY_DPI_ANY = 0xFFFE
        const val DENSITY_DPI_NONE = 0xFFFF

        private val DENSITY_DPI_VALUES = mapOf(
            DENSITY_DPI_UNDEFINED to "",
            DENSITY_DPI_LDPI to "ldpi",
            DENSITY_DPI_MDPI to "mdpi",
            DENSITY_DPI_TVDPI to "tvdpi",
            DENSITY_DPI_HDPI to "hdpi",
            DENSITY_DPI_XHDPI to "xhdpi",
            DENSITY_DPI_XXHDPI to "xxhdpi",
            DENSITY_DPI_XXXHDPI to "xxxhdpi",
            DENSITY_DPI_ANY to "anydpi",
            DENSITY_DPI_NONE to "nodpi"
        )

        const val KEYBOARD_NOKEYS = 1
        const val KEYBOARD_QWERTY = 2
        const val KEYBOARD_12KEY = 3

        private val KEYBOARD_VALUES = mapOf(
            KEYBOARD_NOKEYS to "nokeys",
            KEYBOARD_QWERTY to "qwerty",
            KEYBOARD_12KEY to "12key"
        )

        const val KEYBOARDHIDDEN_MASK = 0x03
        const val KEYBOARDHIDDEN_NO = 1
        const val KEYBOARDHIDDEN_YES = 2
        const val KEYBOARDHIDDEN_SOFT = 3

        private val KEYBOARDHIDDEN_VALUES = mapOf(
            KEYBOARDHIDDEN_NO to "keysexposed",
            KEYBOARDHIDDEN_YES to "keyshidden",
            KEYBOARDHIDDEN_SOFT to "keyssoft"
        )

        const val NAVIGATION_NONAV = 1
        const val NAVIGATION_DPAD = 2
        const val NAVIGATION_TRACKBALL = 3
        const val NAVIGATION_WHEEL = 4

        private val NAVIGATION_VALUES = mapOf(
            NAVIGATION_NONAV to "nonav",
            NAVIGATION_DPAD to "dpad",
            NAVIGATION_TRACKBALL to "trackball",
            NAVIGATION_WHEEL to "wheel"
        )

        const val NAVIGATIONHIDDEN_MASK = 0x0C
        const val NAVIGATIONHIDDEN_NO = 0x04
        const val NAVIGATIONHIDDEN_YES = 0x08

        private val NAVIGATIONHIDDEN_VALUES = mapOf(
            NAVIGATIONHIDDEN_NO to "navexposed",
            NAVIGATIONHIDDEN_YES to "navhidden"
        )

        const val ORIENTATION_PORTRAIT = 0x01
        const val ORIENTATION_LANDSCAPE = 0x02

        private val ORIENTATION_VALUES = mapOf(
            ORIENTATION_PORTRAIT to "port",
            ORIENTATION_LANDSCAPE to "land"
        )

        const val SCREENLAYOUT_LAYOUTDIR_MASK = 0xC0
        const val SCREENLAYOUT_LAYOUTDIR_LTR = 0x40
        const val SCREENLAYOUT_LAYOUTDIR_RTL = 0x80

        private val SCREENLAYOUT_LAYOUTDIR_VALUES = mapOf(
            SCREENLAYOUT_LAYOUTDIR_LTR to "ldltr",
            SCREENLAYOUT_LAYOUTDIR_RTL to "ldrtl"
        )

        const val SCREENLAYOUT_LONG_MASK = 0x30
        const val SCREENLAYOUT_LONG_NO = 0x10
        const val SCREENLAYOUT_LONG_YES = 0x20

        private val SCREENLAYOUT_LONG_VALUES = mapOf(
            SCREENLAYOUT_LONG_NO to "notlong",
            SCREENLAYOUT_LONG_YES to "long"
        )

        const val SCREENLAYOUT_ROUND_MASK = 0x03
        const val SCREENLAYOUT_ROUND_NO = 0x01
        const val SCREENLAYOUT_ROUND_YES = 0x02

        private val SCREENLAYOUT_ROUND_VALUES = mapOf(
            SCREENLAYOUT_ROUND_NO to "notround",
            SCREENLAYOUT_ROUND_YES to "round"
        )

        const val SCREENLAYOUT_SIZE_MASK = 0x0F
        const val SCREENLAYOUT_SIZE_SMALL = 0x01
        const val SCREENLAYOUT_SIZE_NORMAL = 0x02
        const val SCREENLAYOUT_SIZE_LARGE = 0x03
        const val SCREENLAYOUT_SIZE_XLARGE = 0x04

        private val SCREENLAYOUT_SIZE_VALUES = mapOf(
            SCREENLAYOUT_SIZE_SMALL to "small",
            SCREENLAYOUT_SIZE_NORMAL to "normal",
            SCREENLAYOUT_SIZE_LARGE to "large",
            SCREENLAYOUT_SIZE_XLARGE to "xlarge"
        )

        const val TOUCHSCREEN_NOTOUCH = 1
        const val TOUCHSCREEN_FINGER = 3

        private val TOUCHSCREEN_VALUES = mapOf(
            TOUCHSCREEN_NOTOUCH to "notouch",
            TOUCHSCREEN_FINGER to "finger"
        )

        const val UI_MODE_NIGHT_MASK = 0x30
        const val UI_MODE_NIGHT_NO = 0x10
        const val UI_MODE_NIGHT_YES = 0x20

        private val UI_MODE_NIGHT_VALUES = mapOf(
            UI_MODE_NIGHT_NO to "notnight",
            UI_MODE_NIGHT_YES to "night"
        )

        const val UI_MODE_TYPE_MASK = 0x0F
        const val UI_MODE_TYPE_DESK = 0x02
        const val UI_MODE_TYPE_CAR = 0x03
        const val UI_MODE_TYPE_TELEVISION = 0x04
        const val UI_MODE_TYPE_APPLIANCE = 0x05
        const val UI_MODE_TYPE_WATCH = 0x06
        const val UI_MODE_TYPE_VR_HEADSET = 0x07

        private val UI_MODE_TYPE_VALUES = mapOf(
            UI_MODE_TYPE_DESK to "desk",
            UI_MODE_TYPE_CAR to "car",
            UI_MODE_TYPE_TELEVISION to "television",
            UI_MODE_TYPE_APPLIANCE to "appliance",
            UI_MODE_TYPE_WATCH to "watch",
            UI_MODE_TYPE_VR_HEADSET to "vrheadset"
        )

        /** The minimum size in bytes that a [ResourceConfiguration] can be. */
        private const val MIN_SIZE = 28

        /** The minimum size in bytes that this configuration must be to contain screen config info. */
        private const val SCREEN_CONFIG_MIN_SIZE = 32

        /** The minimum size in bytes that this configuration must be to contain screen dp info. */
        private const val SCREEN_DP_MIN_SIZE = 36

        /** The minimum size in bytes that this configuration must be to contain locale info. */
        private const val LOCALE_MIN_SIZE = 48

        /** The minimum size in bytes that this config must be to contain the screenConfig extension. */
        private const val SCREEN_CONFIG_EXTENSION_MIN_SIZE = 52

        /** The size of resource configurations in bytes for the latest version of Android resources. */
        const val SIZE = SCREEN_CONFIG_EXTENSION_MIN_SIZE

        private fun unpackLanguageOrRegion(value: ByteArray, base: Int): String {
            check(value.size == 2) { "Language or region value must be 2 bytes." }
            if (value[0].toInt() == 0 && value[1].toInt() == 0) {
                return ""
            }
            if (value[0].unsignedToInt() and 0x80 != 0) {
                val result = ByteArray(3)
                result[0] = (base + (value[1].toInt() and 0x1F)).toByte()
                result[1] = (base + (value[1].toInt() and 0xE0 ushr 5) + (value[0].toInt() and 0x03 shl 3)).toByte()
                result[2] = (base + (value[0].toInt() and 0x7C ushr 2)).toByte()
                return String(result, StandardCharsets.US_ASCII)
            }
            return String(value, StandardCharsets.US_ASCII)
        }

        /**
         * Packs a 2 or 3 character language string into two bytes. If this is a 2 character string the
         * returned bytes is simply the string bytes, if this is a 3 character string we use a packed
         * format where the two bytes are:
         *
         * ```
         * +--+--+--+--+--+--+--+--+  +--+--+--+--+--+--+--+--+
         * |B |2 |2 |2 |2 |2 |1 |1 |  |1 |1 |1 |0 |0 |0 |0 |0 |
         * +--+--+--+--+--+--+--+--+  +--+--+--+--+--+--+--+--+
         * ```
         * B: if bit set indicates this is a 3 character string (languages are always old style 7 bit
         * ascii chars only, so this is never set for a two character language)
         *
         * 2: The third character - 0x61
         *
         * 1: The second character - 0x61
         *
         * 0: The first character - 0x61
         *
         * Languages are always lower case chars, so max is within 5 bits (z = 11001)
         *
         * @param language The language to pack.
         * @return The two byte representation of the language
         */
        fun packLanguage(language: String): ByteArray {
            val unpacked = language.toByteArray(StandardCharsets.US_ASCII)
            if (unpacked.size == 2) {
                return unpacked
            }
            val base = 0x61
            val result = ByteArray(2)
            check(unpacked.size == 3)
            for (value in unpacked) {
                check(value >= 'a'.code.toByte() && value <= 'z'.code.toByte())
            }
            result[0] = (unpacked[2] - base shl 2 or (unpacked[1] - base shr 3) or 0x80).toByte()
            result[1] = (unpacked[0] - base or (unpacked[1] - base shl 5)).toByte()
            return result
        }
    }
}

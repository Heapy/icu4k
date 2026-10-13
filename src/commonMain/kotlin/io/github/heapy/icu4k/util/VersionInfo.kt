// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/**
 * Four unsigned byte-sized version components, ordered lexicographically.
 *
 * Port of ICU4J VersionInfo's value operations. Instances use value equality;
 * unlike ICU4J, they are not interned. Historical constants and resource-backed
 * timezone version lookup are not yet ported.
 */
class VersionInfo private constructor(private val packed: UInt) : Comparable<VersionInfo> {
    val major: Int get() = (packed shr 24).toInt()
    val minor: Int get() = ((packed shr 16) and 255u).toInt()
    val milli: Int get() = ((packed shr 8) and 255u).toInt()
    val micro: Int get() = (packed and 255u).toInt()

    override fun compareTo(other: VersionInfo): Int = packed.compareTo(other.packed)
    override fun equals(other: Any?): Boolean = other is VersionInfo && packed == other.packed
    override fun hashCode(): Int = packed.toInt()
    override fun toString(): String = "$major.$minor.$milli.$micro"

    fun getVersionString(minDigits: Int, maxDigits: Int): String {
        require(minDigits in 1..4 && maxDigits in minDigits..4) { "Invalid min/maxDigits range" }
        val components = listOf(major, minor, milli, micro)
        var count = maxDigits
        while (count > minDigits && components[count - 1] == 0) count--
        return components.take(count).joinToString(".")
    }

    companion object {
        val ICU_VERSION: VersionInfo = getInstance(78, 3)
        val UNICODE_17_0: VersionInfo = getInstance(17)

        fun getInstance(major: Int, minor: Int = 0, milli: Int = 0, micro: Int = 0): VersionInfo {
            require(major in 0..255 && minor in 0..255 && milli in 0..255 && micro in 0..255) {
                "Version components must be in 0..255"
            }
            return VersionInfo((major.toUInt() shl 24) or (minor.toUInt() shl 16) or
                (milli.toUInt() shl 8) or micro.toUInt())
        }

        /** Preserves ICU's acceptance of empty components (including an empty string). */
        fun getInstance(version: String): VersionInfo {
            val components = IntArray(4)
            var component = 0
            for (char in version) {
                require(component < 4) { "Version has more than four components" }
                if (char == '.') {
                    component++
                } else {
                    require(char in '0'..'9') { "Invalid version digit" }
                    // Check before multiplication so oversized input cannot wrap around.
                    val next = components[component] * 10 + (char - '0')
                    require(next <= 255) { "Version components must be in 0..255" }
                    components[component] = next
                }
            }
            return getInstance(components[0], components[1], components[2], components[3])
        }
    }
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Common resource lookup boundary replacing ClassLoader in binary bundle APIs. */
fun interface ICUResourceLoader {
    fun getData(fullName: String): BinaryBuffer?

    /** Class misses permit properties; a rejected class load does not. */
    fun loadJavaBundleClass(name: String): JavaBundleClassResult = JavaBundleClassResult.LoadProperties

    fun loadJavaProperties(fullName: String): JavaResourceBundle? {
        val data = try { getData(fullName) } catch (_: io.github.heapy.icu4k.util.ICUUncheckedIOException) { return null } ?: return null
        return try { JavaPropertiesBundle.read(data.slice().readBytes(data.remaining)) } catch (_: Exception) { null }
    }

    /** Visits immediate resource file names, without directory prefixes. */
    fun visitResources(directory: String, visitor: (String) -> Unit) {}

    /** ASCII lines, retaining whitespace and publishing lines incrementally. */
    fun readAsciiLines(fullName: String, visitor: (String) -> Unit) {
        val data = getData(fullName) ?: return
        var line = StringBuilder()
        var afterCR = false
        for (index in data.position until data.limit) {
            val byte = data.getByte(index).toInt() and 255
            if (byte == 10 && afterCR) { afterCR = false; continue }
            afterCR = byte == 13
            if (byte == 10 || byte == 13) {
                visitor(line.toString()); line = StringBuilder()
            } else line.append(if (byte < 128) byte.toChar() else '\uFFFD')
        }
        if (line.isNotEmpty()) visitor(line.toString())
    }
}

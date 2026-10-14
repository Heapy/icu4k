// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import java.io.IOException
import java.net.URL
import java.security.AccessController
import java.security.PrivilegedAction

/** JVM ClassLoader boundary for binary resources and locale directory enumeration. */
class ICUClassLoaderResourceLoader(private val loader: ClassLoader) : ICUResourceLoader {
    override fun getData(fullName: String): BinaryBuffer? = try {
        loader.getResourceAsStream(fullName)?.use { BinaryBuffer(it.readBytes()) }
    } catch (failure: IOException) { throw ICUUncheckedIOException(failure) }

    override fun readAsciiLines(fullName: String, visitor: (String) -> Unit) {
        try {
            val stream = loader.getResourceAsStream(fullName) ?: return
            val reader = stream.reader(Charsets.US_ASCII).buffered()
            try {
                while (true) visitor(reader.readLine() ?: break)
            } finally { reader.close() }
        } catch (_: IOException) { }
    }

    @Suppress("DEPRECATION", "removal")
    override fun visitResources(directory: String, visitor: (String) -> Unit) {
        AccessController.doPrivileged(PrivilegedAction {
            try {
                val urls = loader.getResources(directory)
                if (urls != null) while (urls.hasMoreElements()) visit(urls.nextElement(), visitor)
            } catch (failure: IOException) { if (debug) println("ouch: ${failure.message}") }
            null
        })
    }

    private fun visit(url: URL, visitor: (String) -> Unit) {
        val handler = URLHandler.get(url)
        if (handler != null) handler.guide(URLHandler.URLVisitor(visitor), false)
        else if (debug) println("handler for $url is null")
    }
    companion object { private val debug = ICUDebug.enabled("localedata") }
}

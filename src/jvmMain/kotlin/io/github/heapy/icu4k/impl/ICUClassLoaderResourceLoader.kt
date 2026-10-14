// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
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
            } catch (_: IOException) { }
            null
        })
    }

    private fun visit(url: URL, visitor: (String) -> Unit) {
        when (url.protocol) {
            "file" -> {
                val file = try { File(url.toURI()).takeIf { it.exists() } } catch (_: Exception) { null } ?: return
                if (file.isDirectory) file.listFiles()?.forEach { if (!it.isDirectory) visitor(it.name) }
                else visitor(file.name)
            }
            "jar", "wsjar" -> {
                val path = url.path
                val delimiter = path.lastIndexOf("!/")
                val prefix = if (delimiter >= 0) path.substring(delimiter + 2) else path
                val jar = try {
                    val normalized = if (url.protocol == "jar") url else URL("jar" + url.toString().substring(url.toString().indexOf(':')))
                    (normalized.openConnection() as JarURLConnection).jarFile
                } catch (_: Exception) { return }
                // JarURLConnection owns/caches the JarFile; ICU4J leaves it open.
                try {
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
                        val relative = entry.name.substring(prefix.length)
                        val slash = relative.lastIndexOf('/')
                        if (slash > 0) continue
                        visitor(if (slash != -1) relative.substring(slash + 1) else relative)
                    }
                } catch (_: Exception) { }
            }
        }
    }
}

// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.JarURLConnection
import java.net.URISyntaxException
import java.net.URL

/** JVM URL overload; file/archive traversal remains in Common Kotlin. */
fun URLHandler.Companion.get(url: URL?): URLHandler? = JVMURLHandlers.get(url)
internal actual fun urlHandlerError(message: Any?) { System.err.println(message) }

private object JVMURLHandlers {
    private val debug = URLHandler.DEBUG
    private val handlers: Map<String, Method>? = loadHandlers()
    private fun loadHandlers(): Map<String, Method>? {
        var result: MutableMap<String, Method>? = null
        var reader: BufferedReader? = null
        try {
            val input = ClassLoaderUtil.getClassLoader(URLHandler::class.java).getResourceAsStream(URLHandler.PROPNAME)
            if (input != null) {
                reader = BufferedReader(InputStreamReader(input))
                while (true) {
                    val line = (reader.readLine() ?: break).trim { it <= '\u0020' }
                    if (line.isEmpty() || line[0] == '#') continue
                    val equal = line.indexOf('=')
                    if (equal == -1) { if (debug) urlHandlerError("bad urlhandler line: '$line'"); break }
                    val key = line.substring(0, equal).trim { it <= '\u0020' }
                    val value = line.substring(equal + 1).trim { it <= '\u0020' }
                    try {
                        val method = Class.forName(value).getDeclaredMethod("get", URL::class.java)
                        if (result == null) result = HashMap()
                        result[key] = method
                    } catch (failure: ClassNotFoundException) { if (debug) urlHandlerError(failure) }
                    catch (failure: NoSuchMethodException) { if (debug) urlHandlerError(failure) }
                    catch (failure: SecurityException) { if (debug) urlHandlerError(failure) }
                }
                reader.close()
            }
        } catch (failure: Throwable) { if (debug) urlHandlerError(failure) }
        finally { if (reader != null) try { reader.close() } catch (_: IOException) {} }
        return result
    }
    fun get(url: URL?): URLHandler? {
        if (url == null) return null
        val method = handlers?.get(url.protocol)
        if (method != null) {
            try {
                val handler = method.invoke(null, url) as URLHandler?
                if (handler != null) return handler
            } catch (failure: IllegalAccessException) { if (debug) urlHandlerError(failure) }
            catch (failure: IllegalArgumentException) { if (debug) urlHandlerError(failure) }
            catch (failure: InvocationTargetException) { if (debug) urlHandlerError(failure) }
        }
        return getDefault(url)
    }
    private fun getDefault(url: URL): URLHandler? = try {
        when (url.protocol) {
            "file" -> {
                val file = try { File(url.toURI()) } catch (_: URISyntaxException) { null }
                if (file == null || !file.exists()) {
                    if (debug) urlHandlerError("file does not exist - $url")
                    throw IllegalArgumentException()
                }
                FileHandler(JvmURLFile(file))
            }
            "jar", "wsjar" -> {
                try {
                    val path = url.path
                    val delimiter = path.lastIndexOf("!/")
                    val prefix = if (delimiter >= 0) path.substring(delimiter + 2) else path
                    val actual = if (url.protocol == "jar") url else URL("jar" + url.toString().substring(url.toString().indexOf(':')))
                    val jar = (actual.openConnection() as JarURLConnection).jarFile
                    URLHandler.forArchive(prefix) {
                        val entries = jar.entries()
                        object : Iterator<URLHandler.ArchiveEntry> {
                            override fun hasNext(): Boolean = entries.hasMoreElements()
                            override fun next(): URLHandler.ArchiveEntry = entries.nextElement().let { URLHandler.ArchiveEntry(it.name, it.isDirectory) }
                        }
                    }
                } catch (failure: Exception) {
                    if (debug) urlHandlerError("icurb jar error: $failure")
                    throw IllegalArgumentException("jar error: ${failure.message}")
                }
            }
            else -> null
        }
    } catch (_: Exception) { null }
}
private class JvmURLFile(private val file: File) : URLFile {
    override val name: String get() = file.name
    override val isDirectory: Boolean get() = file.isDirectory
    override fun children(): List<URLFile>? = file.listFiles()?.map { JvmURLFile(it) }
}

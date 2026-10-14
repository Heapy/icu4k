// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import java.io.BufferedInputStream
import java.io.IOException
import java.net.URL
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.ResourceBundle
import java.util.PropertyResourceBundle

/** JVM ClassLoader boundary for binary resources and locale directory enumeration. */
class ICUClassLoaderResourceLoader(private val loader: ClassLoader) : ICUResourceLoader {
    override fun getData(fullName: String): BinaryBuffer? = try {
        loader.getResourceAsStream(fullName)?.use { BinaryBuffer(it.readBytes()) }
    } catch (failure: IOException) { throw ICUUncheckedIOException(failure) }

    @Suppress("DEPRECATION")
    override fun loadJavaBundleClass(name: String): JavaBundleClassResult = try {
        JavaBundleClassResult.Found(JvmJavaResourceBundle(loader.loadClass(name).asSubclass(ResourceBundle::class.java).newInstance()))
    } catch (_: ClassNotFoundException) { JavaBundleClassResult.LoadProperties }
    catch (_: NoClassDefFoundError) { JavaBundleClassResult.LoadProperties }
    catch (failure: Exception) { JavaBundleClassResult.Rejected(failure) }

    @Suppress("DEPRECATION", "removal")
    override fun loadJavaProperties(fullName: String): JavaResourceBundle? {
        val stream = AccessController.doPrivileged(PrivilegedAction { loader.getResourceAsStream(fullName) }) ?: return null
        val buffered = BufferedInputStream(stream)
        try { return JvmJavaResourceBundle(PropertyResourceBundle(buffered)) }
        catch (_: Exception) { return null }
        finally { try { buffered.close() } catch (_: Exception) {} }
    }

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
        val scanDebug = debug
        AccessController.doPrivileged(PrivilegedAction {
            try {
                val urls = loader.getResources(directory)
                if (urls != null) while (urls.hasMoreElements()) visit(urls.nextElement(), visitor)
            } catch (failure: IOException) { if (scanDebug) println("ouch: ${failure.message}") }
            null
        })
    }

    private fun visit(url: URL, visitor: (String) -> Unit) {
        val handler = URLHandler.get(url)
        if (handler != null) handler.guide(URLHandler.URLVisitor(visitor), false)
        else if (debug) println("handler for $url is null")
    }
    companion object { private val debug by lazy { ICUDebug.enabled("localedata") } }
}

private class JvmJavaResourceBundle(private val bundle: ResourceBundle) : JavaResourceBundle {
    override val className: String get() = bundle.javaClass.name
    override fun getObject(key: String): Any? = try { bundle.getObject(key) } catch (failure: java.util.MissingResourceException) {
        throw io.github.heapy.icu4k.util.MissingResourceException(failure.message, failure.className, failure.key)
    }
    override fun getKeys(): io.github.heapy.icu4k.util.Enumeration<String> {
        val keys = bundle.keys
        return object : io.github.heapy.icu4k.util.Enumeration<String> {
            override fun hasMoreElements(): Boolean = keys.hasMoreElements()
            override fun nextElement(): String = keys.nextElement()
        }
    }
}

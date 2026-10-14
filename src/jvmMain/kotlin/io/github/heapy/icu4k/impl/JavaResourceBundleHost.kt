// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "removal")
package io.github.heapy.icu4k.impl

import java.security.AccessController
import java.security.PrivilegedAction

internal actual fun defaultJavaResourceLoader(): ICUResourceLoader = ICUClassLoaderResourceLoader(ClassLoaderUtil.getClassLoader())
internal actual fun javaPropertyResourceEncoding(): String? = AccessController.doPrivileged(PrivilegedAction { System.getProperty("java.util.PropertyResourceBundle.encoding", "") })

private val defaultICULoader: ICUResourceLoader by lazy {
    val classpath = ICUClassLoaderResourceLoader(ClassLoaderUtil.getClassLoader(ICUResourceBundle::class.java))
    object : ICUResourceLoader {
        override fun getData(fullName: String) = ICUResourceBundleReader.embeddedLoader.getData(fullName) ?: classpath.getData(fullName)
        override fun loadJavaBundleClass(name: String) = classpath.loadJavaBundleClass(name)
        override fun loadJavaProperties(fullName: String) = classpath.loadJavaProperties(fullName)
        override fun visitResources(directory: String, visitor: (String) -> Unit) {
            ICUResourceBundleReader.embeddedLoader.visitResources(directory, visitor)
            classpath.visitResources(directory, visitor)
        }
    }
}
internal actual fun defaultICUResourceLoader(): ICUResourceLoader = defaultICULoader

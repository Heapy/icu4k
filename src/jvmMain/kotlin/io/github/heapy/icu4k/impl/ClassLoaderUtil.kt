// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "removal")
package io.github.heapy.icu4k.impl

import java.security.AccessController
import java.security.PrivilegedAction

/** JVM class-loader selection boundary, including the bootstrap fallback. */
object ClassLoaderUtil {
    private class BootstrapClassLoader : ClassLoader(Any::class.java.classLoader)
    @Volatile private var bootstrap: ClassLoader? = null
    private fun bootstrapLoader(): ClassLoader {
        if (bootstrap == null) synchronized(this) {
            if (bootstrap == null) bootstrap = if (System.getSecurityManager() != null)
                AccessController.doPrivileged(PrivilegedAction { BootstrapClassLoader() }) else BootstrapClassLoader()
        }
        return bootstrap!!
    }
    @JvmStatic fun getClassLoader(cls: Class<*>?): ClassLoader = cls!!.classLoader ?: getClassLoader()
    @JvmStatic fun getClassLoader(): ClassLoader = Thread.currentThread().contextClassLoader
        ?: ClassLoader.getSystemClassLoader() ?: bootstrapLoader()
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "removal")
package io.github.heapy.icu4k.impl

import java.security.AccessController
import java.security.PrivilegedAction

internal actual fun defaultJavaResourceLoader(): ICUResourceLoader = ICUClassLoaderResourceLoader(ClassLoaderUtil.getClassLoader())
internal actual fun javaPropertyResourceEncoding(): String? = AccessController.doPrivileged(PrivilegedAction { System.getProperty("java.util.PropertyResourceBundle.encoding", "") })

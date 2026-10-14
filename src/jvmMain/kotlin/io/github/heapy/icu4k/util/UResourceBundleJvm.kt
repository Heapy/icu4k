// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUClassLoaderResourceLoader
import java.util.Locale

/** Java locale factories retain keyword stripping and nullable default semantics. */
fun UResourceBundle.Companion.getBundleInstance(baseName: String?, locale: Locale?): UResourceBundle =
    getBundleInstance(baseName, ULocale.forLocale(locale))

fun UResourceBundle.Companion.getBundleInstance(baseName: String?, locale: Locale?, loader: ClassLoader?): UResourceBundle =
    getBundleInstance(baseName, ULocale.forLocale(locale), loader?.let(::ICUClassLoaderResourceLoader))

fun UResourceBundle.Companion.getBundleInstance(baseName: String?, locale: ULocale?, loader: ClassLoader?): UResourceBundle =
    getBundleInstance(baseName, locale, loader?.let(::ICUClassLoaderResourceLoader))

@JvmOverloads
fun UResourceBundle.Companion.getBundleInstance(baseName: String?, localeName: String?, loader: ClassLoader?, disableFallback: Boolean = false): UResourceBundle =
    getBundleInstance(baseName, localeName, loader?.let(::ICUClassLoaderResourceLoader), disableFallback)

fun UResourceBundle.getLocale(): Locale = getULocale().toLocale()

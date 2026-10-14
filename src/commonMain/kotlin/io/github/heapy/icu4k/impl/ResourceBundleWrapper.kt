// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*

/** Shared wrapper, recursive loading and cache for Java class/property bundles. */
class ResourceBundleWrapper private constructor(private val bundle: JavaResourceBundle) : UResourceBundle() {
    private lateinit var localeID: String
    private lateinit var baseName: String
    private lateinit var keys: List<String>
    override fun handleGetObject(aKey: String): Any {
        var current: ResourceBundleWrapper? = this
        var result: Any? = null
        while (current != null) {
            try { result = current.bundle.getObject(aKey); break }
            catch (_: MissingResourceException) { current = current.getParent() as ResourceBundleWrapper? }
        }
        return result ?: throw MissingResourceException("Can't find resource for bundle $baseName, key $aKey", resourceClassName(), aKey)
    }
    override fun getKeys(): Enumeration<String> = enumeration(keys)
    private fun initKeysVector() {
        var current: ResourceBundleWrapper? = this
        val result = ArrayList<String>()
        keys = result
        while (current != null) {
            val iterator = current.bundle.getKeys()
            while (iterator.hasMoreElements()) {
                val key = iterator.nextElement()
                if (key !in result) result.add(key)
            }
            current = current.getParent() as ResourceBundleWrapper?
        }
    }
    override fun getLocaleID(): String = localeID
    override fun getBaseName(): String = bundle.className.replace('.', '/')
    override fun getULocale(): ULocale = ULocale(localeID)
    public override fun getParent(): UResourceBundle? = parentBundle

    companion object {
        private val debug = ICUDebug.enabled("resourceBundleWrapper")
        private val bundles = object : SoftCache<String, ResourceBundleWrapper, () -> ResourceBundleWrapper?>() {
            override fun createInstance(key: String, data: () -> ResourceBundleWrapper?): ResourceBundleWrapper? = data()
        }
        fun getBundleInstance(baseName: String, localeID: String, loader: ICUResourceLoader? = null, disableFallback: Boolean = false): ResourceBundleWrapper {
            val provider = loader ?: defaultJavaResourceLoader()
            val defaultID = if (disableFallback) null else ULocale.getBaseName(resourceDefaultLocaleName())
            return instantiateBundle(baseName, localeID, defaultID, provider, disableFallback) ?: run {
                val separator = if ('/' in baseName) "/" else "_"
                throw MissingResourceException("Could not find the bundle $baseName$separator$localeID", "", "")
            }
        }
        private fun instantiateBundle(baseName: String, localeID: String, defaultID: String?, loader: ICUResourceLoader, disableFallback: Boolean): ResourceBundleWrapper? {
            val name = if (localeID.isEmpty()) baseName else "${baseName}_$localeID"
            val cacheKey = if (disableFallback) name else "$name#$defaultID"
            return bundles.getInstance(cacheKey) {
                var parent: ResourceBundleWrapper? = null
                var parentIsRoot = false
                val index = localeID.lastIndexOf('_')
                if (index != -1) parent = instantiateBundle(baseName, localeID.substring(0, index), defaultID, loader, disableFallback)
                else if (localeID.isNotEmpty()) { parent = instantiateBundle(baseName, "", defaultID, loader, disableFallback); parentIsRoot = true }
                var result: ResourceBundleWrapper? = null
                var properties = false
                val classResult = try { loader.loadJavaBundleClass(name) } catch (failure: Exception) { JavaBundleClassResult.Rejected(failure) }
                when (classResult) {
                    is JavaBundleClassResult.Found -> result = wrap(classResult.bundle, baseName, localeID, parent)
                    JavaBundleClassResult.LoadProperties -> properties = true
                    is JavaBundleClassResult.Rejected -> if (debug) { println("failure"); println(classResult.failure) }
                }
                if (properties) {
                    try {
                        loader.loadJavaProperties(name.replace('.', '/') + ".properties")?.let { result = wrap(it, baseName, localeID, parent) }
                        if (result == null && !disableFallback && localeID.isNotEmpty() && '_' !in localeID &&
                            !localeIDStartsWithLangSubtag(defaultID!!, localeID)) {
                            result = instantiateBundle(baseName, defaultID, defaultID, loader, disableFallback)
                        }
                        if (result == null && (!parentIsRoot || !disableFallback)) result = parent
                    } catch (failure: Exception) { if (debug) { println("failure"); println(failure) } }
                }
                result?.initKeysVector()
                if (result == null && debug) println("Returning null for ${baseName}_$localeID")
                result
            }
        }
        private fun wrap(bundle: JavaResourceBundle, baseName: String, localeID: String, parent: ResourceBundleWrapper?): ResourceBundleWrapper =
            ResourceBundleWrapper(bundle).also { it.setParent(parent); it.baseName = baseName; it.localeID = localeID }
        private fun localeIDStartsWithLangSubtag(localeID: String, lang: String): Boolean =
            localeID.startsWith(lang) && (localeID.length == lang.length || localeID[lang.length] == '_')
    }
}

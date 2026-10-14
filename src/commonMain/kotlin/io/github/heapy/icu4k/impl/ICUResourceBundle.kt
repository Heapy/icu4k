// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.concurrent.atomics.AtomicReference

/** Shared ICU bundle identity and containment. Loading, resource subclasses and fallback paths are being ported. */
open class ICUResourceBundle : UResourceBundle {
    protected class WholeBundle internal constructor(
        val baseName: String,
        val localeID: String,
        internal val reader: ICUResourceBundleReader,
    ) {
        val ulocale = ULocale(localeID)
        internal val topLevelKeys = AtomicReference<Set<String>?>(null)
    }
    protected val wholeBundle: WholeBundle
    private val container: ICUResourceBundle?
    protected var resourceKey: String? = null
    protected constructor(wholeBundle: WholeBundle) {
        this.wholeBundle = wholeBundle
        container = null
    }
    protected constructor(container: ICUResourceBundle, key: String) {
        wholeBundle = container.wholeBundle
        this.container = container
        resourceKey = key
        parentBundle = container.parentBundle
    }
    override fun getLocaleID(): String = wholeBundle.localeID
    override fun getBaseName(): String = wholeBundle.baseName
    override fun getULocale(): ULocale = wholeBundle.ulocale
    fun isRoot(): Boolean = wholeBundle.localeID.isEmpty() || wholeBundle.localeID == "root"
    public override fun getParent(): ICUResourceBundle? = parentBundle as ICUResourceBundle?
    override fun getKey(): String? = resourceKey
    override fun isTopLevelResource(): Boolean = container == null
    override fun handleGetKeys(): Enumeration<String> = enumeration(handleKeySet())
    fun getTopLevelKeySet(): Set<String>? = wholeBundle.topLevelKeys.load()
    fun setTopLevelKeySet(keys: Set<String>?) { wholeBundle.topLevelKeys.store(keys) }
    internal override fun topLevelKeySet(): Set<String>? = getTopLevelKeySet()
    internal override fun cacheTopLevelKeySet(keys: Set<String>) { setTopLevelKeySet(keys) }
    override fun equals(other: Any?): Boolean = this === other || other is ICUResourceBundle &&
        getBaseName() == other.getBaseName() && getLocaleID() == other.getLocaleID()
    override fun hashCode(): Int = 42
    companion object {
        const val NO_INHERITANCE_MARKER = "∅∅∅"
        protected const val INSTALLED_LOCALES = "InstalledLocales"
    }
}

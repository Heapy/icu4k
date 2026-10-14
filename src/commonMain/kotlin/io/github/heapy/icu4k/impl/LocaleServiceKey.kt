// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

/** ICULocaleService.LocaleKey extracted pending the complete locale-service facade. */
internal open class LocaleServiceKey protected constructor(primaryID: String?, canonicalPrimaryID: String?, canonicalFallbackID: String?, private val kind: Int) : ICUService.Key(primaryID) {
    private var variantStart = 0
    private val primary: String
    private var fallback: String?
    private var current: String?
    init {
        if (canonicalPrimaryID == null || canonicalPrimaryID.equals("root", ignoreCase = true)) {
            primary = ""; fallback = null
        } else {
            val index = canonicalPrimaryID.indexOf('@')
            if (index == 4 && canonicalPrimaryID.regionMatches(0, "root", 0, 4, ignoreCase = true)) {
                primary = canonicalPrimaryID.substring(4); variantStart = 0; fallback = null
            } else {
                primary = canonicalPrimaryID; variantStart = index
                fallback = if (canonicalFallbackID == null || primary == canonicalFallbackID) "" else canonicalFallbackID
            }
        }
        current = if (variantStart == -1) primary else primary.substring(0, variantStart)
    }
    open fun prefix(): String? = if (kind == KIND_ANY) null else kind().toString()
    open fun kind(): Int = kind
    override fun canonicalID(): String? = primary
    override fun currentID(): String? = current
    override fun currentDescriptor(): String? {
        val id = currentID() ?: return null
        return buildString {
            if (kind != KIND_ANY) append(prefix())
            append('/'); append(id)
            if (variantStart != -1) append(primary.substring(variantStart))
        }
    }
    open fun canonicalLocale(): ULocale = ULocale(primary)
    open fun currentLocale(): ULocale = if (variantStart == -1) ULocale(current!!) else ULocale(current + primary.substring(variantStart))
    override fun fallback(): Boolean {
        val id = current!!
        var index = id.lastIndexOf('_')
        if (index != -1) {
            do { index-- } while (index >= 0 && id[index] == '_')
            current = id.substring(0, index + 1)
            return true
        }
        val next = fallback
        if (next != null) {
            current = next; fallback = if (next.isEmpty()) null else ""
            return true
        }
        current = null
        return false
    }
    override fun isFallbackOf(idToCheck: String?): Boolean {
        val parent = canonicalID()!!
        val child = idToCheck!!
        if (!child.startsWith(parent)) return false
        return parent.length == child.length || child[parent.length] == '_'
    }
    companion object {
        const val KIND_ANY = -1
        fun createWithCanonicalFallback(primaryID: String?, canonicalFallbackID: String?): LocaleServiceKey? = createWithCanonicalFallback(primaryID, canonicalFallbackID, KIND_ANY)
        fun createWithCanonicalFallback(primaryID: String?, canonicalFallbackID: String?, kind: Int): LocaleServiceKey? = primaryID?.let { LocaleServiceKey(it, ULocale.getName(it), canonicalFallbackID, kind) }
        fun createWithCanonical(locale: ULocale?, canonicalFallbackID: String?, kind: Int): LocaleServiceKey? = locale?.getName()?.let { LocaleServiceKey(it, it, canonicalFallbackID, kind) }
    }
}

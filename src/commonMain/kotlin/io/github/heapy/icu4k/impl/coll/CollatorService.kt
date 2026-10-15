// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.text.Collator
import io.github.heapy.icu4k.text.RuleBasedCollator
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.atomics.AtomicReference

/** Lazy CollatorServiceShim state with shared locale-service prototypes. */
internal object CollatorService {
    private val published = AtomicReference<ICULocaleService?>(null)
    private val lock = createAvailabilityLock()
    fun current(): ICULocaleService? = published.load()
    fun get(): ICULocaleService = published.load() ?: lock.withLock {
        published.load() ?: CService().also { published.store(it) }
    }
    fun getInstance(locale: ULocale?): Collator {
        val service = get()
        try {
            val actual = arrayOfNulls<ULocale>(1)
            val coll = service.get(locale,actual) as Collator?
                ?: throw MissingResourceException("Could not locate Collator data","","")
            return coll.clone()
        } catch (e: CloneNotSupportedException) { throw ICUCloneNotSupportedException(e) }
    }
    fun registerInstance(collator: Collator?, locale: ULocale?): Any {
        val service = get()
        collator!!.setLocale(locale,locale)
        return service.registerObject(collator,locale)
    }
    fun registerFactory(factory: Collator.CollatorFactory?): Any {
        val service = get()
        val f = factory!!
        val wrapped = object : ICULocaleService.LocaleKeyFactory(f.visible()) {
            override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any? = f.createCollator(loc)
            override fun getDisplayName(id: String?, locale: ULocale?): String? = f.getDisplayName(ULocale(id!!),locale)
            override fun getSupportedIDs(): Set<String> = f.getSupportedLocaleIDs()
        }
        return service.registerFactory(wrapped)
    }
    fun unregister(key: Any?): Boolean {
        val service = current() ?: return false
        return service.unregisterFactory(key as ICUService.Factory?)
    }
    fun getAvailableULocales(): Array<ULocale> {
        val service = current()
        return if (service == null || service.isDefault()) ICUResourceBundle.getAvailableULocales(ICUData.ICU_COLLATION_BASE_NAME)!!
            else service.getAvailableULocales()
    }
    fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String? {
        val service = get()
        val id = objectLocale!!.getName()
        return service.getDisplayName(id,displayLocale)
    }
    private class CService : ICULocaleService("Collator") {
        init {
            registerFactory(object : ICUResourceBundleFactory(ICUData.ICU_COLLATION_BASE_NAME) {
                override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any = makeInstance(loc)
            })
            markDefault()
        }
        override fun validateFallbackLocale(): String = ""
        override fun handleDefault(key: ICUService.Key?, actualIDReturn: Array<String?>?): Any? {
            if (actualIDReturn != null) actualIDReturn[0] = "root"
            return try { makeInstance(ULocale.ROOT) } catch (_: MissingResourceException) { null }
        }
    }
    private fun makeInstance(desired: ULocale?): Collator {
        val valid = Output(ULocale.ROOT)
        val t = CollationLoader.loadTailoring(desired,valid)
        return RuleBasedCollator(t,valid.value)
    }
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.createTextTrieMapLock
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.CaseInsensitiveString
import io.github.heapy.icu4k.util.Enumeration
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale

/** Dynamic/static lookup and visibility graph, kept separate from instance construction. */
internal class TransliteratorRegistryStore {
    internal class LocaleEntry(var rule: String, var direction: Int)
    private val registry = Index<Array<Any?>>()
    private val sources = Index<Index<Variants>>()
    private val available = Index<Unit>()

    fun put(id: String, entry: Any?, visible: Boolean) {
        val stv = TransliteratorIDParser.IDtoSTV(id)
        val canonical = TransliteratorIDParser.STVtoID(stv[0]!!, stv[1]!!, stv[2]!!)
        register(canonical, stv[0]!!, stv[1]!!, stv[2]!!, arrayOf(entry), visible)
    }
    fun remove(id: String) {
        val stv = TransliteratorIDParser.IDtoSTV(id)
        val canonical = TransliteratorIDParser.STVtoID(stv[0]!!, stv[1]!!, stv[2]!!)
        registry.remove(CaseInsensitiveString(canonical))
        removeSTV(stv[0]!!, stv[1]!!, stv[2]!!)
        available.remove(CaseInsensitiveString(canonical))
    }
    fun getAvailableIDs(): Enumeration<String> = available.enumeration()
    fun getAvailableSources(): Enumeration<String> = sources.enumeration()
    fun getAvailableTargets(source: String): Enumeration<String> =
        sources[CaseInsensitiveString(source)]?.enumeration() ?: missingEnumeration()
    fun getAvailableVariants(source: String, target: String): Enumeration<String> =
        sources[CaseInsensitiveString(source)]?.get(CaseInsensitiveString(target))?.enumeration() ?: missingEnumeration()

    private fun register(id: String, source: String, target: String, variant: String, wrapper: Array<Any?>, visible: Boolean) {
        val key = CaseInsensitiveString(id)
        registry.put(key, wrapper)
        if (visible) { registerSTV(source, target, variant); available.put(key, Unit) }
        else { removeSTV(source, target, variant); available.remove(key) }
    }
    private fun registerSTV(source: String, target: String, variant: String) {
        val src = CaseInsensitiveString(source)
        val trg = CaseInsensitiveString(target)
        var targets = sources[src]
        if (targets == null) { targets = Index(); sources.put(src, targets) }
        var variants = targets[trg]
        if (variants == null) { variants = Variants(); targets.put(trg, variants) }
        variants.add(CaseInsensitiveString(variant), variant.isEmpty())
    }
    private fun removeSTV(source: String, target: String, variant: String) {
        val src = CaseInsensitiveString(source)
        val trg = CaseInsensitiveString(target)
        val targets = sources[src] ?: return
        val variants = targets[trg] ?: return
        variants.remove(CaseInsensitiveString(variant))
        if (variants.isEmpty()) {
            targets.remove(trg)
            if (targets.isEmpty()) sources.remove(src)
        }
    }
    fun find(id: String): Array<Any?>? {
        val stv = TransliteratorIDParser.IDtoSTV(id)
        val src = TransliteratorRegistrySpec(stv[0]!!)
        val trg = TransliteratorRegistrySpec(stv[1]!!)
        val variant = stv[2]!!
        if (variant.isNotEmpty()) {
            findDynamic(src, trg, variant)?.let { return it }
            findStatic(src, trg, variant)?.let { return it }
        }
        while (true) {
            src.reset()
            while (true) {
                findDynamic(src, trg, "")?.let { return it }
                findStatic(src, trg, "")?.let { return it }
                if (!src.hasFallback()) break
                src.next()
            }
            if (!trg.hasFallback()) break
            trg.next()
        }
        return null
    }
    private fun findDynamic(src: TransliteratorRegistrySpec, trg: TransliteratorRegistrySpec, variant: String): Array<Any?>? =
        registry[CaseInsensitiveString(TransliteratorIDParser.STVtoID(src.get()!!, trg.get()!!, variant))]
    private fun findStatic(src: TransliteratorRegistrySpec, trg: TransliteratorRegistrySpec, variant: String): Array<Any?>? {
        val entry = when {
            src.isLocale() -> findInBundle(src, trg, variant, 0)
            trg.isLocale() -> findInBundle(trg, src, variant, 1)
            else -> null
        }
        if (entry != null) {
            val source = src.getTop()
            val target = trg.getTop()
            register(TransliteratorIDParser.STVtoID(source, target, variant), source.ifEmpty { "Any" }, target, variant, entry, false)
        }
        return entry
    }
    private fun findInBundle(open: TransliteratorRegistrySpec, find: TransliteratorRegistrySpec, variant: String, direction: Int): Array<Any?>? {
        val resource = open.getBundle() ?: return null
        for (pass in 0..1) {
            val tag = (if (pass == 0) (if (direction == 0) "TransliterateTo" else "TransliterateFrom") else "Transliterate") +
                UCharacter.toUpperCase(ULocale.ENGLISH, find.get()!!)
            try {
                val rules = resource.getStringArray(tag)
                var i = 0
                if (variant.isNotEmpty()) {
                    while (i < rules.size && !rules[i].equals(variant, true)) i += 2
                }
                if (i < rules.size) return arrayOf(LocaleEntry(rules[i + 1], if (pass == 0) 0 else direction))
            } catch (_: MissingResourceException) { }
        }
        return null
    }

    // Each map operation locks separately, matching Collections.synchronizedMap rather
    // than serializing a whole registration/lookup. Enumerations remain fail-fast.
    private class Index<V> {
        private val values = LinkedHashMap<CaseInsensitiveString, V>()
        private val lock = createTextTrieMapLock(values)
        private var version = 0
        operator fun get(key: CaseInsensitiveString): V? = lock.withLock { values[key] }
        fun put(key: CaseInsensitiveString, value: V) = lock.withLock {
            if (!values.containsKey(key)) version++
            values[key] = value
        }
        fun remove(key: CaseInsensitiveString) = lock.withLock {
            if (values.containsKey(key)) { values.remove(key); version++ }
        }
        fun isEmpty(): Boolean = lock.withLock { values.isEmpty() }
        fun enumeration(): Enumeration<String> {
            val (keys, expected) = lock.withLock { values.keys.toList() to version }
            return idEnumeration(keys) { if (expected != version) throw ConcurrentModificationException() }
        }
    }
    private class Variants {
        private val values = mutableListOf<CaseInsensitiveString>()
        private var version = 0
        fun add(value: CaseInsensitiveString, first: Boolean) {
            if (!values.contains(value)) { values.add(if (first) 0 else values.size, value); version++ }
        }
        fun remove(value: CaseInsensitiveString) { if (values.remove(value)) version++ }
        fun isEmpty(): Boolean = values.isEmpty()
        fun enumeration(): Enumeration<String> {
            val expected = version
            return object : Enumeration<String> {
                private var index = 0
                override fun hasMoreElements(): Boolean = index != values.size
                override fun nextElement(): String {
                    if (expected != version) throw ConcurrentModificationException()
                    if (index >= values.size) throw NoSuchElementException()
                    return values[index++].getString()!!
                }
            }
        }
    }
    companion object {
        private fun missingEnumeration(): Enumeration<String> = object : Enumeration<String> {
            override fun hasMoreElements(): Boolean = false
            override fun nextElement(): String = throw NullPointerException()
        }
        private fun idEnumeration(keys: List<CaseInsensitiveString>, check: () -> Unit): Enumeration<String> = object : Enumeration<String> {
            private var index = 0
            override fun hasMoreElements(): Boolean = index < keys.size
            override fun nextElement(): String {
                check()
                if (index == keys.size) throw NoSuchElementException()
                return keys[index++].getString()!!
            }
        }
    }
}

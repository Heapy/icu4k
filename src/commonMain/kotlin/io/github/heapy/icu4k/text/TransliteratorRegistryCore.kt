// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.createTextTrieMapLock

/** Registry entry lifecycle. The owner supplies concrete public Transliterator construction. */
internal class TransliteratorRegistryCore<T : Any>(private val host: Host<T>) : TransliterationResourceBootstrap.Registry {
    interface Host<T : Any> {
        val parserFactory: TransliterationFactory
        fun ruleBased(id: String, data: TransliterationData, filter: UnicodeSet?): T
        fun compound(elements: List<T>, anonymousPasses: Int): T
        fun getInstance(id: String): T
        fun setID(instance: T, id: String)
        fun setFilter(instance: T, filter: UnicodeSet)
        /** Clone Any/RuleBased/Compound instances; return other registered instances unchanged. */
        fun cloneStored(instance: T): T
    }
    class ResourceEntry private constructor(private var resource: String?, val direction: Int, private val supplier: (() -> String?)?) {
        constructor(resource: String?, direction: Int) : this(resource, direction, null)
        constructor(supplier: () -> String?, direction: Int) : this(null, direction, supplier)
        private val lock = createTextTrieMapLock(this)
        fun getResource(): String? {
            val source = supplier ?: return resource
            return lock.withLock { resource ?: source().also { resource = it } }
        }
    }
    class AliasEntry(var alias: String?)
    class FactoryEntry<T>(val factory: (String) -> T?)
    /** The JVM owner catches only InstantiationException and IllegalAccessException. */
    class ClassEntry<T>(val construct: () -> T?)
    class InstanceEntry<T>(val instance: T)
    private class CompoundEntry(val id: String, val ids: List<String>, val data: List<TransliterationData>, val filter: UnicodeSet?)
    internal val store = TransliteratorRegistryStore()
    override fun putResource(id: String, rules: () -> String, direction: Int, visible: Boolean) = putResourceSupplier(id, rules, direction, visible)
    fun putResourceSupplier(id: String, rules: () -> String?, direction: Int, visible: Boolean) = store.put(id, ResourceEntry(rules, direction), visible)
    fun putResource(id: String, rules: String?, direction: Int, visible: Boolean) = store.put(id, ResourceEntry(rules, direction), visible)
    override fun putAlias(id: String, alias: String, visible: Boolean) = putNullableAlias(id,alias,visible)
    fun putNullableAlias(id: String, alias: String?, visible: Boolean) = store.put(id,AliasEntry(alias),visible)
    fun putFactory(id: String, factory: ((String) -> T?)?, visible: Boolean) = store.put(id, factory?.let {FactoryEntry(it)}, visible)
    fun putClass(id: String, construct: () -> T?, visible: Boolean) = store.put(id, ClassEntry(construct), visible)
    fun putInstance(id: String, instance: T, visible: Boolean) = store.put(id, InstanceEntry(instance), visible)
    fun remove(id: String) = store.remove(id)
    fun getAvailableIDs() = store.getAvailableIDs()
    fun getAvailableSources() = store.getAvailableSources()
    fun getAvailableTargets(source: String) = store.getAvailableTargets(source)
    fun getAvailableVariants(source: String, target: String) = store.getAvailableVariants(source, target)
    fun get(id: String, aliasReturn: StringBuilder): T? {
        val wrapper = store.find(id) ?: return null
        while (true) {
            when (val entry = wrapper[0]) {
                is TransliterationData -> return host.ruleBased(id, entry, null)
                is ClassEntry<*> -> { @Suppress("UNCHECKED_CAST") return entry.construct() as T? }
                is AliasEntry -> { aliasReturn.append(entry.alias); return null }
                is FactoryEntry<*> -> { @Suppress("UNCHECKED_CAST") return entry.factory(id) as T? }
                is CompoundEntry -> {
                    val elements = mutableListOf<T>()
                    var pass = 1
                    for (i in 0 until maxOf(entry.ids.size, entry.data.size)) {
                        if (i < entry.ids.size && entry.ids[i].isNotEmpty()) elements.add(host.getInstance(entry.ids[i]))
                        if (i < entry.data.size) elements.add(host.ruleBased("%Pass${pass++}", entry.data[i], null))
                    }
                    val result = host.compound(elements, pass - 1)
                    host.setID(result, entry.id)
                    entry.filter?.let { host.setFilter(result, it) }
                    return result
                }
                is InstanceEntry<*> -> { @Suppress("UNCHECKED_CAST") return host.cloneStored(entry.instance as T) }
                else -> {
                    val parser = TransliteratorParser(host.parserFactory)
                    try {
                        val resource = entry as ResourceEntry?
                        val rules = resource!!.getResource()
                        if (rules == null) parser.parseLines(emptyArray(), resource.direction) else parser.parse(rules, resource.direction)
                    } catch (_: ClassCastException) {
                        val locale = entry as TransliteratorRegistryStore.LocaleEntry
                        parser.parse(locale.rule, locale.direction)
                    }
                    wrapper[0] = when {
                        parser.idBlockVector.isEmpty() && parser.dataVector.isEmpty() -> AliasEntry("Any-Null")
                        parser.idBlockVector.isEmpty() && parser.dataVector.size == 1 -> parser.dataVector[0]
                        parser.idBlockVector.size == 1 && parser.dataVector.isEmpty() -> AliasEntry(
                            (parser.compoundFilter?.let { it.toPattern(false) + ";" } ?: "") + parser.idBlockVector[0])
                        else -> CompoundEntry(id, parser.idBlockVector, parser.dataVector, parser.compoundFilter)
                    }
                }
            }
        }
    }
}

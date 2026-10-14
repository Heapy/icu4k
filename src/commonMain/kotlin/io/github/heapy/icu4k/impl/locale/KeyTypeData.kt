// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.Output

/** Original legacy/BCP47 key/type mapping initialized from ICU keyTypeData.res. */
object KeyTypeData {
    enum class ValueType { single, multiple, incremental, any }
    private enum class SpecialType {
        CODEPOINTS, REORDER_CODE, RG_KEY_VALUE, SCRIPT_CODE, SUBDIVISION_CODE, PRIVATE_USE;
        fun isWellFormed(value: String): Boolean = when (this) {
            CODEPOINTS -> parts(value, 4, 6) { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            REORDER_CODE -> parts(value, 3, 8, AsciiUtil::isAlpha)
            RG_KEY_VALUE -> value.length >= 4 && value.takeLast(4).all { it == 'z' || it == 'Z' } && region(value.dropLast(4))
            SCRIPT_CODE -> parts(value, 4, 4, AsciiUtil::isAlpha)
            SUBDIVISION_CODE -> region(value)
            PRIVATE_USE -> parts(value, 3, 8, AsciiUtil::isAlphaNumeric)
        }
    }
    private data class Type(val legacyId: String, val bcpId: String)
    private data class KeyData(val legacyId: String, val bcpId: String, val types: Map<String, Type>, val special: Set<SpecialType>)
    private val keyMap = HashMap<String, KeyData>()
    private val bcpKeys = LinkedHashMap<String, Set<String>>()
    private val deprecatedKeys = HashSet<String>()
    private val valueTypes = HashMap<String, ValueType>()
    private val deprecatedTypes = HashMap<String, Set<String>>()
    init {
        val resources = Resources()
        val root = resources.root("keyTypeData")
        fun entries(node: Resource): List<Pair<String, Resource>> = resources.entries(node)
        fun string(node: Resource): String = resources.string(node)
        for ((info, resource) in entries(resources.child(root, "keyInfo"))) for ((key, value) in entries(resource)) when (info) {
            "deprecated" -> deprecatedKeys.add(key)
            "valueType" -> valueTypes[key] = ValueType.valueOf(string(value))
            else -> error("Unknown key info: $info")
        }
        for ((info, resource) in entries(resources.child(root, "typeInfo"))) {
            check(info == "deprecated")
            for ((key, value) in entries(resource)) deprecatedTypes[key] = entries(value).map { it.first }.toSet()
        }
        val types = resources.child(root, "typeMap")
        val aliases = resources.child(root, "typeAlias")
        val bcpAliases = resources.child(root, "bcpTypeAlias")
        fun reversedAliases(resource: Resource?, timezone: Boolean): Map<String, Set<String>> {
            val result = HashMap<String, MutableSet<String>>()
            if (resource != null) for ((rawFrom, value) in entries(resource)) {
                val from = if (timezone) rawFrom.replace(':', '/') else rawFrom
                result.getOrPut(string(value)) { HashSet() }.add(from)
            }
            return result
        }
        for ((legacyKey, rawBcp) in entries(resources.child(root, "keyMap"))) {
            val bcpValue = string(rawBcp)
            val bcpKey = bcpValue.ifEmpty { legacyKey }
            val timezone = legacyKey == "timezone"
            val legacyAliases = reversedAliases(resources.child(aliases, legacyKey), timezone)
            val unicodeAliases = reversedAliases(resources.child(bcpAliases, bcpKey), false)
            val typeMap = HashMap<String, Type>()
            val special = HashSet<SpecialType>()
            val listedTypes = LinkedHashSet<String>()
            for ((rawLegacy, value) in entries(resources.child(types, legacyKey))) {
                val rawBcpType = string(value)
                if (rawLegacy[0] > '9' && rawLegacy[0] < 'a' && rawBcpType.isEmpty()) {
                    special.add(SpecialType.valueOf(rawLegacy)); listedTypes.add(rawLegacy); continue
                }
                val legacyType = if (timezone) rawLegacy.replace(':', '/') else rawLegacy
                val bcpType = rawBcpType.ifEmpty { legacyType }
                listedTypes.add(bcpType)
                val type = Type(legacyType, bcpType)
                typeMap[AsciiUtil.toLowerString(legacyType)] = type
                if (rawBcpType.isNotEmpty()) typeMap[AsciiUtil.toLowerString(bcpType)] = type
                for (alias in legacyAliases[legacyType].orEmpty()) typeMap[AsciiUtil.toLowerString(alias)] = type
                for (alias in unicodeAliases[bcpType].orEmpty()) typeMap[AsciiUtil.toLowerString(alias)] = type
            }
            bcpKeys[bcpKey] = OrderedReadOnlySet(listedTypes)
            val data = KeyData(legacyKey, bcpKey, typeMap, special)
            keyMap[AsciiUtil.toLowerString(legacyKey)] = data
            if (bcpValue.isNotEmpty()) keyMap[AsciiUtil.toLowerString(bcpKey)] = data
        }
    }
    fun toBcpKey(key: String): String? = keyMap[AsciiUtil.toLowerString(key)]?.bcpId
    fun toLegacyKey(key: String): String? = keyMap[AsciiUtil.toLowerString(key)]?.legacyId
    fun toBcpType(key: String, type: String, isKnownKey: Output<Boolean>? = null, isSpecialType: Output<Boolean>? = null): String? =
        convert(key, type, isKnownKey, isSpecialType, true)
    fun toLegacyType(key: String, type: String, isKnownKey: Output<Boolean>? = null, isSpecialType: Output<Boolean>? = null): String? =
        convert(key, type, isKnownKey, isSpecialType, false)
    private fun convert(key: String, type: String, known: Output<Boolean>?, special: Output<Boolean>?, bcp: Boolean): String? {
        known?.value = false; special?.value = false
        val normalizedKey = AsciiUtil.toLowerString(key)
        val normalized = AsciiUtil.toLowerString(type)
        val data = keyMap[normalizedKey] ?: return null
        known?.value = true
        data.types[normalized]?.let { return if (bcp) it.bcpId else it.legacyId }
        for (handler in SpecialType.entries) if (handler in data.special && handler.isWellFormed(normalized)) {
            special?.value = true; return normalized
        }
        return null
    }
    fun getBcp47Keys(): Set<String> = OrderedReadOnlySet(bcpKeys.keys)
    fun getBcp47KeyTypes(key: String): Set<String>? = bcpKeys[key]
    fun isDeprecated(key: String): Boolean = key in deprecatedKeys
    fun isDeprecated(key: String, type: String): Boolean = type in deprecatedTypes[key].orEmpty()
    fun getValueType(key: String): ValueType = valueTypes[key] ?: ValueType.single
    private data class Resource(val reader: ICUResourceBundleReader, val value: Int)
    /** Direct ICUDATA alias resolution for the original key/type data bundles. */
    private class Resources {
        private val readers = HashMap<String, ICUResourceBundleReader>()
        fun root(name: String): Resource {
            val reader = readers.getOrPut(name) { ICUResourceBundleReader.getReader(ICUData.ICU_BASE_NAME, name)!! }
            return Resource(reader, reader.rootResource)
        }
        private fun rawChild(node: Resource, key: String): Resource = Resource(node.reader,
            node.reader.getTable(node.value)?.getResource(key) ?: ICUResourceBundleReader.RES_BOGUS)
        fun child(node: Resource, key: String): Resource = rawChild(resolve(node, HashSet()), key)
        private fun resolve(node: Resource, visited: MutableSet<String>): Resource {
            val alias = node.reader.getAlias(node.value) ?: return node
            if (!visited.add(alias)) throw IllegalArgumentException("Circular references in the resource bundles")
            check(alias.startsWith("/ICUDATA/")) { "Unsupported direct key/type resource alias: $alias" }
            val path = alias.removePrefix("/ICUDATA/").split('/')
            var target = root(path[0])
            for (part in path.drop(1)) target = rawChild(resolve(target, visited), part)
            return resolve(target, visited)
        }
        fun entries(node: Resource): List<Pair<String, Resource>> {
            val resolved = resolve(node, HashSet())
            val table = resolved.reader.getTable(resolved.value) ?: return emptyList()
            return List(table.size) { table.getKey(it)!! to Resource(resolved.reader, table.getContainerResource(it)) }
        }
        fun string(node: Resource): String = resolve(node, HashSet()).let { it.reader.getString(it.value)!! }
    }
    private fun region(value: String): Boolean = value.length == 2 && AsciiUtil.isAlphaString(value) || value.length == 3 && AsciiUtil.isNumericString(value)
    private fun parts(value: String, min: Int, max: Int, predicate: (Char) -> Boolean): Boolean =
        value.split('-').all { it.length in min..max && it.all(predicate) }
    private class OrderedReadOnlySet(private val values: Set<String>) : AbstractMutableSet<String>() {
        override val size: Int get() = values.size
        override fun contains(element: String): Boolean = values.contains(element)
        override fun iterator(): MutableIterator<String> {
            val source = values.iterator()
            return object : MutableIterator<String> {
                override fun hasNext(): Boolean = source.hasNext()
                override fun next(): String = source.next()
                override fun remove(): Nothing = throw UnsupportedOperationException()
            }
        }
        override fun add(element: String): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
        override fun remove(element: String): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
}

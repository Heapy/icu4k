// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2016, Google, Inc.; International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUNumberParsing
import io.github.heapy.icu4k.impl.number.javaCompatibleDoubleString
import kotlin.jvm.JvmStatic

/** Immutable locale priorities, ordered by descending weight and last insertion order. */
class LocalePriorityList private constructor(private val languagesAndWeights: Map<ULocale?,Double>) : Iterable<ULocale?> {
    private val locales: Set<ULocale?> = object : AbstractMutableSet<ULocale?>() {
        override val size: Int get() = languagesAndWeights.size
        override fun contains(element: ULocale?): Boolean = languagesAndWeights.containsKey(element)
        override fun iterator(): MutableIterator<ULocale?> = object : MutableIterator<ULocale?> {
            private val iterator = languagesAndWeights.keys.iterator()
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): ULocale? = iterator.next()
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
        override fun add(element: ULocale?): Nothing = throw UnsupportedOperationException()
        override fun remove(element: ULocale?): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<ULocale?>): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<ULocale?>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<ULocale?>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
    fun getWeight(locale: ULocale?): Double? = languagesAndWeights[locale]
    fun getULocales(): Set<ULocale?> = locales
    override fun iterator(): Iterator<ULocale?> = locales.iterator()
    override fun toString(): String = buildString {
        for ((locale,weight) in languagesAndWeights) {
            if (isNotEmpty()) append(", ")
            append(locale)
            if (weight != 1.0) { append(";q="); append(javaCompatibleDoubleString(weight)) }
        }
    }
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        return try { languagesAndWeights == (other as LocalePriorityList).languagesAndWeights } catch(e: RuntimeException) { false }
    }
    override fun hashCode(): Int {
        var hash = 0
        for ((locale,weight) in languagesAndWeights) {
            val bits = weight.toBits()
            hash += (locale?.hashCode() ?: 0) xor (bits xor (bits ushr 32)).toInt()
        }
        return hash
    }

    class Builder internal constructor() {
        private var languageToWeight: MutableMap<ULocale?,Double>? = LinkedHashMap()
        private var built: LocalePriorityList? = null
        private var hasWeights = false
        internal constructor(list: LocalePriorityList?) : this() {
            languageToWeight = null; built = list
            for (weight in list!!.languagesAndWeights.values) {
                if (assertionsEnabled && !(0.0 < weight && weight <= 1.0)) throw AssertionError()
                if (weight != 1.0) { hasWeights = true; break }
            }
        }
        fun build(): LocalePriorityList = build(false)
        fun build(preserveWeights: Boolean): LocalePriorityList {
            built?.let { return it }
            val input = languageToWeight!!
            val result: Map<ULocale?,Double>
            if (hasWeights) {
                val groups = LinkedHashMap<Double,MutableList<ULocale?>>()
                for ((locale,weight) in input) groups.getOrPut(weight) { ArrayList() }.add(locale)
                val weights = groups.keys.sortedWith { a,b -> b.compareTo(a) }
                if (weights.size <= 1) {
                    result = input
                    if (weights.isEmpty() || weights.first() == 1.0) hasWeights = false
                } else {
                    val sorted = LinkedHashMap<ULocale?,Double>()
                    for (weight in weights) for (locale in groups.getValue(weight)) {
                        sorted[locale] = if (preserveWeights) weight else 1.0
                    }
                    result = sorted
                }
            } else result = input
            languageToWeight = null
            return LocalePriorityList(result).also { built = it }
        }
        fun add(list: LocalePriorityList?): Builder {
            for ((locale,weight) in list!!.languagesAndWeights) add(locale,weight)
            return this
        }
        fun add(locale: ULocale?): Builder = add(locale,1.0)
        fun add(vararg locales: ULocale?): Builder {
            for (locale in locales) add(locale,1.0)
            return this
        }
        fun add(locale: ULocale?, weight: Double): Builder {
            if (languageToWeight == null) {
                languageToWeight = LinkedHashMap(built!!.languagesAndWeights)
                built = null
            }
            val map = languageToWeight!!
            map.remove(locale)
            val value = if (weight <= 0.0) return this else if (weight >= 1.0) 1.0 else {
                hasWeights = true; weight
            }
            map[locale] = value
            return this
        }
        fun add(acceptLanguageList: String?): Builder {
            for (item in languageSplitter.split(acceptLanguageList!!.trim { it <= ' ' })) {
                val match = weightSplitter.matchEntire(item)
                if (match != null) {
                    val language = ULocale(match.groupValues[1])
                    val weight = ICUNumberParsing.parseDouble(match.groupValues[2])
                    if (!(0.0 <= weight && weight <= 1.0)) throw IllegalArgumentException("Illegal weight, must be 0..1: "+javaCompatibleDoubleString(weight))
                    add(language,weight)
                } else if (item.isNotEmpty()) add(ULocale(item))
            }
            return this
        }
    }
    companion object {
        // Java Pattern uses ASCII whitespace unless UNICODE_CHARACTER_CLASS is requested.
        private const val WS = "[\\t\\n\\u000b\\u000c\\r ]"
        private const val NS = "[^\\t\\n\\u000b\\u000c\\r ]"
        private val languageSplitter = Regex("$WS*,$WS*")
        private val weightSplitter = Regex("$WS*($NS*)$WS*;$WS*q$WS*=$WS*($NS*)")
        private val assertionsEnabled = localePriorityListAssertionsEnabled()
        @JvmStatic fun add(vararg locales: ULocale?): Builder = Builder().add(*locales)
        @JvmStatic fun add(locale: ULocale?, weight: Double): Builder = Builder().add(locale,weight)
        @JvmStatic fun add(list: LocalePriorityList?): Builder = Builder(list)
        @JvmStatic fun add(acceptLanguageString: String?): Builder = Builder().add(acceptLanguageString)
    }
}
internal expect fun localePriorityListAssertionsEnabled(): Boolean

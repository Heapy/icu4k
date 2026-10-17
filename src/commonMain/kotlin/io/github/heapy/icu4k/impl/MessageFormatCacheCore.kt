// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.MessagePattern
import io.github.heapy.icu4k.text.MessagePattern.Part

/** Explicit/custom format cache. Stock scanning descends into submessages; public setters do not. */
internal class MessageFormatCacheCore<F>(private val pattern: () -> MessagePattern?, private val createFormat: (String,String) -> F?) {
    var cachedFormatters: MutableMap<Int,F?>? = null
        private set
    var customFormatArgStarts: MutableSet<Int>? = null
        private set
    fun clear() { cachedFormatters?.clear(); customFormatArgStarts = null }
    fun cacheExplicitFormats() {
        clear()
        val limit = pattern()!!.countParts() - 2
        var i = 1
        while (i < limit) {
            var part = pattern()!!.getPart(i)
            if (part.type == Part.Type.ARG_START && part.getArgType() == MessagePattern.ArgType.SIMPLE) {
                val start = i
                i += 2
                val type = pattern()!!.getSubstring(pattern()!!.getPart(i++))
                var style = ""
                part = pattern()!!.getPart(i)
                if (part.type == Part.Type.ARG_STYLE) { style = pattern()!!.getSubstring(part); i++ }
                set(start,createFormat(type,style),false)
            }
            i++
        }
    }
    private fun set(start: Int, value: F?, custom: Boolean) {
        if (cachedFormatters == null) cachedFormatters = HashMap()
        cachedFormatters!![start] = value
        if (custom) {
            if (customFormatArgStarts == null) customFormatArgStarts = HashSet()
            customFormatArgStarts!!.add(start)
        }
    }
    private inline fun each(block: (Int) -> Unit) {
        var i = 0
        while (true) {
            i = MessageFormatPatternCore.nextTopLevelArgStart(pattern()!!,i)
            if (i < 0) return
            block(i)
        }
    }
    private fun numericOnly() {
        if (pattern()!!.hasNamedArguments()) throw IllegalArgumentException("This method is not available in MessageFormat objects that use alphanumeric argument names.")
    }
    private fun name(index: Int): String {
        val message = pattern()!!
        val part = message.getPart(index)
        return if (part.type == Part.Type.ARG_NAME) message.getSubstring(part) else part.value.toString()
    }
    private fun matches(index: Int, name: String?, number: Int): Boolean {
        val message = pattern()!!
        val part = message.getPart(index)
        return if (part.type == Part.Type.ARG_NAME) message.partSubstringMatches(part,name) else part.value == number
    }
    fun setFormatsByArgumentIndex(formats: List<F?>?) {
        numericOnly()
        each { i -> val number = pattern()!!.getPart(i + 1).value; if (number < formats!!.size) set(i,formats[number],true) }
    }
    fun setFormatsByArgumentName(formats: Map<String,F?>?) {
        each { i -> val name = name(i + 1); if (formats!!.containsKey(name)) set(i,formats[name],true) }
    }
    fun setFormats(formats: List<F?>?) {
        var ordinal = 0
        var i = 0
        while (ordinal < formats!!.size) {
            i = MessageFormatPatternCore.nextTopLevelArgStart(pattern()!!,i)
            if (i < 0) break
            set(i,formats[ordinal++],true)
        }
    }
    fun setFormatByArgumentIndex(index: Int, format: F?) {
        numericOnly()
        each { i -> if (pattern()!!.getPart(i + 1).value == index) set(i,format,true) }
    }
    fun setFormatByArgumentName(name: String?, format: F?) {
        val number = MessagePattern.validateArgumentName(name)
        if (number < MessagePattern.ARG_NAME_NOT_NUMBER) return
        each { i -> if (matches(i + 1,name,number)) set(i,format,true) }
    }
    fun setFormat(index: Int, format: F?) {
        var ordinal = 0
        each { i -> if (ordinal++ == index) { set(i,format,true); return } }
        throw IndexOutOfBoundsException(index.toString())
    }
    fun formatsByArgumentIndex(): List<F?> {
        numericOnly()
        val formats = ArrayList<F?>()
        each { i ->
            val index = pattern()!!.getPart(i + 1).value
            while (index >= formats.size) formats.add(null)
            formats[index] = cachedFormatters?.get(i)
        }
        return formats
    }
    fun formats(): List<F?> {
        val formats = ArrayList<F?>()
        each { i -> formats.add(cachedFormatters?.get(i)) }
        return formats
    }
    fun argumentNames(): MutableSet<String> {
        val names = HashSet<String>()
        each { i -> names.add(name(i + 1)) }
        return names
    }
    fun formatByArgumentName(name: String?): F? {
        if (cachedFormatters == null) return null
        val number = MessagePattern.validateArgumentName(name)
        if (number < MessagePattern.ARG_NAME_NOT_NUMBER) return null
        each { i -> if (matches(i + 1,name,number)) return cachedFormatters!![i] }
        return null
    }
    fun copyStateFrom(other: MessageFormatCacheCore<F>) {
        cachedFormatters = other.cachedFormatters?.let { HashMap(it) }
        customFormatArgStarts = other.customFormatArgStarts?.let { HashSet(it) }
    }
}

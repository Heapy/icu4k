// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.UnicodeSet

/** Compressed text trie with simple Unicode case folding and live value iterators. */
open class TextTrieMap<V>(internal var ignoreCase: Boolean) {
    private val root = Node()
    private val lock = createTextTrieMapLock(this)
    open class Output { var matchLength = 0; var partialMatch = false }
    fun interface ResultHandler<V> { fun handlePrefixMatch(matchLength: Int, values: MutableIterator<V>): Boolean }
    open fun put(text: CharSequence?, value: V): TextTrieMap<V> {
        val iterator = CharIterator(text, 0, ignoreCase)
        val buffer = StringBuilder()
        while (iterator.hasNext()) buffer.append(iterator.next())
        root.add(buffer.toString().toCharArray(), 0, value)
        return this
    }
    open fun get(text: String?): MutableIterator<V>? = get(text, 0)
    open fun get(text: CharSequence?, start: Int): MutableIterator<V>? = get(text, start, null)
    open fun get(text: CharSequence?, start: Int, output: Output?): MutableIterator<V>? {
        val handler = LongestMatchHandler<V>()
        find(text, start, handler, output)
        if (output != null) output.matchLength = handler.length
        return handler.matches
    }
    open fun find(text: CharSequence?, handler: ResultHandler<V>?) = find(text, 0, handler, null)
    open fun find(text: CharSequence?, offset: Int, handler: ResultHandler<V>?) = find(text, offset, handler, null)
    private fun find(text: CharSequence?, offset: Int, handler: ResultHandler<V>?, output: Output?) {
        find(root, CharIterator(text, offset, ignoreCase), handler, output)
    }
    private fun find(node: Node, iterator: CharIterator, handler: ResultHandler<V>?, output: Output?) {
        lock.withLock {
            val values = node.values?.iterator()
            if (values != null && !handler!!.handlePrefixMatch(iterator.processedLength(), values)) return@withLock
            node.findMatch(iterator, output)?.let { find(it, iterator, handler, output) }
        }
    }
    open fun putLeadCodePoints(output: UnicodeSet?) { root.putLeadCodePoints(output) }
    open class CharIterator internal constructor(private val text: CharSequence?, offset: Int, private val ignoreCase: Boolean) : MutableIterator<Char?> {
        private var nextIndex = offset
        private val startIndex = offset
        private var remaining: Char? = null
        override fun hasNext(): Boolean = !(nextIndex == text!!.length && remaining == null)
        override fun next(): Char? {
            if (nextIndex == text!!.length && remaining == null) return null
            if (remaining != null) return remaining.also { remaining = null }
            if (!ignoreCase) {
                val value = text[nextIndex]
                nextIndex++
                return value
            }
            val first = text[nextIndex]
            var cp = first.code
            if (first in '\ud800'..'\udbff' && nextIndex + 1 < text.length) {
                val second = text[nextIndex + 1]
                if (second in '\udc00'..'\udfff') cp = ((first.code - 0xd800) shl 10) + second.code - 0xdc00 + 0x10000
            }
            cp = UCharacter.foldCase(cp, true)
            nextIndex += if (cp >= 0x10000) 2 else 1
            if (cp < 0x10000) return cp.toChar()
            remaining = ((cp - 0x10000 and 0x3ff) + 0xdc00).toChar()
            return ((cp - 0x10000 ushr 10) + 0xd800).toChar()
        }
        override fun remove(): Nothing = throw UnsupportedOperationException("remove() not supported")
        open fun nextIndex(): Int = nextIndex
        open fun processedLength(): Int {
            if (remaining != null) throw IllegalStateException("In the middle of surrogate pair")
            return nextIndex - startIndex
        }
    }
    private class LongestMatchHandler<V> : ResultHandler<V> {
        var matches: MutableIterator<V>? = null
        var length = 0
        override fun handlePrefixMatch(matchLength: Int, values: MutableIterator<V>): Boolean {
            if (matchLength > length) { length = matchLength; matches = values }
            return true
        }
    }
    private inner class Node(var text: CharArray? = null, var values: MutableList<V>? = null, var children: MutableList<Node>? = null) {
        fun add(input: CharArray, offset: Int, value: V) {
            if (input.size == offset) { if (values == null) values = ArrayList(); values!!.add(value); return }
            if (children == null) { children = arrayListOf(Node(subArray(input, offset), arrayListOf(value))); return }
            val iterator = children!!.listIterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (input[offset] < next.text!![0]) { iterator.previous(); break }
                if (input[offset] == next.text!![0]) {
                    var length = 0
                    val limit = minOf(next.text!!.size, input.size - offset)
                    while (length < limit && next.text!![length] == input[offset + length]) length++
                    if (length != next.text!!.size) next.split(length)
                    next.add(input, offset + length, value)
                    return
                }
            }
            iterator.add(Node(subArray(input, offset), arrayListOf(value)))
        }
        private fun split(offset: Int) {
            val child = Node(subArray(text!!, offset), values, children)
            text = subArray(text!!, 0, offset); values = null; children = arrayListOf(child)
        }
        fun findMatch(iterator: CharIterator, output: Output?): Node? {
            val children = children ?: return null
            if (!iterator.hasNext()) { if (output != null) output.partialMatch = true; return null }
            val ch = iterator.next()!!
            for (child in children) {
                if (ch < child.text!![0]) break
                if (ch == child.text!![0]) return if (child.matchFollowing(iterator, output)) child else null
            }
            return null
        }
        private fun matchFollowing(iterator: CharIterator, output: Output?): Boolean {
            var index = 1
            while (index < text!!.size) {
                if (!iterator.hasNext()) { if (output != null) output.partialMatch = true; return false }
                if (iterator.next() != text!![index++]) return false
            }
            return true
        }
        fun putLeadCodePoints(output: UnicodeSet?) {
            val children = children ?: return
            for (child in children) {
                val first = child.text!![0]
                if (first !in '\ud800'..'\udbff') output!!.add(first.code)
                else if (child.text!!.size >= 2) {
                    val second = child.text!![1]
                    val cp = if (second in '\udc00'..'\udfff') ((first.code - 0xd800) shl 10) + second.code - 0xdc00 + 0x10000 else first.code
                    output!!.add(cp)
                } else child.children?.let { grandchildren -> for (grandchild in grandchildren) output!!.add(((first.code - 0xd800) shl 10) + grandchild.text!![0].code - 0xdc00 + 0x10000) }
            }
        }
    }
    private fun subArray(array: CharArray, start: Int, limit: Int = array.size): CharArray = if (start == 0 && limit == array.size) array else array.copyOfRange(start, limit)
}
internal expect fun createTextTrieMapLock(owner: Any): ResourceCacheLock

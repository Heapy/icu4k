// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Common dynamic trie, hash-consing and backwards-serialization algorithm. */
abstract class StringTrieBuilder protected constructor() {
    enum class Option { FAST, SMALL }
    private enum class State { ADDING, BUILDING_FAST, BUILDING_SMALL, BUILT }
    private var state = State.ADDING
    protected val strings = StringBuilder()
    private var root: Node? = null
    private val nodes = HashMap<Node, Node>()
    private val lookupFinalValueNode = ValueNode()
    protected fun addImpl(s: CharSequence, value: Int) {
        check(state == State.ADDING) { "Cannot add (string, value) pairs after build()." }
        if (s.length > 0xffff) throw IndexOutOfBoundsException("The maximum string length is 0xffff.")
        root = root?.add(this, s, 0, value) ?: createSuffixNode(s, 0, value)
    }
    protected fun buildImpl(option: Option) {
        when (state) {
            State.ADDING -> {
                if (root == null) throw IndexOutOfBoundsException("No (string, value) pairs were added.")
                state = if (option == Option.FAST) State.BUILDING_FAST else State.BUILDING_SMALL
            }
            State.BUILT -> return
            else -> error("Builder failed and must be clear()ed.")
        }
        root = root!!.register(this)
        root!!.markRightEdgesFirst(-1)
        root!!.write(this)
        state = State.BUILT
    }
    protected fun clearImpl() { strings.setLength(0); nodes.clear(); root = null; state = State.ADDING }
    private fun registerNode(node: Node): Node {
        if (state == State.BUILDING_FAST) return node
        val old = nodes[node]
        if (old != null) return old
        nodes[node] = node
        return node
    }
    private fun registerFinalValue(value: Int): ValueNode {
        lookupFinalValueNode.setFinalValue(value)
        val old = nodes[lookupFinalValueNode]
        if (old != null) return old as ValueNode
        val node = ValueNode(value)
        nodes[node] = node
        return node
    }
    private abstract class Node {
        var offset = 0
        abstract override fun hashCode(): Int
        override fun equals(other: Any?): Boolean = this === other || other != null && this::class == other::class
        open fun add(builder: StringTrieBuilder, s: CharSequence, start: Int, value: Int): Node = this
        open fun register(builder: StringTrieBuilder): Node = this
        open fun markRightEdgesFirst(edge: Int): Int { if (offset == 0) offset = edge; return edge }
        abstract fun write(builder: StringTrieBuilder)
        fun writeUnlessInsideRightEdge(first: Int, last: Int, builder: StringTrieBuilder) {
            if (offset < 0 && (offset < last || first < offset)) write(builder)
        }
    }
    private open class ValueNode() : Node() {
        var hasValue = false
        var value = 0
        constructor(value: Int) : this() { hasValue = true; this.value = value }
        fun assignValue(value: Int) { hasValue = true; this.value = value }
        fun setFinalValue(value: Int) { hasValue = true; this.value = value }
        override fun hashCode(): Int = if (hasValue) 0x111111 * 37 + value else 0x111111
        override fun equals(other: Any?): Boolean = this === other || super.equals(other) && other is ValueNode &&
            hasValue == other.hasValue && (!hasValue || value == other.value)
        override fun add(builder: StringTrieBuilder, s: CharSequence, start: Int, value: Int): Node {
            require(start != s.length) { "Duplicate string." }
            return builder.createSuffixNode(s, start, value).also { it.assignValue(this.value) }
        }
        override fun write(builder: StringTrieBuilder) { offset = builder.writeValueAndFinal(value, true) }
    }
    private class IntermediateValueNode(value: Int, val next: Node) : ValueNode() {
        init { assignValue(value) }
        override fun hashCode(): Int = (0x222222 * 37 + value) * 37 + next.hashCode()
        override fun equals(other: Any?): Boolean = this === other || super.equals(other) && other is IntermediateValueNode && next === other.next
        override fun markRightEdgesFirst(edge: Int): Int {
            var number = edge
            if (offset == 0) { number = next.markRightEdgesFirst(number); offset = number }
            return number
        }
        override fun write(builder: StringTrieBuilder) { next.write(builder); offset = builder.writeValueAndFinal(value, false) }
    }
    private class LinearMatchNode(val text: CharSequence, var stringOffset: Int, var length: Int, var next: Node) : ValueNode() {
        private var hash = 0
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (!super.equals(other) || other !is LinearMatchNode || length != other.length || next !== other.next) return false
            for (i in 0 until length) if (text[stringOffset + i] != other.text[other.stringOffset + i]) return false
            return true
        }
        override fun add(builder: StringTrieBuilder, s: CharSequence, start: Int, value: Int): Node {
            var index = start
            if (index == s.length) { require(!hasValue) { "Duplicate string." }; assignValue(value); return this }
            val limit = stringOffset + length
            var i = stringOffset
            while (i < limit) {
                if (index == s.length) {
                    val prefixLength = i - stringOffset
                    val suffix = LinearMatchNode(text, i, length - prefixLength, next)
                    suffix.assignValue(value)
                    length = prefixLength; next = suffix
                    return this
                }
                val oldChar = text[i]
                val newChar = s[index]
                if (oldChar != newChar) {
                    val branch = DynamicBranchNode()
                    val result: Node
                    val suffix: Node
                    if (i == stringOffset) {
                        if (hasValue) { branch.assignValue(this.value); this.value = 0; hasValue = false }
                        ++stringOffset; --length
                        suffix = if (length > 0) this else next
                        result = branch
                    } else if (i == limit - 1) {
                        --length; suffix = next; next = branch; result = this
                    } else {
                        val prefixLength = i - stringOffset
                        ++i
                        suffix = LinearMatchNode(text, i, length - prefixLength - 1, next)
                        length = prefixLength; next = branch; result = this
                    }
                    branch.add(oldChar, suffix)
                    branch.add(newChar, builder.createSuffixNode(s, index + 1, value))
                    return result
                }
                ++i; ++index
            }
            next = next.add(builder, s, index, value)
            return this
        }
        override fun register(builder: StringTrieBuilder): Node {
            next = next.register(builder)
            val max = builder.getMaxLinearMatchLength()
            while (length > max) {
                val nextOffset = stringOffset + length - max
                length -= max
                val suffix = LinearMatchNode(text, nextOffset, max, next)
                suffix.setHashCode(); next = builder.registerNode(suffix)
            }
            val result: Node
            if (hasValue && !builder.matchNodesCanHaveValues()) {
                val intermediate = value; value = 0; hasValue = false
                setHashCode()
                result = IntermediateValueNode(intermediate, builder.registerNode(this))
            } else { setHashCode(); result = this }
            return builder.registerNode(result)
        }
        override fun markRightEdgesFirst(edge: Int): Int {
            var number = edge
            if (offset == 0) { number = next.markRightEdgesFirst(number); offset = number }
            return number
        }
        override fun write(builder: StringTrieBuilder) {
            next.write(builder); builder.write(stringOffset, length)
            offset = builder.writeValueAndType(hasValue, value, builder.getMinLinearMatch() + length - 1)
        }
        private fun setHashCode() {
            hash = (0x333333 * 37 + length) * 37 + next.hashCode()
            if (hasValue) hash = hash * 37 + value
            for (i in stringOffset until stringOffset + length) hash = hash * 37 + text[i].code
        }
    }
    private class DynamicBranchNode : ValueNode() {
        private val chars = StringBuilder()
        private val equal = ArrayList<Node>()
        fun add(c: Char, node: Node) { val i = find(c); chars.insert(i, c); equal.add(i, node) }
        override fun add(builder: StringTrieBuilder, s: CharSequence, start: Int, value: Int): Node {
            if (start == s.length) { require(!hasValue) { "Duplicate string." }; assignValue(value); return this }
            val c = s[start]
            val i = find(c)
            if (i < chars.length && c == chars[i]) equal[i] = equal[i].add(builder, s, start + 1, value)
            else { chars.insert(i, c); equal.add(i, builder.createSuffixNode(s, start + 1, value)) }
            return this
        }
        override fun register(builder: StringTrieBuilder): Node {
            val head = BranchHeadNode(chars.length, register(builder, 0, chars.length))
            var result: Node = head
            if (hasValue) {
                if (builder.matchNodesCanHaveValues()) head.assignValue(value)
                else result = IntermediateValueNode(value, builder.registerNode(head))
            }
            return builder.registerNode(result)
        }
        private fun register(builder: StringTrieBuilder, start: Int, limit: Int): Node {
            val length = limit - start
            if (length > builder.getMaxBranchLinearSubNodeLength()) {
                val middle = start + length / 2
                return builder.registerNode(SplitBranchNode(chars[middle], register(builder, start, middle), register(builder, middle, limit)))
            }
            val list = ListBranchNode(length)
            for (i in start until limit) {
                val node = equal[i]
                if (node::class == ValueNode::class) list.add(chars[i].code, (node as ValueNode).value)
                else list.add(chars[i].code, node.register(builder))
            }
            return builder.registerNode(list)
        }
        private fun find(c: Char): Int {
            var start = 0; var limit = chars.length
            while (start < limit) {
                val i = (start + limit) / 2
                if (c < chars[i]) limit = i else if (c == chars[i]) return i else start = i + 1
            }
            return start
        }
    }
    private abstract class BranchNode : Node() {
        var hash = 0
        var firstEdgeNumber = 0
        override fun hashCode(): Int = hash
    }
    private class ListBranchNode(capacity: Int) : BranchNode() {
        private val equal = arrayOfNulls<Node>(capacity)
        private val values = IntArray(capacity)
        private val units = CharArray(capacity)
        private var length = 0
        init { hash = 0x444444 * 37 + capacity }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (!super.equals(other) || other !is ListBranchNode) return false
            for (i in 0 until length) if (units[i] != other.units[i] || values[i] != other.values[i] || equal[i] !== other.equal[i]) return false
            return true
        }
        override fun hashCode(): Int = super.hashCode()
        override fun markRightEdgesFirst(edge: Int): Int {
            var number = edge
            if (offset == 0) {
                firstEdgeNumber = number
                var step = 0
                var i = length
                do { val node = equal[--i]; if (node != null) number = node.markRightEdgesFirst(number - step); step = 1 } while (i > 0)
                offset = number
            }
            return number
        }
        override fun write(builder: StringTrieBuilder) {
            var unitNumber = length - 1
            val right = equal[unitNumber]
            val rightNumber = right?.offset ?: firstEdgeNumber
            do { --unitNumber; equal[unitNumber]?.writeUnlessInsideRightEdge(firstEdgeNumber, rightNumber, builder) } while (unitNumber > 0)
            unitNumber = length - 1
            if (right == null) builder.writeValueAndFinal(values[unitNumber], true) else right.write(builder)
            offset = builder.write(units[unitNumber].code)
            while (--unitNumber >= 0) {
                val child = equal[unitNumber]
                builder.writeValueAndFinal(if (child == null) values[unitNumber] else offset - child.offset, child == null)
                offset = builder.write(units[unitNumber].code)
            }
        }
        fun add(c: Int, value: Int) {
            units[length] = c.toChar(); equal[length] = null; values[length] = value; ++length
            hash = (hash * 37 + c) * 37 + value
        }
        fun add(c: Int, node: Node) {
            units[length] = c.toChar(); equal[length] = node; values[length] = 0; ++length
            hash = (hash * 37 + c) * 37 + node.hashCode()
        }
    }
    private class SplitBranchNode(val unit: Char, val lessThan: Node, val greaterOrEqual: Node) : BranchNode() {
        init { hash = ((0x555555 * 37 + unit.code) * 37 + lessThan.hashCode()) * 37 + greaterOrEqual.hashCode() }
        override fun equals(other: Any?): Boolean = this === other || super.equals(other) && other is SplitBranchNode &&
            unit == other.unit && lessThan === other.lessThan && greaterOrEqual === other.greaterOrEqual
        override fun hashCode(): Int = super.hashCode()
        override fun markRightEdgesFirst(edge: Int): Int {
            var number = edge
            if (offset == 0) {
                firstEdgeNumber = number; number = greaterOrEqual.markRightEdgesFirst(number)
                number = lessThan.markRightEdgesFirst(number - 1); offset = number
            }
            return number
        }
        override fun write(builder: StringTrieBuilder) {
            lessThan.writeUnlessInsideRightEdge(firstEdgeNumber, greaterOrEqual.offset, builder)
            greaterOrEqual.write(builder); builder.writeDeltaTo(lessThan.offset); offset = builder.write(unit.code)
        }
    }
    private class BranchHeadNode(val length: Int, val next: Node) : ValueNode() {
        override fun hashCode(): Int = (0x666666 * 37 + length) * 37 + next.hashCode()
        override fun equals(other: Any?): Boolean = this === other || super.equals(other) && other is BranchHeadNode && length == other.length && next === other.next
        override fun markRightEdgesFirst(edge: Int): Int {
            var number = edge
            if (offset == 0) { number = next.markRightEdgesFirst(number); offset = number }
            return number
        }
        override fun write(builder: StringTrieBuilder) {
            next.write(builder)
            if (length <= builder.getMinLinearMatch()) offset = builder.writeValueAndType(hasValue, value, length - 1)
            else { builder.write(length - 1); offset = builder.writeValueAndType(hasValue, value, 0) }
        }
    }
    private fun createSuffixNode(s: CharSequence, start: Int, value: Int): ValueNode {
        var node = registerFinalValue(value)
        if (start < s.length) {
            val offset = strings.length
            strings.append(s, start, s.length)
            node = LinearMatchNode(strings, offset, s.length - start, node)
        }
        return node
    }
    protected abstract fun matchNodesCanHaveValues(): Boolean
    protected abstract fun getMaxBranchLinearSubNodeLength(): Int
    protected abstract fun getMinLinearMatch(): Int
    protected abstract fun getMaxLinearMatchLength(): Int
    protected abstract fun write(unit: Int): Int
    protected abstract fun write(offset: Int, length: Int): Int
    protected abstract fun writeValueAndFinal(value: Int, isFinal: Boolean): Int
    protected abstract fun writeValueAndType(hasValue: Boolean, value: Int, node: Int): Int
    protected abstract fun writeDeltaTo(jumpTarget: Int): Int
}

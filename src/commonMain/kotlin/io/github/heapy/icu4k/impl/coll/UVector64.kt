// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** Growable primitive array; indexed access deliberately uses capacity rather than logical size. */
internal class UVector64 {
    private var buffer = LongArray(32)
    private var length = 0
    fun isEmpty(): Boolean = length == 0
    fun size(): Int = length
    fun elementAti(i: Int): Long = buffer[checkedIndex(i)]
    fun getBuffer(): LongArray = buffer
    fun addElement(e: Long) {
        ensureAppendCapacity()
        val index = length++
        buffer[checkedIndex(index)] = e
    }
    fun setElementAt(elem: Long, index: Int) { buffer[checkedIndex(index)] = elem }
    fun insertElementAt(elem: Long, index: Int) {
        ensureAppendCapacity()
        val count = length - index
        val destination = index + 1
        if (index < 0 || destination < 0 || count < 0 || count > buffer.size ||
            index > buffer.size - count || destination > buffer.size - count) throw IndexOutOfBoundsException()
        buffer.copyInto(buffer, destination, index, length)
        buffer[checkedIndex(index)] = elem
        ++length
    }
    fun removeAllElements() { length = 0 }
    private fun checkedIndex(index: Int): Int {
        if (index < 0 || index >= buffer.size) throw IndexOutOfBoundsException()
        return index
    }
    private fun ensureAppendCapacity() {
        if (length >= buffer.size) {
            val newCapacity = if (buffer.size <= 0xffff) 4 * buffer.size else 2 * buffer.size
            val next = LongArray(newCapacity)
            buffer.copyInto(next, 0, 0, length)
            buffer = next
        }
    }
}

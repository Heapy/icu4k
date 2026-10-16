// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date

/** TimeZone's base implementations, retaining dispatch to the owning subclass. */
internal class TimeZoneBaseCore<Z : Any>(private val self: Z, private val owner: Owner<Z>) {
    interface Owner<Z : Any> {
        fun getRawOffset(zone: Z): Int
        fun getOffset(zone: Z, era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int): Int
        fun getOffset(zone: Z, date: Long, local: Boolean, offsets: IntArray)
        fun useDaylightTime(zone: Z): Boolean
        fun inDaylightTime(zone: Z, date: Date): Boolean
        fun isFrozen(zone: Z): Boolean
        fun cloneAsThawed(zone: Z): Z?
        /** Runtime class equality, without calling the owner's equals implementation. */
        fun hasSameClass(other: Any): Boolean
        /** Read the other object's private base ID, bypassing virtual getID. */
        fun storedID(other: Any): String?
    }

    private var id: String? = null

    constructor(self: Z, owner: Owner<Z>, id: String?) : this(self, owner) { this.id = id!! }

    fun getID(): String? = id
    fun setID(value: String?) {
        if (value == null) throw NullPointerException()
        if (owner.isFrozen(self)) throw UnsupportedOperationException("Attempt to modify a frozen TimeZone instance.")
        id = value
    }

    fun getOffset(date: Long): Int {
        val offsets = IntArray(2)
        owner.getOffset(self, date, false, offsets)
        return offsets[0] + offsets[1]
    }

    fun getOffset(date: Long, local: Boolean, offsets: IntArray?) {
        // Java evaluates the RHS before the array store's null/bounds check.
        val raw = owner.getRawOffset(self)
        write(offsets, 0, raw)
        var time = date
        if (!local) time += read(offsets, 0)
        val fields = IntArray(6)
        var pass = 0
        while (true) {
            Grego.timeToFields(time, fields)
            // The source passes AD even for negative proleptic Gregorian years.
            val total = owner.getOffset(self, 1, fields[0], fields[1], fields[2], fields[3], fields[5])
            val dst = total - read(offsets, 0)
            write(offsets, 1, dst)
            if (pass != 0 || !local || read(offsets, 1) == 0) break
            time -= read(offsets, 1)
            pass++
        }
    }

    fun getDSTSavings(): Int = if (owner.useDaylightTime(self)) 3600000 else 0
    fun observesDaylightTime(): Boolean = owner.useDaylightTime(self) || owner.inDaylightTime(self, Date())

    fun hasSameRules(other: Z?): Boolean = other != null &&
        owner.getRawOffset(self) == owner.getRawOffset(other) &&
        owner.useDaylightTime(self) == owner.useDaylightTime(other)

    fun clone(): Z? = if (owner.isFrozen(self)) self else owner.cloneAsThawed(self)
    fun isFrozen(): Boolean = false
    fun freeze(): Nothing = throw UnsupportedOperationException("Needs to be implemented by the subclass.")

    fun equalsZone(other: Any?): Boolean {
        if (self === other) return true
        if (other == null || !owner.hasSameClass(other)) return false
        val ownID = id
        val otherID = owner.storedID(other)
        return ownID!! == otherID
    }
    fun hashCodeID(): Int = id!!.hashCode()

    private fun read(array: IntArray?, index: Int): Int {
        if (array == null) throw NullPointerException()
        if (index !in array.indices) throw IndexOutOfBoundsException()
        return array[index]
    }
    private fun write(array: IntArray?, index: Int, value: Int) {
        if (array == null) throw NullPointerException()
        if (index !in array.indices) throw IndexOutOfBoundsException()
        array[index] = value
    }
}

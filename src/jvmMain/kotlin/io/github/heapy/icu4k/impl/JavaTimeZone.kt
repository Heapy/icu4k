// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.timezone.JavaTimeZoneOffsetCore
import io.github.heapy.icu4k.util.Date
import io.github.heapy.icu4k.util.TimeZone
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** JVM interoperability with JDK timezone rules. ICU resource zones remain common. */
open class JavaTimeZone : TimeZone {
    private var javatz: java.util.TimeZone?
    private var javacal: java.util.Calendar?
    @Volatile private var frozen = false
    constructor() : this(java.util.TimeZone.getDefault(), null)
    constructor(zone: java.util.TimeZone?, id: String?) : super() {
        val zoneID = id ?: zone!!.id
        javatz = zone
        setID(zoneID)
        javacal = java.util.GregorianCalendar(javatz)
    }
    override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int =
        javatz!!.getOffset(era, year, month, day, dayOfWeek, milliseconds)
    override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) = JavaTimeZoneOffsetCore(Binding()).getOffset(date, local, offsets)
    override fun getRawOffset(): Int = javatz!!.rawOffset
    override fun setRawOffset(offsetMillis: Int) {
        if (isFrozen()) throw UnsupportedOperationException("Attempt to modify a frozen JavaTimeZone instance.")
        javatz!!.rawOffset = offsetMillis
    }
    override fun useDaylightTime(): Boolean = javatz!!.useDaylightTime()
    override fun inDaylightTime(date: Date?): Boolean = javatz!!.inDaylightTime(date)
    override fun getDSTSavings(): Int = javatz!!.dstSavings
    override fun observesDaylightTime(): Boolean {
        val method = observesMethod
        if (method != null) try {
            return method.invoke(javatz) as Boolean
        } catch (_: IllegalAccessException) {
        } catch (_: IllegalArgumentException) {
        } catch (_: InvocationTargetException) { }
        return super.observesDaylightTime()
    }
    open fun unwrap(): java.util.TimeZone? = javatz
    public override fun clone(): JavaTimeZone = if (isFrozen()) this else cloneAsThawed()
    override fun hashCode(): Int = super.hashCode() + javatz!!.hashCode()
    override fun isFrozen(): Boolean = frozen
    override fun freeze(): TimeZone { frozen = true; return this }
    override fun cloneAsThawed(): JavaTimeZone {
        val copy = super.cloneAsThawed() as JavaTimeZone
        copy.javatz = javatz!!.clone() as java.util.TimeZone
        // ICU4J binds the clone's calendar to the original wrapped zone; retain that behavior.
        copy.javacal = java.util.GregorianCalendar(javatz)
        copy.frozen = false
        return copy
    }
    private inner class Binding : JavaTimeZoneOffsetCore.Owner {
        override fun <T> withCalendarLock(block: () -> T): T = synchronized(javacal!!, block)
        override fun clear() = javacal!!.clear()
        override fun setDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) = javacal!!.set(year, month, day, hour, minute, second)
        override fun set(field: Int, value: Int) = javacal!!.set(field, value)
        override fun get(field: Int): Int = javacal!!.get(field)
        override fun getTimeInMillis(): Long = javacal!!.timeInMillis
        override fun setTimeInMillis(value: Long) { javacal!!.timeInMillis = value }
    }
    companion object {
        private val available = java.util.TreeSet<String?>().apply { addAll(java.util.TimeZone.getAvailableIDs().asList()) }
        private val observesMethod: Method? = try { java.util.TimeZone::class.java.getMethod("observesDaylightTime") }
            catch (_: NoSuchMethodException) { null } catch (_: SecurityException) { null }
        fun createTimeZone(id: String?): JavaTimeZone? {
            var zone: java.util.TimeZone? = if (available.contains(id)) java.util.TimeZone.getTimeZone(id) else null
            if (zone == null) {
                val system = BooleanArray(1)
                val canonical = TimeZone.getCanonicalID(id, system)
                if (system[0] && available.contains(canonical)) zone = java.util.TimeZone.getTimeZone(canonical)
            }
            return zone?.let { JavaTimeZone(it, id) }
        }
    }
}

package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.GregorianCalendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale
import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.test.*

class GregorianCalendarCoreOracleTest {
    internal open class Probe(zoneID: String = "Etc/GMT") : GregorianCalendar(TimeZone.getTimeZone(zoneID), ULocale.US) {
        var common = false
        private var ready = false
        var trace = ArrayList<String>()
        var port = GregorianCalendarCore<GregorianCalendar>(Binding(this))
        var fieldPort = CalendarFieldsCore(rawFields(), rawStamps(), FieldBinding(this))
        var datePort = CalendarDateCore(rawFields(),rawStamps(),DateBinding(this))
        var timePort = CalendarTimeCore<TimeZone>(rawStamps(),TimeBinding(this),Calendar::class.java.desiredAssertionStatus())
        var statePort = CalendarStateCore(rawFields(),rawStamps(),StateBinding(this),StateOwner(this),Calendar::class.java.desiredAssertionStatus())
        var limitsPort = CalendarLimitsCore(rawFields(), LimitsBinding(this))
        var addPort = CalendarAddCore(AddBinding(this))
        var rollPort = CalendarRollCore(RollBinding(this))
        init { ready = true }
        private fun record(value: String) { if (ready) trace.add(value) }
        override fun isLeapYear(year: Int): Boolean {
            record("leap:$year")
            return if (common) port.isLeapYear(year) else super.isLeapYear(year)
        }
        override fun getLimit(field: Int, limitType: Int): Int = if (common) limitsPort.getLimit(field,limitType) else super.getLimit(field,limitType)
        override fun prepareGetActual(field: Int, minimum: Boolean) { if (common) limitsPort.prepareGetActual(field,minimum) else super.prepareGetActual(field,minimum) }
        override fun pinField(field: Int) { if (common) limitsPort.pinField(field) else super.pinField(field) }
        override fun handleGetLimit(field: Int, limitType: Int) = if (common) port.handleGetLimit(field, limitType) else super.handleGetLimit(field, limitType)
        override fun handleGetMonthLength(year: Int, month: Int) = if (common) port.handleGetMonthLength(year, month) else super.handleGetMonthLength(year, month)
        override fun handleGetYearLength(year: Int): Int {
            record("length:$year")
            return if (common) port.handleGetYearLength(year) else super.handleGetYearLength(year)
        }
        override fun handleComputeFields(day: Int) { if (common) port.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetExtendedYear(): Int = if (common) port.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleComputeJulianDay(bestField: Int): Int = if (common) port.handleComputeJulianDay(bestField) else super.handleComputeJulianDay(bestField)
        override fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int {
            record("start:$year:$month:$useMonth")
            return if (common) port.handleComputeMonthStart(year, month, useMonth) else super.handleComputeMonthStart(year, month, useMonth)
        }
        override fun computeFields() { if (common) fieldPort.computeFields() else super.computeFields() }
        override fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int): Int =
            if (common) fieldPort.weekNumber(desiredDay, dayOfPeriod, dayOfWeek) else super.weekNumber(desiredDay, dayOfPeriod, dayOfWeek)
        override fun newerField(defaultField: Int, alternateField: Int): Int =
            if (common) fieldPort.newerField(defaultField,alternateField) else super.newerField(defaultField,alternateField)
        override fun resolveFields(table: Array<Array<IntArray>>?): Int =
            if (common) fieldPort.resolveFields(table) else super.resolveFields(table)
        override fun computeMillisInDay(): Int = if (common) fieldPort.computeMillisInDay() else super.computeMillisInDay()
        override fun computeMillisInDayLong(): Long = if (common) fieldPort.computeMillisInDayLong() else super.computeMillisInDayLong()
        override fun computeJulianDay(): Int = if (common) datePort.computeJulianDay() else super.computeJulianDay()
        override fun internalGetMonth(): Int = if (common) datePort.internalGetMonth() else super.internalGetMonth()
        override fun internalGetMonth(defaultValue: Int): Int = if (common) datePort.internalGetMonth(defaultValue) else super.internalGetMonth(defaultValue)
        override fun validateFields() { if (common) datePort.validateFields() else super.validateFields() }
        override fun validateField(field: Int) { if (common) datePort.validateField(field) else super.validateField(field) }
        override fun fieldName(field: Int): String = if (common) datePort.fieldName(field) else super.fieldName(field)
        override fun computeGregorianMonthStart(year: Int, month: Int): Int =
            if (common) CalendarDateCore.computeGregorianMonthStart(year,month) else super.computeGregorianMonthStart(year,month)
        override fun getDefaultMonthInYear(year: Int): Int = if (common) datePort.getDefaultMonthInYear(year) else super.getDefaultMonthInYear(year)
        override fun getDefaultDayInMonth(year: Int, month: Int): Int = if (common) datePort.getDefaultDayInMonth(year,month) else super.getDefaultDayInMonth(year,month)
        override fun computeTime() { if (common) timePort.computeTime() else super.computeTime() }
        override fun computeZoneOffset(millis: Long, millisInDay: Int): Int =
            if (common) timePort.computeZoneOffset(millis,millisInDay) else super.computeZoneOffset(millis,millisInDay)
        override fun computeZoneOffset(millis: Long, millisInDay: Long): Int =
            if (common) timePort.computeZoneOffset(millis,millisInDay) else super.computeZoneOffset(millis,millisInDay)
        override fun complete() { if (common) statePort.complete() else super.complete() }
        override fun getTimeInMillis(): Long = if (common) statePort.getTimeInMillis() else super.getTimeInMillis()
        override fun setTimeInMillis(value: Long) { if (common) statePort.setTimeInMillis(value) else super.setTimeInMillis(value) }
        override fun getTimeZone(): TimeZone? = if (common) statePort.getTimeZone() else super.getTimeZone()
        override fun setTimeZone(value: TimeZone?) { if (common) statePort.setTimeZone(value) else super.setTimeZone(value) }
        override fun isLenient(): Boolean = if (common) statePort.isLenient() else super.isLenient()
        override fun setLenient(value: Boolean) { if (common) statePort.setLenient(value) else super.setLenient(value) }
        override fun getRepeatedWallTimeOption(): Int = if (common) statePort.getRepeatedWallTimeOption() else super.getRepeatedWallTimeOption()
        override fun setRepeatedWallTimeOption(value: Int) { if (common) statePort.setRepeatedWallTimeOption(value) else super.setRepeatedWallTimeOption(value) }
        override fun getSkippedWallTimeOption(): Int = if (common) statePort.getSkippedWallTimeOption() else super.getSkippedWallTimeOption()
        override fun setSkippedWallTimeOption(value: Int) { if (common) statePort.setSkippedWallTimeOption(value) else super.setSkippedWallTimeOption(value) }
        override fun getFirstDayOfWeek(): Int = if (common) statePort.getFirstDayOfWeek() else super.getFirstDayOfWeek()
        override fun setFirstDayOfWeek(value: Int) { if (common) statePort.setFirstDayOfWeek(value) else super.setFirstDayOfWeek(value) }
        override fun getMinimalDaysInFirstWeek(): Int = if (common) statePort.getMinimalDaysInFirstWeek() else super.getMinimalDaysInFirstWeek()
        override fun setMinimalDaysInFirstWeek(value: Int) { if (common) statePort.setMinimalDaysInFirstWeek(value) else super.setMinimalDaysInFirstWeek(value) }
        override fun getRelatedYearDifference(): Int = if (common) statePort.getRelatedYearDifference() else super.getRelatedYearDifference()
        override fun getRelatedYear(): Int = if (common) statePort.getRelatedYear() else super.getRelatedYear()
        override fun setRelatedYear(year: Int) { if (common) statePort.setRelatedYear(year) else super.setRelatedYear(year) }
        override fun inTemporalLeapYear(): Boolean = if (common) statePort.inTemporalLeapYear() else super.inTemporalLeapYear()
        override fun getTemporalMonthCode(): String = if (common) statePort.getTemporalMonthCode() else super.getTemporalMonthCode()
        override fun setTemporalMonthCode(value: String?) { if (common) statePort.setTemporalMonthCode(value) else super.setTemporalMonthCode(value) }
        override fun add(field: Int, amount: Int) { if (common) addPort.add(field,amount) else super.add(field,amount) }
        override fun roll(field: Int, amount: Int) { if (common) port.roll(field, amount) else super.roll(field, amount) }
        override fun getActualMinimum(field: Int) = if (common) port.getActualMinimum(field) else super.getActualMinimum(field)
        override fun getActualMaximum(field: Int) = if (common) port.getActualMaximum(field) else super.getActualMaximum(field)
        public override fun clone(): Probe {
            record("clone")
            val copy = super.clone() as Probe
            copy.port = port.copyFor(Binding(copy))
            copy.fieldPort = fieldPort.copyFor(copy.rawFields(), copy.rawStamps(), FieldBinding(copy))
            copy.datePort = CalendarDateCore(copy.rawFields(),copy.rawStamps(),DateBinding(copy))
            copy.timePort = CalendarTimeCore(copy.rawStamps(),TimeBinding(copy),Calendar::class.java.desiredAssertionStatus())
            copy.statePort = CalendarStateCore(copy.rawFields(),copy.rawStamps(),StateBinding(copy),StateOwner(copy),Calendar::class.java.desiredAssertionStatus())
            copy.limitsPort = CalendarLimitsCore(copy.rawFields(), LimitsBinding(copy))
            copy.addPort = CalendarAddCore(AddBinding(copy))
            copy.rollPort = CalendarRollCore(RollBinding(copy))
            return copy
        }
        fun change(date: Date?) { super.setGregorianChange(date); port.setGregorianChange(date) }
        fun baseRoll(field: Int, amount: Int) { if (common) rollPort.roll(field,amount) else baseRollHandle.invokeWithArguments(this,field,amount) }
        fun pin(field: Int) = pinField(field)
        fun backwardEra() = isEra0CountingBackward()
        fun prepareLimits(field: Int, minimum: Boolean) = prepareGetActual(field,minimum)
        fun limit(field: Int, type: Int) = handleGetLimit(field, type)
        fun monthLength(year: Int, month: Int) = handleGetMonthLength(year, month)
        fun yearLength(year: Int) = handleGetYearLength(year)
        fun monthStart(year: Int, month: Int, useMonth: Boolean) = handleComputeMonthStart(year, month, useMonth)
        fun fields(day: Int): List<Int> { if (common) fieldPort.computeGregorianFields(day) else computeGregorianFields(day); handleComputeFields(day); return listOf(ERA,YEAR,MONTH,DAY_OF_MONTH,DAY_OF_YEAR,EXTENDED_YEAR,ORDINAL_MONTH).map { internalGet(it) } }
        fun extendedYear() = handleGetExtendedYear()
        fun julianDay(field: Int) = handleComputeJulianDay(field)
        fun read(field: Int) = if (common) datePort.internalGet(field) else internalGet(field)
        fun read(field: Int, default: Int) = if (common) datePort.internalGet(field,default) else internalGet(field, default)
        fun write(field: Int, value: Int) { if (common) datePort.internalSet(field,value) else internalSet(field, value) }
        fun month() = internalGetMonth()
        fun newer(a: Int, b: Int) = newerField(a, b)
        fun gregorianMonth() = if (common) fieldPort.gregorianMonth else getGregorianMonth()
        fun gregorianDayOfMonth() = if (common) fieldPort.gregorianDayOfMonth else getGregorianDayOfMonth()
        fun gregorianDayOfYear() = if (common) fieldPort.gregorianDayOfYear else getGregorianDayOfYear()
        fun gregorianYear() = if (common) fieldPort.gregorianYear else getGregorianYear()
        fun week(day: Int, dow: Int) = weekNumber(day, dow)
        fun finish() = complete()
        fun flags() = if (common) listOf(port.isGregorian, port.invertGregorian) else listOf(isGregorian, invertGregorian)
        fun rawFields() = fieldsField.get(this) as IntArray
        fun rawStamps() = stampsField.get(this) as ByteArray
        fun rawMask() = maskField.getInt(this)
        fun rawTime() = internalGetTimeInMillis()
        fun assignTime(value: Long) = timeField.setLong(this,value)
        fun storedZone() = zoneField.get(this) as TimeZone?
        fun lenientFlag() = lenientField.getBoolean(this)
        fun skippedFlag() = skippedField.getInt(this)
        fun repeatedFlag() = repeatedField.getInt(this)
        fun computeRawTime() = computeTime()
        fun zoneOffset32(millis: Long,mid: Int) = computeZoneOffset(millis,mid)
        fun zoneOffset64(millis: Long,mid: Long) = computeZoneOffset(millis,mid)
        fun virtualMillis32() = computeMillisInDay()
        fun virtualMillis64() = computeMillisInDayLong()
        fun immediatePrevious(base: Long): Long? = if (common) timePort.getImmediatePreviousZoneTransition(base) else immediateTransitionMethod.invoke(this,base) as Long?
        fun searchPrevious(zone: TimeZone?,base: Long,duration: Long): Long? = if (common) timePort.getPreviousZoneTransitionTime(zone,base,duration) else previousTransitionMethod.invoke(null,zone,base,duration) as Long?
        fun computeAll() = computeFields()
        fun handleFields(day: Int) = handleComputeFields(day)
        fun yearSize(year: Int) = handleGetYearLength(year)
        fun week3(desired: Int, period: Int, dow: Int) = weekNumber(desired, period, dow)
        fun millis32() = if (common) fieldPort.computeMillisInDay() else computeMillisInDay()
        fun millis64() = if (common) fieldPort.computeMillisInDayLong() else computeMillisInDayLong()
        fun resolve(table: Array<Array<IntArray>>?) = if (common) fieldPort.resolveFields(table) else resolveFields(table)
        fun newest(first: Int, last: Int, best: Int) = if (common) fieldPort.newestStamp(first,last,best) else newestStamp(first,last,best)
        fun stampAt(field: Int) = if (common) fieldPort.getStamp(field) else getStamp(field)
        fun chooseNewer(a: Int, b: Int) = if (common) fieldPort.newerField(a,b) else newerField(a,b)
        fun weekFields() { if (common) fieldPort.computeWeekFields() else weekFieldsMethod.invoke(this) }
        fun sourceDateTable() = getFieldResolutionTable()
        fun monthDefault(default: Int) = internalGetMonth(default)
        fun defaultMonth(year: Int) = getDefaultMonthInYear(year)
        fun defaultDay(year: Int, month: Int) = getDefaultDayInMonth(year,month)
        fun allJulian() = computeJulianDay()
        fun baseJulianDay(field: Int): Int = if (common) datePort.handleComputeJulianDay(field) else baseJulian.invokeWithArguments(this,field) as Int
        fun validateAll() = validateFields()
        fun validateOne(field: Int) = validateField(field)
        fun validateRange(field: Int, min: Int, max: Int) { if (common) datePort.validateField(field,min,max) else validateField(field,min,max) }
        fun nameOf(field: Int) = fieldName(field)
        fun gregorianStart(year: Int, month: Int) = computeGregorianMonthStart(year,month)
        fun floorInt(n: Int,d: Int) = if (common) CalendarDateCore.floorDivide(n,d) else floorDivide(n,d)
        fun floorLong(n: Long,d: Long) = if (common) CalendarDateCore.floorDivide(n,d) else floorDivide(n,d)
        fun floorIntRemainder(n: Int,d: Int,r: IntArray?) = if (common) CalendarDateCore.floorDivide(n,d,r) else floorDivide(n,d,r)
        fun floorLongRemainder(n: Long,d: Int,r: IntArray?) = if (common) CalendarDateCore.floorDivide(n,d,r) else floorDivide(n,d,r)
        fun millisDay(value: Long) = if (common) CalendarDateCore.millisToJulianDay(value) else millisToJulianDay(value)
        fun dayMillis(value: Int) = if (common) CalendarDateCore.julianDayToMillis(value) else julianDayToMillis(value)
        fun gregorianLength(year: Int, month: Int) = if (common) CalendarDateCore.gregorianMonthLength(year,month) else gregorianMonthLength(year,month)
        fun previousGregorianLength(year: Int, month: Int) = if (common) CalendarDateCore.gregorianPreviousMonthLength(year,month) else gregorianPreviousMonthLength(year,month)
        fun baseMonthLength(year: Int, month: Int): Int = if (common) datePort.handleGetMonthLength(year,month) else baseMonthLengthHandle.invokeWithArguments(this,year,month) as Int
        fun baseYearLength(year: Int): Int = if (common) datePort.handleGetYearLength(year) else baseYearLengthHandle.invokeWithArguments(this,year) as Int
        fun getField(field: Int) = if (common) statePort.get(field) else get(field)
        fun setField(field: Int,value: Int) { if (common) statePort.set(field,value) else set(field,value) }
        fun setDateFields(year: Int,month: Int,day: Int) { if (common) statePort.set(year,month,day) else set(year,month,day) }
        fun setDateTimeFields(year: Int,month: Int,day: Int,hour: Int,minute: Int,second: Int) { if (common) statePort.set(year,month,day,hour,minute,second) else set(year,month,day,hour,minute,second) }
        fun clearFields() { if (common) statePort.clear() else clear() }
        fun clearField(field: Int) { if (common) statePort.clear(field) else clear(field) }
        fun fieldIsSet(field: Int) = if (common) statePort.isSet(field) else isSet(field)
        fun dateValue(): Date = if (common) statePort.getTime() else time
        fun assignDate(value: Date?) { if (common) statePort.setTime(value) else setTime(value) }
        fun relatedDifference() = getRelatedYearDifference()
        fun lifecycleSnapshot(): List<Any?> = stateFields.map { (name,field) -> if (name == "zone") (field.get(this) as TimeZone?)?.id else field.get(this) }
        fun forceLifecycle(name: String,value: Any?) = stateFields.getValue(name).set(this,value)
    }
    private class StateOwner(private val p: Probe) : CalendarStateCore.Owner {
        override fun computeFields() = p.computeAll()
        override fun computeTime() = p.computeRawTime()
        override fun complete() = p.finish()
        override fun isLenient() = p.isLenient
        override fun getTimeInMillis() = p.timeInMillis
        override fun setTimeInMillis(millis: Long) { p.timeInMillis = millis }
        override fun getRelatedYearDifference() = p.relatedDifference()
        override fun getActualMaximum(field: Int) = p.getActualMaximum(field)
    }
    private class StateBinding(private val p: Probe) : CalendarStateCore.State<TimeZone> {
        private fun f(name: String) = stateFields.getValue(name)
        override var time: Long get() = p.rawTime()
            set(value) { p.assignTime(value) }
        override var isTimeSet: Boolean get() = f("isTimeSet").getBoolean(p)
            set(value) = f("isTimeSet").setBoolean(p,value)
        override var areFieldsSet: Boolean get() = f("areFieldsSet").getBoolean(p)
            set(value) = f("areFieldsSet").setBoolean(p,value)
        override var areAllFieldsSet: Boolean get() = f("areAllFieldsSet").getBoolean(p)
            set(value) = f("areAllFieldsSet").setBoolean(p,value)
        override var areFieldsVirtuallySet: Boolean get() = f("areFieldsVirtuallySet").getBoolean(p)
            set(value) = f("areFieldsVirtuallySet").setBoolean(p,value)
        override var nextStamp: Byte get() = f("nextStamp").getByte(p)
            set(value) = f("nextStamp").setByte(p,value)
        override var zone: TimeZone? get() = p.storedZone()
            set(value) = zoneField.set(p,value)
        override var lenient: Boolean get() = p.lenientFlag()
            set(value) = lenientField.setBoolean(p,value)
        override var repeatedWallTime: Int get() = p.repeatedFlag()
            set(value) = repeatedField.setInt(p,value)
        override var skippedWallTime: Int get() = p.skippedFlag()
            set(value) = skippedField.setInt(p,value)
        override var firstDayOfWeek: Int get() = f("firstDayOfWeek").getInt(p)
            set(value) = f("firstDayOfWeek").setInt(p,value)
        override var minimalDaysInFirstWeek: Int get() = f("minimalDaysInFirstWeek").getInt(p)
            set(value) = f("minimalDaysInFirstWeek").setInt(p,value)
    }
    private class TimeBinding(private val p: Probe) : CalendarTimeCore.Owner<TimeZone> {
        override fun isLenient() = p.isLenient
        override fun getLenientFlag() = p.lenientFlag()
        override fun validateFields() = p.validateAll()
        override fun computeJulianDay() = p.allJulian()
        override fun newestStamp(first: Int,last: Int,best: Int) = p.newest(first,last,best)
        override fun internalGet(field: Int) = p.read(field)
        override fun computeMillisInDay() = p.virtualMillis32()
        override fun computeMillisInDayLong() = p.virtualMillis64()
        override fun computeZoneOffset(millis: Long,millisInDay: Long) = p.zoneOffset64(millis,millisInDay)
        override fun setTime(time: Long) = p.assignTime(time)
        override fun getZone() = p.storedZone()
        override fun getSkippedWallTime() = p.skippedFlag()
        override fun getRepeatedWallTime() = p.repeatedFlag()
        override fun isBasicTimeZone(zone: TimeZone?) = zone is com.ibm.icu.util.BasicTimeZone
        override fun getOffsetFromLocal(zone: TimeZone?,wall: Long,nonExisting: Int,duplicated: Int,offsets: IntArray) {
            val former = com.ibm.icu.util.BasicTimeZone.LocalOption.FORMER
            val latter = com.ibm.icu.util.BasicTimeZone.LocalOption.LATTER
            (zone as com.ibm.icu.util.BasicTimeZone).getOffsetFromLocal(wall,if (nonExisting == 4) former else latter,if (duplicated == 4) former else latter,offsets)
        }
        override fun getOffset(zone: TimeZone?,date: Long,local: Boolean,offsets: IntArray) = zone!!.getOffset(date,local,offsets)
        override fun getTotalOffset(zone: TimeZone?,date: Long) = zone!!.getOffset(date)
        override fun getPreviousTransition(zone: TimeZone?,base: Long,inclusive: Boolean): CalendarTimeCore.Transition? {
            val transition = (zone as com.ibm.icu.util.BasicTimeZone).getPreviousTransition(base,inclusive) ?: return null
            return CalendarTimeCore.Transition { transition.time }
        }
    }
    private class DateBinding(private val p: Probe) : CalendarDateCore.Owner {
        override fun newestStamp(first: Int,last: Int,best: Int) = p.newest(first,last,best)
        override fun resolveFields(table: Array<Array<IntArray>>?) = p.resolve(table)
        override fun getFieldResolutionTable() = p.sourceDateTable()
        override fun newerField(defaultField: Int,alternateField: Int) = p.newer(defaultField,alternateField)
        override fun internalGet(field: Int) = p.read(field)
        override fun internalGet(field: Int,defaultValue: Int) = p.read(field,defaultValue)
        override fun internalGetMonth() = p.month()
        override fun internalGetMonth(defaultValue: Int) = p.monthDefault(defaultValue)
        override fun internalSet(field: Int,value: Int) = p.write(field,value)
        override fun handleGetExtendedYear() = p.extendedYear()
        override fun handleComputeJulianDay(bestField: Int) = p.julianDay(bestField)
        override fun handleComputeMonthStart(year: Int,month: Int,useMonth: Boolean) = p.monthStart(year,month,useMonth)
        override fun getDefaultMonthInYear(year: Int) = p.defaultMonth(year)
        override fun getDefaultDayInMonth(year: Int,month: Int) = p.defaultDay(year,month)
        override fun isSet(field: Int) = p.fieldIsSet(field)
        override fun getFirstDayOfWeek() = p.firstDayOfWeek
        override fun getMinimalDaysInFirstWeek() = p.minimalDaysInFirstWeek
        override fun handleGetMonthLength(year: Int,month: Int) = p.monthLength(year,month)
        override fun handleGetYearLength(year: Int) = p.yearLength(year)
        override fun validateField(field: Int) = p.validateOne(field)
        override fun getMinimum(field: Int) = p.getMinimum(field)
        override fun getMaximum(field: Int) = p.getMaximum(field)
        override fun fieldName(field: Int) = p.nameOf(field)
        override fun getInternalSetMask() = p.rawMask()
        override fun reportDefaultMonthLength(year: Int,month: Int) { System.out.printf("handleGetMonthLength(ey=%d, m=%d)",year,month) }
    }
    private class RollBinding(private val p: Probe) : CalendarRollCore.Owner {
        override fun complete() = p.finish()
        override fun get(field: Int) = p.getField(field)
        override fun internalGet(field: Int) = p.read(field)
        override fun internalGetMonth() = p.month()
        override fun set(field: Int, value: Int) = p.setField(field,value)
        override fun clear(field: Int) = p.clearField(field)
        override fun getActualMinimum(field: Int) = p.getActualMinimum(field)
        override fun getActualMaximum(field: Int) = p.getActualMaximum(field)
        override fun getMaximum(field: Int) = p.getMaximum(field)
        override fun pinField(field: Int) = p.pin(field)
        override fun isEra0CountingBackward() = p.backwardEra()
        override fun getFirstDayOfWeek() = p.firstDayOfWeek
        override fun getMinimalDaysInFirstWeek() = p.minimalDaysInFirstWeek
        override fun getTimeInMillis() = p.timeInMillis
        override fun setTimeInMillis(value: Long) { p.timeInMillis = value }
        override fun storedTimeInMillis() = p.rawTime()
        override fun assignStoredTime(value: Long) = p.assignTime(value)
        override fun fieldName(field: Int) = p.nameOf(field)
    }
    private class AddBinding(private val p: Probe) : CalendarAddCore.Owner {
        override fun get(field: Int) = p.getField(field)
        override fun set(field: Int, value: Int) = p.setField(field,value)
        override fun pinField(field: Int) = p.pin(field)
        override fun isEra0CountingBackward() = p.backwardEra()
        override fun isLenient() = p.isLenient
        override fun setLenient(value: Boolean) { p.isLenient = value }
        override fun complete() = p.finish()
        override fun fieldName(field: Int) = p.nameOf(field)
        override fun getTimeInMillis() = p.timeInMillis
        override fun setTimeInMillis(value: Long) { p.timeInMillis = value }
        override fun storedTimeInMillis() = p.rawTime()
        override fun storedSkippedWallTime() = p.skippedFlag()
        override fun getImmediatePreviousZoneTransition(time: Long) = p.immediatePrevious(time)
    }
    private class LimitsBinding(private val p: Probe) : CalendarLimitsCore.Owner {
        override fun handleGetLimit(field: Int, type: Int) = p.limit(field,type)
        override fun getLimit(field: Int, type: Int) = when (type) {
            0 -> p.getMinimum(field)
            1 -> p.getGreatestMinimum(field)
            2 -> p.getLeastMaximum(field)
            else -> p.getMaximum(field)
        }
        override fun getMinimalDaysInFirstWeek() = p.minimalDaysInFirstWeek
        override fun storedFirstDayOfWeek() = StateBinding(p).firstDayOfWeek
        override fun get(field: Int) = p.getField(field)
        override fun set(field: Int, value: Int) = p.setField(field,value)
        override fun getActualMaximum(field: Int) = p.getActualMaximum(field)
        override fun getActualMinimum(field: Int) = p.getActualMinimum(field)
        override fun handleGetMonthLength(year: Int, month: Int) = p.monthLength(year,month)
        override fun handleGetYearLength(year: Int) = p.yearLength(year)
        override fun inTemporalLeapYear() = p.inTemporalLeapYear()
        override fun cloneCalendar(): CalendarLimitsCore.Owner = LimitsBinding(p.clone())
        override fun complete() = p.finish()
        override fun setLenient(value: Boolean) { p.isLenient = value }
        override fun prepareGetActual(field: Int, minimum: Boolean) { p.prepareLimits(field,minimum) }
        override fun add(field: Int, amount: Int) = p.add(field,amount)
    }
    private class FieldBinding(private val p: Probe) : CalendarFieldsCore.Owner<TimeZone> {
        override fun getTimeZone(): TimeZone? = p.timeZone
        override fun getOffset(zone: TimeZone?, date: Long, local: Boolean, offsets: IntArray) = zone!!.getOffset(date,local,offsets)
        override fun getTime() = p.rawTime()
        override fun getInternalSetMask() = p.rawMask()
        override fun handleComputeFields(julianDay: Int) = p.handleFields(julianDay)
        override fun getFirstDayOfWeek() = p.firstDayOfWeek
        override fun getMinimalDaysInFirstWeek() = p.minimalDaysInFirstWeek
        override fun handleGetYearLength(year: Int) = p.yearSize(year)
        override fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int) = p.week3(desiredDay,dayOfPeriod,dayOfWeek)
        override fun internalGet(field: Int) = p.read(field)
    }
    private class Binding(private val p: Probe) : GregorianCalendarCore.Owner<GregorianCalendar> {
        override fun cutoverExtendedYear(date: Date): Int = GregorianCalendar(p.timeZone).apply { time = date }.get(Calendar.EXTENDED_YEAR)
        override fun isLeapYear(year: Int) = p.isLeapYear(year)
        override fun getGregorianMonth() = p.gregorianMonth()
        override fun getGregorianDayOfMonth() = p.gregorianDayOfMonth()
        override fun getGregorianDayOfYear() = p.gregorianDayOfYear()
        override fun getGregorianYear() = p.gregorianYear()
        override fun internalSet(field: Int, value: Int) = p.write(field, value)
        override fun internalGet(field: Int) = p.read(field)
        override fun internalGet(field: Int, defaultValue: Int) = p.read(field, defaultValue)
        override fun internalGetMonth() = p.month()
        override fun newerField(defaultField: Int, alternateField: Int) = p.newer(defaultField, alternateField)
        override fun baseComputeJulianDay(bestField: Int): Int = p.baseJulianDay(bestField)
        override fun get(field: Int) = p.getField(field)
        override fun set(field: Int, value: Int) = p.setField(field, value)
        override fun getYearLength(year: Int) = p.yearLength(year)
        override fun getFirstDayOfWeek() = p.firstDayOfWeek
        override fun getMinimalDaysInFirstWeek() = p.minimalDaysInFirstWeek
        override fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int) = p.week(dayOfPeriod, dayOfWeek)
        override fun baseRoll(field: Int, amount: Int) = p.baseRoll(field,amount)
        override fun getMinimum(field: Int) = p.getMinimum(field)
        override fun baseGetActualMaximum(field: Int): Int = if (p.common) p.limitsPort.getActualMaximum(field) else baseActualMaximum.invokeWithArguments(p, field) as Int
        override fun cloneCalendar(): GregorianCalendar = p.clone()
        override fun setLenient(calendar: GregorianCalendar, value: Boolean) { calendar.isLenient = value }
        override fun get(calendar: GregorianCalendar, field: Int) = (calendar as Probe).getField(field)
        override fun set(calendar: GregorianCalendar, field: Int, value: Int) = (calendar as Probe).setField(field, value)
        override fun getTime(calendar: GregorianCalendar): Date = calendar.time
        override fun setTime(calendar: GregorianCalendar, date: Date) { calendar.time = date }
        override fun zoneUsesDaylightTime() = p.timeZone!!.useDaylightTime()
        override fun complete() = p.finish()
    }

    private fun compare(p: Probe, setup: () -> Unit = {}, action: () -> Any?) {
        p.common = false; setup(); p.trace.clear()
        val expected = outcome(action); val trace = p.trace.toList()
        p.common = true; setup(); p.trace.clear()
        assertEquals(expected, outcome(action))
        assertEquals(trace, p.trace)
    }

    @Test fun limitsMonthArithmeticLeapYearsAndFieldConversion() {
        val p = Probe()
        for (field in -1..24) for (limit in -1..4) compare(p) { p.limit(field, limit) }
        val years = listOf(Int.MIN_VALUE, -5838269, -400, -100, -4, -1, 0, 1, 4, 100, 400, 1500, 1582, 1600, 1700, 1900, 1970, 2000, 2100, 5838270, Int.MAX_VALUE)
        val months = listOf(Int.MIN_VALUE, -25, -13, -12, -1, 0, 1, 2, 11, 12, 13, 24, Int.MAX_VALUE)
        for (cutover in listOf(null, Long.MIN_VALUE, Grego.MIN_MILLIS, Grego.MIN_MILLIS + 1, -12219292800000L, -1L, 0L, 1582934400000L, Grego.MAX_MILLIS - 1, Grego.MAX_MILLIS, Long.MAX_VALUE)) {
            p.common = false
            if (cutover != null) p.change(Date(cutover))
            for (year in years) {
                compare(p) { p.isLeapYear(year) }
                compare(p) { p.yearLength(year) }
                for (month in months) {
                    compare(p) { p.monthLength(year, month) }
                    for (useMonth in listOf(false, true)) compare(p) { listOf(p.monthStart(year, month, useMonth), p.flags()) }
                }
            }
            for (day in listOf(Int.MIN_VALUE, -2130706432, -1, 0, 1721423, 1721424, 1721425, 1721426, 2299159, 2299160, 2299161, 2299162, 2440588, 2451604, 2130706432, Int.MAX_VALUE)) {
                compare(p, { p.clearFields() }) { p.fields(day) }
            }
        }
    }

    @Test fun fieldPrecedenceAndCutoverRetryThroughRealCalendarBase() {
        val p = Probe()
        for (cutover in listOf(null, Long.MIN_VALUE, -12219292800000L, 0L, Long.MAX_VALUE)) {
            p.common = false; if (cutover != null) p.change(Date(cutover))
            for (year in listOf(-400, 0, 1, 1500, 1582, 1583, 1900, 2000)) for (month in listOf(-1, 0, 1, 9, 11, 12)) for (day in listOf(-1, 1, 4, 5, 14, 15, 28, 32)) {
                compare(p, { p.clearFields(); p.setField(Calendar.EXTENDED_YEAR, year); p.setField(Calendar.MONTH, month); p.setField(Calendar.DAY_OF_MONTH, day) }) {
                    listOf(p.julianDay(Calendar.DAY_OF_MONTH), p.flags())
                }
            }
        }
        val orders = listOf(emptyList(), listOf(19 to 7), listOf(1 to 7), listOf(0 to 0), listOf(1 to Int.MIN_VALUE, 0 to 0), listOf(19 to 9, 1 to 4), listOf(1 to 4, 19 to 9))
        for (fields in orders) compare(p, { p.clearFields(); for ((field,value) in fields) p.setField(field,value) }) { p.extendedYear() }
    }

    @Test fun weekRollAndActualLimitsThroughRealCalendarState() {
        for (zone in listOf("Etc/GMT", "America/New_York", "Pacific/Apia")) {
            val p = Probe(zone)
            for (date in listOf(-12219292800000L, -2208988800000L, 1451520000000L, 1483228800000L, 1609372800000L, 1710054000000L)) {
                for (first in listOf(1, 2, 7)) for (minimal in listOf(1, 4, 7)) for (amount in listOf(Int.MIN_VALUE, -104, -53, -1, 0, 1, 53, 104, Int.MAX_VALUE)) {
                    compare(p, { p.timeInMillis = date; p.firstDayOfWeek = first; p.minimalDaysInFirstWeek = minimal }) {
                        p.roll(Calendar.WEEK_OF_YEAR, amount)
                        listOf(p.timeInMillis, p.get(Calendar.YEAR), p.get(Calendar.WEEK_OF_YEAR))
                    }
                }
                for (field in listOf(Calendar.ERA, Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.DAY_OF_YEAR, Calendar.WEEK_OF_YEAR)) {
                    compare(p, { p.timeInMillis = date }) { p.getActualMinimum(field) }
                    compare(p, { p.timeInMillis = date }) { p.getActualMaximum(field) }
                }
                compare(p, { p.timeInMillis = date }) { p.roll(Calendar.MONTH, 1); p.timeInMillis }
            }
        }
    }

    companion object {
        private val fieldsField = Calendar::class.java.getDeclaredField("fields").apply { isAccessible = true }
        private val stampsField = Calendar::class.java.getDeclaredField("stamp").apply { isAccessible = true }
        private val maskField = Calendar::class.java.getDeclaredField("internalSetMask").apply { isAccessible = true }
        private val weekFieldsMethod = Calendar::class.java.getDeclaredMethod("computeWeekFields").apply { isAccessible = true }
        private val timeField = Calendar::class.java.getDeclaredField("time").apply { isAccessible = true }
        private val zoneField = Calendar::class.java.getDeclaredField("zone").apply { isAccessible = true }
        private val lenientField = Calendar::class.java.getDeclaredField("lenient").apply { isAccessible = true }
        private val skippedField = Calendar::class.java.getDeclaredField("skippedWallTime").apply { isAccessible = true }
        private val repeatedField = Calendar::class.java.getDeclaredField("repeatedWallTime").apply { isAccessible = true }
        private val stateFields = listOf("time","isTimeSet","areFieldsSet","areAllFieldsSet","areFieldsVirtuallySet","nextStamp","zone","lenient","repeatedWallTime","skippedWallTime","firstDayOfWeek","minimalDaysInFirstWeek")
            .associateWith { Calendar::class.java.getDeclaredField(it).apply { isAccessible = true } }
        private val immediateTransitionMethod = Calendar::class.java.getDeclaredMethod("getImmediatePreviousZoneTransition",Long::class.javaPrimitiveType).apply { isAccessible = true }
        private val previousTransitionMethod = Calendar::class.java.getDeclaredMethod("getPreviousZoneTransitionTime",TimeZone::class.java,Long::class.javaPrimitiveType,Long::class.javaPrimitiveType).apply { isAccessible = true }
        private val lookup = MethodHandles.privateLookupIn(GregorianCalendar::class.java, MethodHandles.lookup())
        private val intType = Int::class.javaPrimitiveType!!
        private val baseJulian = lookup.findSpecial(Calendar::class.java, "handleComputeJulianDay", MethodType.methodType(intType, intType), GregorianCalendar::class.java)
        private val baseRollHandle = lookup.findSpecial(Calendar::class.java, "roll", MethodType.methodType(Void.TYPE, intType, intType), GregorianCalendar::class.java)
        private val baseActualMaximum = lookup.findSpecial(Calendar::class.java, "getActualMaximum", MethodType.methodType(intType, intType), GregorianCalendar::class.java)
        private val baseMonthLengthHandle = lookup.findSpecial(Calendar::class.java, "handleGetMonthLength", MethodType.methodType(intType, intType, intType), GregorianCalendar::class.java)
        private val baseYearLengthHandle = lookup.findSpecial(Calendar::class.java, "handleGetYearLength", MethodType.methodType(intType, intType), GregorianCalendar::class.java)
        private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
            when (e) {
                is VirtualMachineError -> throw e
                is IndexOutOfBoundsException -> "bounds"
                is NullPointerException -> "null"
                else -> e.javaClass.simpleName + ":" + e.message
            }
        }
    }
}

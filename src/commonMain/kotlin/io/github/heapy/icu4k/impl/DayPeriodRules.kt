// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Locale-specific day periods loaded from ICU's dayPeriods resource. */
class DayPeriodRules private constructor() {
    enum class DayPeriod {
        MIDNIGHT, NOON, MORNING1, AFTERNOON1, EVENING1, NIGHT1,
        MORNING2, AFTERNOON2, EVENING2, NIGHT2, AM, PM;

        companion object {
            @kotlin.jvm.JvmField var VALUES: Array<DayPeriod> = entries.toTypedArray()
            internal fun fromStringOrNull(str: CharSequence): DayPeriod? = when (str.toString()) {
                "midnight" -> MIDNIGHT; "noon" -> NOON
                "morning1" -> MORNING1; "afternoon1" -> AFTERNOON1
                "evening1" -> EVENING1; "night1" -> NIGHT1
                "morning2" -> MORNING2; "afternoon2" -> AFTERNOON2
                "evening2" -> EVENING2; "night2" -> NIGHT2
                "am" -> AM; "pm" -> PM
                else -> null
            }
        }
    }

    private enum class CutoffType {
        BEFORE, AFTER, FROM, AT;
        companion object {
            fun fromStringOrNull(str: CharSequence): CutoffType? = when (str.toString()) {
                "from" -> FROM; "before" -> BEFORE; "after" -> AFTER; "at" -> AT
                else -> null
            }
        }
    }

    private class DayPeriodRulesData {
        val localesToRuleSetNumMap = HashMap<String, Int>()
        lateinit var rules: Array<DayPeriodRules?>
        var maxRuleSetNum = -1
    }

    private class DayPeriodRulesDataSink(private val data: DayPeriodRulesData) : UResource.Sink() {
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val dayPeriodData = value.getTable()
            var i = 0
            while (dayPeriodData.getKeyAndValue(i++, key, value)) {
                if (key.contentEquals("locales")) {
                    val locales = value.getTable()
                    var j = 0
                    while (locales.getKeyAndValue(j++, key, value)) {
                        val setNum = parseSetNum(value.getString())
                        data.localesToRuleSetNumMap[key.toString()] = setNum
                    }
                } else if (key.contentEquals("rules")) processRules(value.getTable(), key, value)
            }
        }

        private fun processRules(rules: UResource.Table, key: UResource.Key, value: UResource.Value) {
            var i = 0
            while (rules.getKeyAndValue(i++, key, value)) {
                ruleSetNum = parseSetNum(key.toString())
                data.rules[ruleSetNum] = DayPeriodRules()
                val ruleSet = value.getTable()
                var j = 0
                while (ruleSet.getKeyAndValue(j++, key, value)) {
                    period = DayPeriod.fromStringOrNull(key) ?: throw ICUException("Unknown day period in data.")
                    val periodDefinition = value.getTable()
                    var k = 0
                    while (periodDefinition.getKeyAndValue(k++, key, value)) {
                        if (value.getType() == UResourceBundle.STRING) {
                            addCutoff(CutoffType.fromStringOrNull(key), value.getString())
                        } else {
                            cutoffType = CutoffType.fromStringOrNull(key)
                            val cutoffArray = value.getArray()
                            for (l in 0 until cutoffArray.getSize()) {
                                cutoffArray.getValue(l, value)
                                addCutoff(cutoffType, value.getString())
                            }
                        }
                    }
                    setDayPeriodForHoursFromCutoffs()
                    cutoffs.fill(0)
                }
                for (period in data.rules[ruleSetNum]!!.dayPeriodForHour) {
                    if (period == null) throw ICUException("Rules in data don't cover all 24 hours (they should).")
                }
            }
        }

        private val cutoffs = IntArray(25)
        private var ruleSetNum = 0
        private var period: DayPeriod? = null
        private var cutoffType: CutoffType? = null

        private fun addCutoff(type: CutoffType?, hourStr: String) {
            if (type == null) throw ICUException("Cutoff type not recognized.")
            val hour = parseHour(hourStr)
            cutoffs[hour] = cutoffs[hour] or (1 shl type.ordinal)
        }

        private fun setDayPeriodForHoursFromCutoffs() {
            val rule = data.rules[ruleSetNum]!!
            for (startHour in 0..24) {
                if ((cutoffs[startHour] and (1 shl CutoffType.AT.ordinal)) > 0) {
                    if (startHour == 0 && period == DayPeriod.MIDNIGHT) rule.hasMidnight = true
                    else if (startHour == 12 && period == DayPeriod.NOON) rule.hasNoon = true
                    else throw ICUException("AT cutoff must only be set for 0:00 or 12:00.")
                }
                if ((cutoffs[startHour] and (1 shl CutoffType.FROM.ordinal)) > 0 ||
                    (cutoffs[startHour] and (1 shl CutoffType.AFTER.ordinal)) > 0) {
                    var hour = startHour + 1
                    while (true) {
                        if (hour == startHour) throw ICUException("FROM/AFTER cutoffs must have a matching BEFORE cutoff.")
                        if (hour == 25) hour = 0
                        if ((cutoffs[hour] and (1 shl CutoffType.BEFORE.ordinal)) > 0) {
                            rule.add(startHour, hour, period)
                            break
                        }
                        hour++
                    }
                }
            }
        }

        companion object {
            private fun parseHour(str: String): Int {
                val firstColonPos = str.indexOf(':')
                if (firstColonPos < 0 || str.substring(firstColonPos) != ":00") throw ICUException("Cutoff time must end in \":00\".")
                val hourStr = str.substring(0, firstColonPos)
                if (firstColonPos != 1 && firstColonPos != 2) throw ICUException("Cutoff time must begin with h: or hh:")
                val hour = parseInt(hourStr)
                if (hour < 0 || hour > 24) throw ICUException("Cutoff hour must be between 0 and 24, inclusive.")
                return hour
            }
        }
    }

    private class DayPeriodRulesCountSink(private val data: DayPeriodRulesData) : UResource.Sink() {
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val rules = value.getTable()
            var i = 0
            while (rules.getKeyAndValue(i++, key, value)) {
                val setNum = parseSetNum(key.toString())
                if (setNum > data.maxRuleSetNum) data.maxRuleSetNum = setNum
            }
        }
    }

    private var hasMidnight = false
    private var hasNoon = false
    private val dayPeriodForHour = arrayOfNulls<DayPeriod>(24)

    fun getMidPointForDayPeriod(dayPeriod: DayPeriod?): Double {
        val startHour = getStartHourForDayPeriod(dayPeriod)
        val endHour = getEndHourForDayPeriod(dayPeriod)
        var midPoint = (startHour + endHour) / 2.0
        if (startHour > endHour) {
            midPoint += 12
            if (midPoint >= 24) midPoint -= 24
        }
        return midPoint
    }

    private fun getStartHourForDayPeriod(dayPeriod: DayPeriod?): Int {
        if (dayPeriod == DayPeriod.MIDNIGHT) return 0
        if (dayPeriod == DayPeriod.NOON) return 12
        if (dayPeriodForHour[0] == dayPeriod && dayPeriodForHour[23] == dayPeriod) {
            for (i in 22 downTo 1) if (dayPeriodForHour[i] != dayPeriod) return i + 1
        } else {
            for (i in 0..23) if (dayPeriodForHour[i] == dayPeriod) return i
        }
        throw IllegalArgumentException()
    }

    private fun getEndHourForDayPeriod(dayPeriod: DayPeriod?): Int {
        if (dayPeriod == DayPeriod.MIDNIGHT) return 0
        if (dayPeriod == DayPeriod.NOON) return 12
        if (dayPeriodForHour[0] == dayPeriod && dayPeriodForHour[23] == dayPeriod) {
            for (i in 1..22) if (dayPeriodForHour[i] != dayPeriod) return i
        } else {
            for (i in 23 downTo 0) if (dayPeriodForHour[i] == dayPeriod) return i + 1
        }
        throw IllegalArgumentException()
    }

    fun hasMidnight(): Boolean = hasMidnight
    fun hasNoon(): Boolean = hasNoon
    fun getDayPeriodForHour(hour: Int): DayPeriod? {
        // JS array access does not perform the bounds check supplied by the JVM.
        if (hour !in dayPeriodForHour.indices) throw IndexOutOfBoundsException()
        return dayPeriodForHour[hour]
    }

    private fun add(startHour: Int, limitHour: Int, period: DayPeriod?) {
        var i = startHour
        while (i != limitHour) {
            if (i == 24) i = 0
            dayPeriodForHour[i] = period
            i++
        }
    }

    companion object {
        private val DATA = loadData()
        @kotlin.jvm.JvmStatic fun getInstance(locale: ULocale?): DayPeriodRules? {
            var localeCode = locale!!.getBaseName()
            if (localeCode.isEmpty()) localeCode = "root"
            var ruleSetNum: Int? = null
            while (ruleSetNum == null) {
                ruleSetNum = DATA.localesToRuleSetNumMap[localeCode]
                if (ruleSetNum == null) {
                    localeCode = ULocale.getFallback(localeCode)
                    if (localeCode.isEmpty()) break
                } else break
            }
            return if (ruleSetNum == null) null else DATA.rules[ruleSetNum]
        }

        private fun loadData(): DayPeriodRulesData {
            val data = DayPeriodRulesData()
            val rb = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "dayPeriods", disableFallback = true)
            rb.getAllItemsWithFallback("rules", DayPeriodRulesCountSink(data))
            data.rules = arrayOfNulls(data.maxRuleSetNum + 1)
            rb.getAllItemsWithFallback("", DayPeriodRulesDataSink(data))
            return data
        }

        private fun parseSetNum(setNumStr: String): Int {
            if (!setNumStr.startsWith("set")) throw ICUException("Set number should start with \"set\".")
            return parseInt(setNumStr.substring(3))
        }

        private fun parseInt(str: String): Int = str.toIntOrNull()
            ?: throw NumberFormatException("For input string: \"$str\"")
    }
}

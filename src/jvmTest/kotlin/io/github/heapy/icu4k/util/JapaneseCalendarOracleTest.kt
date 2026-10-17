@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import java.net.URLClassLoader
import kotlin.test.*
import com.ibm.icu.util.Calendar as JCalendar
import com.ibm.icu.util.GregorianCalendar as JGregorian
import com.ibm.icu.util.JapaneseCalendar as JJapanese
import com.ibm.icu.util.SimpleTimeZone as JZone
import com.ibm.icu.util.TimeZone as JTimeZone
import com.ibm.icu.util.ULocale as JLocale

class JapaneseCalendarOracleTest {
    private fun compare(k: Calendar, j: JCalendar, label: String) {
        assertEquals(j.type, k.getType(), label)
        assertEquals(j.timeInMillis, k.getTimeInMillis(), label)
        for (field in 0..23) assertEquals(j.get(field), k.get(field), "$label/$field")
        assertEquals(j.relatedYear, k.getRelatedYear(), label)
    }
    @Test fun everyEraBoundaryAndLimitsMatchPinnedRules() {
        val rules = com.ibm.icu.impl.EraRules.getInstance(com.ibm.icu.impl.CalType.JAPANESE, JJapanese.enableTentativeEra())
        for (offset in listOf(0, 19800000)) {
            val k = JapaneseCalendar(SimpleTimeZone(offset, "custom"), ULocale.JAPAN)
            val j = JJapanese(JZone(offset, "custom"), JLocale.JAPAN)
            for (era in 0..rules.maxEraCode) {
                val start = rules.getStartDate(era, null)
                if (start[0] == Int.MIN_VALUE) continue
                j.clear(); j.set(JCalendar.EXTENDED_YEAR, start[0]); j.set(JCalendar.MONTH, start[1] - 1); j.set(JCalendar.DATE, start[2])
                val boundary = j.timeInMillis
                for (delta in listOf(-86400000L, -1L, 0L, 1L, 86400000L)) {
                    val time = boundary + delta
                    k.setTimeInMillis(time); j.timeInMillis = time; compare(k, j, "$offset/$era/$delta")
                    assertEquals(j.getActualMaximum(JCalendar.YEAR), k.getActualMaximum(Calendar.YEAR), "$era/max-year")
                }
                k.clear(); j.clear(); k.set(Calendar.ERA, era); j.set(JCalendar.ERA, era); k.set(Calendar.YEAR, 1); j.set(JCalendar.YEAR, 1)
                compare(k, j, "defaults/$era")
            }
            for (field in 0..23) {
                assertEquals(j.getMinimum(field), k.getMinimum(field))
                assertEquals(j.getMaximum(field), k.getMaximum(field))
                assertEquals(j.getLeastMaximum(field), k.getLeastMaximum(field))
                assertEquals(j.getGreatestMinimum(field), k.getGreatestMinimum(field))
            }
        }
        assertEquals(JJapanese.CURRENT_ERA, JapaneseCalendar.CURRENT_ERA)
    }
    @Test fun fieldPrecedenceMutationsCutoverAndCloneMatchSource() {
        val k = JapaneseCalendar(TimeZone.getTimeZone("Asia/Tokyo", 0), ULocale("ja_JP"))
        val j = JJapanese(JTimeZone.getTimeZone("Asia/Tokyo", 0), JLocale("ja_JP"))
        for (era in listOf(JapaneseCalendar.MEIJI, JapaneseCalendar.TAISHO, JapaneseCalendar.SHOWA, JapaneseCalendar.HEISEI, JapaneseCalendar.REIWA)) {
            for (year in listOf(1, 2, 30)) for (field in listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DATE, Calendar.WEEK_OF_YEAR)) {
                for (amount in listOf(-13, -1, 0, 1, 13)) {
                    k.clear(); j.clear(); k.set(Calendar.ERA, era); j.set(JCalendar.ERA, era)
                    k.set(year, 0, 31); j.set(year, 0, 31)
                    k.add(field, amount); j.add(field, amount); compare(k, j, "add/$era/$year/$field/$amount")
                    k.roll(field, -amount); j.roll(field, -amount); compare(k, j, "roll/$era/$year/$field/$amount")
                }
            }
        }
        for (fields in listOf(listOf(Calendar.EXTENDED_YEAR to 2019, Calendar.ERA to JapaneseCalendar.HEISEI), listOf(Calendar.ERA to JapaneseCalendar.HEISEI, Calendar.EXTENDED_YEAR to 2019), listOf(Calendar.YEAR to 1), emptyList())) {
            k.clear(); j.clear(); for ((field, value) in fields) { k.set(field, value); j.set(field, value) }
            compare(k, j, "stamps/$fields")
        }
        k.setGregorianChange(Date(-20000000000000L)); j.gregorianChange = java.util.Date(-20000000000000L)
        k.setTimeInMillis(-15000000000000L); j.timeInMillis = -15000000000000L
        val kc = k.clone(); val jc = j.clone()
        assertIs<JapaneseCalendar>(kc); compare(kc, jc, "clone")
        kc.add(Calendar.YEAR, 1); jc.add(JCalendar.YEAR, 1); compare(kc, jc, "clone-mutation"); compare(k, j, "original")
        assertEquals(j.hashCode(), k.hashCode())
    }
    @Test fun constructorMatrixPreservesDefaultsFieldsAndLocaleBoundaries() {
        val oldK = TimeZone.getDefault(); val oldJ = JTimeZone.getDefault()
        try {
            TimeZone.setICUDefault(SimpleTimeZone(19800000, "private")); JTimeZone.setICUDefault(JZone(19800000, "private"))
            val pairs: List<Pair<Calendar, JCalendar>> = listOf(
                GregorianCalendar(2024, 1, 29) to JGregorian(2024, 1, 29),
                GregorianCalendar(2024, 1, 29, 12, 34) to JGregorian(2024, 1, 29, 12, 34),
                GregorianCalendar(2024, 1, 29, 12, 34, 56) to JGregorian(2024, 1, 29, 12, 34, 56),
                JapaneseCalendar(1, 4, 1) to JJapanese(1, 4, 1),
                JapaneseCalendar(JapaneseCalendar.REIWA, 1, 4, 1) to JJapanese(JJapanese.REIWA, 1, 4, 1),
                JapaneseCalendar(1, 4, 1, 12, 34, 56) to JJapanese(1, 4, 1, 12, 34, 56),
                JapaneseCalendar(Date(0)) to JJapanese(java.util.Date(0)),
            )
            for ((k, j) in pairs) {
                for (field in 0..23) assertEquals(j.isSet(field), k.isSet(field), "constructor-stamp/$field")
                assertEquals(j.timeZone.id, k.getTimeZone()!!.getID()); compare(k, j, "constructor/${k.getType()}")
            }
            for (locale in listOf("ja_JP", "en@timezone=America/New_York", "ja-u-tz-jptyo")) {
                val ku = JapaneseCalendar(ULocale(locale)); val ju = JJapanese(JLocale(locale))
                assertEquals(ju.timeZone.id, ku.getTimeZone()!!.getID())
                ku.setTimeInMillis(0); ju.timeInMillis = 0; compare(ku, ju, locale)
            }
            for (locale in listOf(java.util.Locale.JAPAN, java.util.Locale.forLanguageTag("en-u-tz-usnyc"))) {
                val ku = JapaneseCalendar(locale); val ju = JJapanese(locale)
                assertEquals(ju.timeZone.id, ku.getTimeZone()!!.getID()); ku.setTimeInMillis(0); ju.timeInMillis = 0; compare(ku, ju, "JVM Locale")
                val kg = GregorianCalendar(SimpleTimeZone(0, "explicit"), locale); val jg = JGregorian(JZone(0, "explicit"), locale)
                kg.setTimeInMillis(0); jg.timeInMillis = 0; compare(kg, jg, "JVM explicit Locale")
            }
        } finally { TimeZone.setICUDefault(oldK); JTimeZone.setICUDefault(oldJ) }
    }
    @Test fun tentativePolicyRemainsDynamicWithoutReinitializingConstants() {
        val names = listOf("ICU_ENABLE_TENTATIVE_ERA", "jdk.calendar.japanese.supplemental.era")
        val saved = names.associateWith { System.getProperty(it) }
        val current = JapaneseCalendar.CURRENT_ERA
        try {
            for ((flag, supplemental) in listOf(null to null, "true" to null, "TrUe" to "", "false" to "present", "" to "present", null to "")) {
                for ((name, value) in names.zip(listOf(flag, supplemental))) if (value == null) System.clearProperty(name) else System.setProperty(name, value)
                assertEquals(JJapanese.enableTentativeEra(), JapaneseCalendar.enableTentativeEra(), "$flag/$supplemental")
                assertEquals(current, JapaneseCalendar.CURRENT_ERA)
            }
        } finally { for ((name, value) in saved) if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
    }
    @Test fun freshClassInitializationAndConstructorClockOrderMatchSource() {
        val urls = listOf(JapaneseCalendarBootstrapProbe::class.java, Calendar::class.java, kotlin.Unit::class.java, JCalendar::class.java).map { it.protectionDomain.codeSource.location }.distinct().toTypedArray()
        fun run(mode: String, form: String): String = URLClassLoader(urls, null).use { loader ->
            try {
                loader.loadClass(JapaneseCalendarBootstrapProbe::class.java.name).getMethod("run", String::class.java, String::class.java).invoke(null, mode, form) as String
            } catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
        }
        for (form in listOf("now", "fields", "date")) assertEquals(run("source", form), run("port", form), form)
    }
    @Test fun strictEraYearAndDateValidationMatchSource() {
        val k = JapaneseCalendar(SimpleTimeZone(0, "custom"), ULocale.US)
        val j = JJapanese(JZone(0, "custom"), JLocale.US)
        k.setLenient(false); j.isLenient = false
        fun result(block: () -> Long): String = try { "value:${block()}" } catch (error: RuntimeException) { "${error.javaClass.simpleName}:${error.message}" }
        for (era in listOf(-1, JapaneseCalendar.REIWA, k.getMaximum(Calendar.ERA) + 1)) {
            for (year in listOf(0, 1, 2, Int.MAX_VALUE)) for (month in listOf(-1, 1, 4, 12)) for (day in listOf(0, 1, 29, 32)) {
                k.clear(); j.clear(); k.set(Calendar.ERA, era); j.set(JCalendar.ERA, era)
                k.set(year, month, day); j.set(year, month, day)
                assertEquals(result { j.timeInMillis }, result { k.getTimeInMillis() }, "$era/$year/$month/$day")
            }
        }
    }
}

package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.LongNameHandler as JHandler
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.impl.StandardPlural as JPlural
import com.ibm.icu.number.NumberFormatter.UnitWidth as JWidth
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class LongNameHandlerOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is java.util.MissingResourceException, is MissingResourceException -> "missing"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    private fun compare(j: com.ibm.icu.impl.number.Modifier?, k: Modifier?, label: String) {
        assertEquals(j == null, k == null, label)
        if (j == null || k == null) return
        val jb = JBuffer(); val kb = FormattedStringBuilder()
        jb.append("123", null); kb.append("123", null)
        assertEquals(j.apply(jb, 0, 3), k.apply(kb, 0, 3), label)
        assertEquals(jb.toString(), kb.toString(), label)
        assertEquals(jb.toFieldArray().map { it?.toString()?.substringAfterLast('(') },
            kb.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
        assertEquals(j.getPrefixLength(), k.getPrefixLength(), label)
        assertEquals(j.getCodePointCount(), k.getCodePointCount(), label)
        assertEquals(j.isStrong(), k.isStrong(), label)
        assertEquals(j.parameters?.plural?.name, k.getParameters()?.plural?.name, label)
        assertNull(k.getParameters()?.signum)
    }
    @Test fun builtinAndArbitraryPatternsAcrossAllPluralForms() {
        for (locale in listOf("en", "de", "fr", "de_CH", "fr_CA")) {
            for (id in listOf("meter", "year-person", "liter", "meter-per-second", "meter-second", "centimeter-second", "square-centimeter-second", "cubic-kilometer-per-millisecond", "per-second", "meter-per-100-second", "meter-per-100", "kibibyte", "pow4-meter", "meter-and-second")) {
                for (width in UnitWidth.entries) for (case in listOf("", "accusative", "dative")) {
                    val label = "$locale/$id/$width/$case"
                    var j: JHandler? = null; var k: LongNameHandler? = null
                    val jo = outcome { j = JHandler.forMeasureUnit(JLocale(locale), JUnit.forIdentifier(id), JWidth.valueOf(width.name), case, JRules.forLocale(JLocale(locale)), null); "ok" }
                    val ko = outcome { k = LongNameHandler.forMeasureUnit(ULocale(locale), MeasureUnit.forIdentifier(id), width, case, PluralRules.forLocale(ULocale(locale)), null); "ok" }
                    assertEquals(jo, ko, label)
                    if (j == null || k == null) continue
                    for (plural in StandardPlural.entries) compare(j!!.getModifier(null, JPlural.valueOf(plural.name)), k!!.getModifier(null, plural), "$label/$plural")
                    assertNull(k!!.getModifier(null, null))
                    assertSame(k, k!!.getModifier(null, StandardPlural.OTHER)!!.getParameters()!!.obj)
                    val jm = JMicro(true); jm.rounder = JPrecision.maxFraction(0); jm.gender = "keep"
                    val km = MicroProps(true); km.rounder = Precision.maxFraction(0); km.gender = "keep"
                    j!!.processQuantityWithMicros(JBCD(1.2), jm); k!!.processQuantityWithMicros(DecimalQuantity_DualStorageBCD(1.2), km)
                    assertEquals("keep", km.gender)
                    compare(jm.modOuter, km.modOuter, "$label/selected")
                }
            }
        }
    }

    @Test fun currencyAndParentPipelineRoundingAndGender() {
        for (locale in listOf("en", "de", "fr", "ar", "ru", "ja")) for (code in listOf("USD", "EUR", "JPY", "XXX")) {
            val jm = JMicro(true); jm.rounder = JPrecision.maxFraction(0)
            val km = MicroProps(true); km.rounder = Precision.maxFraction(0)
            val j = JHandler.forCurrencyLongNames(JLocale(locale), JCurrency.getInstance(code), JRules.forLocale(JLocale(locale)), jm)
            val k = LongNameHandler.forCurrencyLongNames(ULocale(locale), Currency.getInstance(code), PluralRules.forLocale(ULocale(locale)), km)
            for (number in listOf(-1.0, 0.0, 1.2, 1.8, 2.0, 5.0, 11.0, 21.0, 101.0)) {
                val jq = JBCD(number); val kq = DecimalQuantity_DualStorageBCD(number)
                val a = j.processQuantity(jq); val b = k.processQuantity(kq)
                compare(a.modOuter, b.modOuter, "$locale/$code/$number")
                assertEquals(a.gender, b.gender)
                jq.roundToInfinity(); kq.roundToInfinity()
                assertEquals(jq.toPlainString(), kq.toPlainString())
            }
        }
        for (locale in listOf("de", "fr")) for (id in listOf("meter", "meter-second", "kilometer-per-square-second")) {
            val jm = JMicro(true); jm.rounder = JPrecision.unlimited()
            val km = MicroProps(true); km.rounder = Precision.unlimited()
            val j = JHandler.forMeasureUnit(JLocale(locale), JUnit.forIdentifier(id), JWidth.FULL_NAME, "", JRules.forLocale(JLocale(locale)), jm)
            val k = LongNameHandler.forMeasureUnit(ULocale(locale), MeasureUnit.forIdentifier(id), UnitWidth.FULL_NAME, "", PluralRules.forLocale(ULocale(locale)), km)
            assertEquals(j.processQuantity(JBCD(2)).gender, k.processQuantity(DecimalQuantity_DualStorageBCD(2)).gender)
        }
    }

    @Test fun displayNamesAndGenderSelection() {
        val method = JHandler::class.java.getDeclaredMethod("calculateGenderForUnit", JLocale::class.java, JUnit::class.java).also { it.isAccessible = true }
        for (locale in listOf("en", "de", "fr")) for (id in listOf("meter", "square-meter", "centimeter", "meter-second", "meter-per-second", "per-second", "meter-and-second")) {
            val ju = JUnit.forIdentifier(id); val ku = MeasureUnit.forIdentifier(id)
            assertEquals(outcome { try { method.invoke(null, JLocale(locale), ju) } catch (e: InvocationTargetException) { throw e.cause!! } },
                outcome { LongNameHandler.calculateGenderForUnit(ULocale(locale), ku) }, "$locale/$id gender")
            for (width in UnitWidth.entries) assertEquals(outcome { JHandler.getUnitDisplayName(JLocale(locale), ju, JWidth.valueOf(width.name)) },
                outcome { LongNameHandler.getUnitDisplayName(ULocale(locale), ku, width) }, "$locale/$id/$width display")
        }
    }
    @Test fun productPatternMutationAndPartialFailure() {
        val method = JHandler::class.java.getDeclaredMethod("processPatternTimes",
            com.ibm.icu.impl.units.MeasureUnitImpl::class.java, JLocale::class.java,
            JWidth::class.java, String::class.java, Array<String>::class.java).also { it.isAccessible = true }
        for (locale in listOf("en", "de", "fr")) for (id in listOf("centimeter-second", "square-centimeter-second", "cubic-kilometer", "pow4-meter-second", "meter-per-100", "meter-and-second")) {
            for (width in listOf(UnitWidth.FULL_NAME, UnitWidth.SHORT, UnitWidth.NARROW)) {
                val j = JUnit.forIdentifier(id).copyOfMeasureUnitImpl
                val k = MeasureUnit.forIdentifier(id)!!.getCopyOfMeasureUnitImpl()
                val ja = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
                val ka = ja.copyOf()
                assertEquals(outcome { try { method.invoke(null, j, JLocale(locale), JWidth.valueOf(width.name), "dative", ja); "ok" } catch (e: InvocationTargetException) { throw e.cause!! } },
                    outcome { LongNameHandler.processPatternTimes(k, ULocale(locale), width, "dative", ka); "ok" }, "$locale/$id/$width")
                assertContentEquals(ja, ka, "$locale/$id/$width data")
                assertEquals(j.identifier, k.getIdentifier())
                assertEquals(j.singleUnits.map { listOf(it.simpleUnitID, it.dimensionality.toString(), it.prefix.name) },
                    k.getSingleUnits().map { listOf(it.getSimpleUnitID(), it.getDimensionality().toString(), it.getPrefix()!!.name) })
            }
        }
    }

}

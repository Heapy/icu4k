package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.LongNameHandler as JHandler
import com.ibm.icu.number.NumberFormatter.UnitWidth as JWidth
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class LongNameDataOracleTest {
    private fun source(name: String, vararg args: Any?): Any? = try {
        JHandler::class.java.declaredMethods.single { it.name == name }.also { it.isAccessible = true }.invoke(null, *args)
    } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) {
        when (e) {
            is java.util.MissingResourceException, is io.github.heapy.icu4k.util.MissingResourceException -> "MissingResourceException"
            is IndexOutOfBoundsException -> "IndexOutOfBoundsException"
            is NullPointerException -> "NullPointerException"
            else -> e::class.simpleName + ":" + e.message
        }
    }

    @Test fun measurePatternsAliasesWidthsAndPreseededValues() {
        for (locale in listOf("root", "en", "en_US", "en_GB")) {
            for (id in listOf("meter", "kilometer-per-hour", "year-person", "liter", "square-meter", "kilogram")) {
                for (width in UnitWidth.entries) for (case in listOf("", "nominative", "accusative")) {
                    val j = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
                    val k = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
                    j[0] = "preseed"; k[0] = "preseed"
                    val ju = JUnit.forIdentifier(id); val ku = MeasureUnit.forIdentifier(id)
                    val jo = outcome { source("getMeasureData", JLocale(locale), ju, JWidth.valueOf(width.name), case, j); "ok" }
                    val ko = outcome { LongNameData.getMeasureData(ULocale(locale), ku, width, case, k); "ok" }
                    assertEquals(jo, ko, "$locale/$id/$width/$case")
                    assertContentEquals(j, k, "$locale/$id/$width/$case")
                }
            }
        }
    }

    @Test fun currencyPatternsAndCompoundFallbacks() {
        for (locale in listOf("root", "en", "fr", "ar", "ru", "ja", "es")) {
            for (code in listOf("USD", "EUR", "JPY", "XXX")) {
                val j = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
                val k = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
                source("getCurrencyLongNameData", JLocale(locale), JCurrency.getInstance(code), j)
                LongNameData.getCurrencyLongNameData(ULocale(locale), Currency.getInstance(code), k)
                assertContentEquals(j, k, "$locale/$code")
            }
        }
        for (locale in listOf("root", "en", "en_US")) for (width in UnitWidth.entries) {
            for (key in listOf("per", "times", "power2", "missing", null)) {
                assertEquals(outcome { source("getCompoundValue", key, JLocale(locale), JWidth.valueOf(width.name)) },
                    outcome { LongNameData.getCompoundValue(key, ULocale(locale), width) }, "$locale/$width/$key")
            }
            for (gender in listOf(null, "", "feminine", "neuter")) for (case in listOf(null, "", "accusative")) {
                val j = Array<String?>(LongNameData.ARRAY_LENGTH) { "old" }
                val k = j.copyOf()
                val jo = outcome { source("getInflectedMeasureData", "compound/power2", JLocale(locale), JWidth.valueOf(width.name), gender, case, j); "ok" }
                val ko = outcome { LongNameData.getInflectedMeasureData("compound/power2", ULocale(locale), width, gender, case, k); "ok" }
                assertEquals(jo, ko); assertContentEquals(j, k)
            }
        }
    }

    @Test fun grammaticalDerivationsAndLanguageFallback() {
        val klass = JHandler::class.java.declaredClasses.single { it.simpleName == "DerivedComponents" }
        val ctor = klass.declaredConstructors.single().also { it.isAccessible = true }
        for (locale in listOf("root", "en", "de", "fr", "ru", "pl", "ar", "xx", "de_CH")) {
            for (feature in listOf("case", "gender", "missing")) for (structure in listOf("per", "times", "power", "prefix", "missing")) {
                val j = ctor.newInstance(JLocale(locale), feature, structure)
                val k = LongNameData.DerivedComponents(ULocale(locale), feature, structure)
                for (value in listOf("accusative", "", null)) {
                    val a = klass.getDeclaredMethod("value0", String::class.java).also { it.isAccessible = true }.invoke(j, value)
                    val b = klass.getDeclaredMethod("value1", String::class.java).also { it.isAccessible = true }.invoke(j, value)
                    assertEquals(a, k.value0(value)); assertEquals(b, k.value1(value))
                }
                assertEquals(outcome { source("getDeriveCompoundRule", JLocale(locale), feature, structure) },
                    outcome { LongNameData.getDeriveCompoundRule(ULocale(locale), feature, structure) }, "$locale/$feature/$structure")
            }
        }
    }

    @Test fun corePatternWhitespaceAndPlaceholderPlacement() {
        val patterns = listOf("", "{0}", "{0}meters", "meters{0}", "{0}  m", "a {0} b", "{1}", "{0}{0}") +
            listOf(' ', '\t', '\n', '\u00a0', '\u1680', '\u2007', '\u2028', '\u2029', '\u202f', '\u205f', '\u3000').flatMap { listOf("{0}${it}m", "m${it}{0}") }
        for (pattern in patterns) {
            val j = source("extractCorePattern", pattern)!!
            fun field(name: String): Any? = j.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(j)
            val k = LongNameData.extractCorePattern(pattern)
            assertEquals(field("coreUnit"), k.coreUnit, pattern)
            assertEquals(field("placeholderPosition").toString(), k.placeholderPosition.name, pattern)
            assertEquals(field("joinerChar"), k.joinerChar, pattern)
        }
        for (size in listOf(0, 1, 5, 11)) {
            val j = Array<String?>(size) { "old" }; val k = j.copyOf()
            assertEquals(outcome { source("getInflectedMeasureData", "missing", JLocale.ENGLISH, JWidth.FULL_NAME, null, null, j) },
                outcome { LongNameData.getInflectedMeasureData("missing", ULocale.ENGLISH, UnitWidth.FULL_NAME, null, null, k) })
            assertContentEquals(j, k)
        }
    }
}

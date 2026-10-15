@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.CurrencyPluralInfo as JInfo
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import kotlin.test.*

class CurrencyPluralInfoOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { e.javaClass.simpleName }
    private fun check(j: JInfo, k: CurrencyPluralInfo, label: String = "") {
        assertEquals(j.locale?.name, k.getLocale()?.getName(), label)
        assertEquals(j.pluralRules?.toString(), k.getPluralRules()?.toString(), label)
        val jk = j.pluralPatternIterator().asSequence().toList(); val kk = k.pluralPatternIterator().asSequence().toList()
        assertEquals(jk, kk, label)
        for (keyword in jk + listOf(null, "zero", "one", "two", "few", "many", "other", "unknown")) {
            assertEquals(outcome { j.getCurrencyPluralPattern(keyword) }, outcome { k.getCurrencyPluralPattern(keyword) }, "$label/$keyword")
        }
        // PluralRules' source hash includes rule-list object identity; compare the remaining formula.
        assertEquals(outcome { j.hashCode() xor j.pluralRules.hashCode() }, outcome { k.hashCode() xor k.getPluralRules().hashCode() }, label)
    }
    @Test fun localeInitializationFactoriesAndSelectionMatchSource() {
        check(JInfo(), CurrencyPluralInfo())
        check(JInfo.getInstance(), CurrencyPluralInfo.getInstance())
        for (locale in JSymbols.getAvailableULocales()) {
            val j = JInfo.getInstance(locale); val k = CurrencyPluralInfo.getInstance(ULocale(locale.name))
            check(j, k, locale.name)
            check(JInfo(locale.toLocale()), CurrencyPluralInfo(locale.toLocale()), locale.name)
            check(JInfo.getInstance(locale.toLocale()), CurrencyPluralInfo.getInstance(locale.toLocale()), locale.name)
            assertSame(k.getPluralRules(), k.clone().getPluralRules())
            for (value in listOf(-1.0, 0.0, 1.0, 2.0, 3.5, 21.0, 1000000.0, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertEquals(j.select(JRules.FixedDecimal(value)), k.select(PluralRules.FixedDecimal(value)), "$locale/$value")
            }
        }
        assertEquals(outcome { JInfo(null as JLocale?) }, outcome { CurrencyPluralInfo(null as ULocale?) })
    }
    @Test fun mutableRulesPatternsNullsAndLocaleResetMatchSource() {
        val j = JInfo(JLocale("en_US")); val k = CurrencyPluralInfo(ULocale("en_US"))
        for ((key, pattern) in listOf("one" to "one {0} ¤¤¤", "other" to null, null to "null-key", "few" to "few", null to null)) {
            j.setCurrencyPluralPattern(key, pattern); k.setCurrencyPluralPattern(key, pattern); check(j, k)
        }
        assertEquals(0, k.getCurrencyPluralPattern("missing")[0].code)
        for (description in listOf(null, "bad", "one: n = 1; few: n = 2..4; other:", "")) {
            j.setPluralRules(description); k.setPluralRules(description); check(j, k, "rules/$description")
            assertEquals(outcome { j.equals(j) }, outcome { k.equals(k) })
            assertEquals(outcome { j.select(JRules.FixedDecimal(2.0)) }, outcome { k.select(PluralRules.FixedDecimal(2.0)) })
            assertEquals(j.equals("different"), k.equals("different"))
            val jc = j.clone(); val kc = k.clone(); check(jc, kc)
            assertEquals(outcome { jc == j }, outcome { kc == k })
        }
        assertEquals(outcome { j.setLocale(null) }, outcome { k.setLocale(null) }); check(j, k, "failed-locale")
        assertEquals(outcome { j.clone() }, outcome { k.clone() })
        j.setLocale(JLocale("ru")); k.setLocale(ULocale("ru")); check(j, k, "locale-reset")
        val jc = j.clone(); val kc = k.clone()
        jc.setCurrencyPluralPattern("one", "changed"); kc.setCurrencyPluralPattern("one", "changed")
        check(jc, kc); check(j, k)
        assertEquals(jc == j, kc == k)
        val ji = jc.pluralPatternIterator(); val ki = kc.pluralPatternIterator()
        assertEquals(ji.next(), ki.next()); ji.remove(); ki.remove(); check(jc, kc)
        val je = JInfo(JLocale("en")); val ke = CurrencyPluralInfo(ULocale("en"))
        assertEquals(je == JInfo(JLocale("en_US")), ke == CurrencyPluralInfo(ULocale("en_US")))
    }
    private fun roundTrip(value: Any): Any = ByteArrayOutputStream().let { bytes ->
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }
    @Test fun cloningSubclassAndSerializationRetainIndependentMapAndRules() {
        val j = JInfo(JLocale("ar")); val k = CurrencyPluralInfo(ULocale("ar"))
        j.setCurrencyPluralPattern(null, "null-key"); k.setCurrencyPluralPattern(null, "null-key")
        j.setCurrencyPluralPattern("few", null); k.setCurrencyPluralPattern("few", null)
        check(roundTrip(j) as JInfo, roundTrip(k) as CurrencyPluralInfo)
        j.setPluralRules("invalid"); k.setPluralRules("invalid")
        check(roundTrip(j) as JInfo, roundTrip(k) as CurrencyPluralInfo)
        val js = object : JInfo(JLocale("en")) { val extra = Any() }
        val ks = object : CurrencyPluralInfo(ULocale("en")) { val extra = Any() }
        assertEquals<Class<*>>(js.javaClass, js.clone().javaClass)
        val kc = ks.clone()
        assertEquals<Class<*>>(ks.javaClass, kc.javaClass)
        assertSame(ks.extra, kc.javaClass.getDeclaredField("extra").also { it.isAccessible = true }.get(kc))
    }
}

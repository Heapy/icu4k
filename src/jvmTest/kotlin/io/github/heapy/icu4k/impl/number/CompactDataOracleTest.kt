package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.CompactData as JData
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle as JStyle
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CompactDataOracleTest {
    private fun compare(j: JData, k: CompactData, locale: String) {
        val jr = JRules.forLocale(JLocale(locale))
        val kr = PluralRules.forLocale(ULocale(locale))
        for (magnitude in -2..24) {
            assertEquals(j.getMultiplier(magnitude), k.getMultiplier(magnitude), "$locale multiplier $magnitude")
            for (number in listOf(0, 1, 2, 3, 5, 11, 21, 101, -1)) {
                assertEquals(j.getPattern(magnitude, jr, JBCD(number)),
                    k.getPattern(magnitude, kr, DecimalQuantity_DualStorageBCD(number)), "$locale pattern $magnitude/$number")
            }
        }
        val js = mutableSetOf<String>(); val ks = mutableSetOf<String>()
        j.getUniquePatterns(js); k.getUniquePatterns(ks)
        assertEquals(js, ks, "$locale unique")
        assertNull(k.getPattern(-1, null, null))
    }

    @Test fun localeFallbackAndPluralPatterns() {
        for (locale in listOf("en", "fr", "ru", "ar", "it", "ja", "zh", "bn", "so", "sw", "root", "en_IN")) {
            for (ns in listOf("latn", "arab", "missing")) for (style in CompactStyle.entries) for (type in CompactData.CompactType.entries) {
                val j = JData(); val k = CompactData()
                j.populate(JLocale(locale), ns, JStyle.valueOf(style.name), JData.CompactType.valueOf(type.name))
                k.populate(ULocale(locale), ns, style, type)
                compare(j, k, locale)
            }
        }
    }

    @Test fun customPatternsExactNumbersLiteralZerosAndMagnitudeBounds() {
        val data: Map<String, Map<String, String>> = linkedMapOf(
            "1" to linkedMapOf("=0" to "zero", "=1" to "exact one", "one" to "0 one", "other" to "0"),
            "1000" to linkedMapOf("one" to "00K", "other" to "0K"),
            "10000" to linkedMapOf("other" to "'00'0 suffix"),
            "100000" to linkedMapOf("other" to "literal"),
            "1".repeat(21) to linkedMapOf("other" to "0Z")
        )
        val j = JData(); val k = CompactData()
        j.populate(data); k.populate(data)
        compare(j, k, "en")
    }

    @Test fun uniquePatternsExposeLiveFixedSizeArrayIncludingNulls() {
        val j = JData(); val k = CompactData()
        var jView: MutableList<String?>? = null
        var kView: MutableList<String?>? = null
        fun capture(save: (MutableList<String?>) -> Unit): MutableSet<String> = object : HashSet<String>() {
            @Suppress("UNCHECKED_CAST")
            override fun addAll(elements: Collection<String>): Boolean {
                save(elements as MutableList<String?>)
                return super.addAll(elements)
            }
        }
        j.getUniquePatterns(capture { jView = it }); k.getUniquePatterns(capture { kView = it })
        assertEquals(168, kView!!.size)
        assertEquals(jView!!.toList(), kView!!.toList())
        val data = mapOf("1" to mapOf("other" to "0X"))
        j.populate(data); k.populate(data)
        assertEquals(jView!!.toList(), kView!!.toList())
        jView!![5] = "edited"; kView!![5] = "edited"
        assertEquals(j.getPattern(0, JRules.DEFAULT, JBCD(2)), k.getPattern(0, PluralRules.DEFAULT, DecimalQuantity_DualStorageBCD(2)))
        assertFailsWith<UnsupportedOperationException> { jView!!.add("x") }
        assertFailsWith<UnsupportedOperationException> { kView!!.add("x") }
    }
    @Test fun failedPopulationRetainsSourcePartialState() {
        fun outcome(action: () -> Unit): String = try { action(); "ok" } catch (e: Throwable) {
            when (e) {
                is NullPointerException -> "null"
                is IndexOutOfBoundsException -> "bounds"
                else -> e.javaClass.simpleName + ":" + e.message
            }
        }
        for (badKey in listOf("", "1".repeat(22), "1".repeat(129), "1".repeat(257))) {
            val map: Map<String, Map<String, String>> = linkedMapOf(
                "1000" to mapOf("other" to "0K"), badKey to mapOf("other" to "0X"))
            val j = JData(); val k = CompactData()
            assertEquals(outcome { j.populate(map) }, outcome { k.populate(map) }, "length=${badKey.length}")
            compare(j, k, "en")
        }
        val invalid: Map<String, Map<String, String>> = linkedMapOf(
            "1000" to linkedMapOf("other" to "0K", "bogus" to "00X"))
        val j = JData(); val k = CompactData()
        assertEquals(outcome { j.populate(invalid) }, outcome { k.populate(invalid) })
        compare(j, k, "en")
    }

}

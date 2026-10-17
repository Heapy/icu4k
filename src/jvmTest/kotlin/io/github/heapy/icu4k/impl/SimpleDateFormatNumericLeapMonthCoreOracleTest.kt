package io.github.heapy.icu4k.impl

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class SimpleDateFormatNumericLeapMonthCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) {
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    private fun normalized(value: Any?): Any? = when (value) {
        is Array<*> -> value.map(::normalized)
        is java.util.Date -> value.time
        is Number -> value.toString()
        else -> value
    }
    @Test fun completeStockCacheThenArgumentOverrideAndParsingMatchSource() {
        val patterns = listOf(
            "", "{0}", "L{0}!", "{0,number,000}", "{0,number,::precision-integer}",
            "{0,choice,0#zero|1#one}", "{0,plural,other{{1,number,000}}}",
            "{0} / {1,number,000}!", "{0} / {1,spellout}!", "{0} / {1,date,yyyy-MM-dd}!",
            "{0} / {1,time,HH:mm}!", "{0} / {1,number,::precision-integer}!",
            "{month} {1,number}", "{0,plural,other{{1,unknown}}}", "{0,number,bad'pattern}"
        )
        val texts = listOf("", "12", "L12!", "12 / 007!", "12 / seven!", "12 / 1970-01-01!", "12 / 01:30!", "zero", "1,234", "१२", "١٢", "leap", "{0}")
        for (id in listOf("en_US", "fr", "ar", "hi_IN", "zh@calendar=chinese", "he@calendar=hebrew")) {
            for (pattern in patterns) {
                var source: Source? = null
                var target: SimpleDateFormatNumericLeapMonthCore? = null
                val styles = MessageFormatStyleCore(MessageFormatStyleCoreOracleTest.Owner(ULocale(id)))
                assertEquals(outcome { source = Source(pattern, SourceLocale(id)); "ok" }, outcome {
                    target = SimpleDateFormatNumericLeapMonthCore(pattern) { type, style ->
                        when (val format = styles.create(type, style)) {
                            null -> null
                            is UFormatBase -> MessageFormatParseCore.Parser { text, pos -> format.parseObject(text, pos) }
                            is LocalizedNumberFormatterAsFormat -> MessageFormatParseCore.Parser { text, pos -> format.parseObject(text, pos) }
                            else -> error("Unexpected stock format: $format")
                        }
                    }
                    "ok"
                }, "$id/$pattern/construction")
                if (source == null || target == null) continue
                for (nullFormat in listOf(false, true)) {
                    val a = if (nullFormat) null else SourceNumber.getInstance(SourceLocale(id))
                    val b = if (nullFormat) null else NumberFormat.getInstance(ULocale(id))
                    val set = outcome { source!!.setFormatByArgumentIndex(0, a); "ok" }
                    assertEquals(set, outcome { target!!.setNumberFormat(b); "ok" }, "$id/$pattern/set")
                    if (set != "ok") continue
                    for (text in texts) for (start in listOf(0, 1)) {
                        val ap = java.text.ParsePosition(start)
                        val bp = ParsePosition(start)
                        assertEquals(outcome { normalized(source!!.parse(text, ap)) }, outcome { normalized(target!!.parse(text, bp)) }, "$id/$pattern/$text/$start/$nullFormat")
                        assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex), "$id/$pattern/$text/position")
                    }
                }
            }
        }
    }
}

package io.github.heapy.icu4k.impl

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.util.ULocale
import kotlin.test.*

class SimpleDateFormatPatternCoreOracleTest {
    private val sourceClass = Source::class.java
    private val getItems = sourceClass.getDeclaredMethod("getPatternItems").apply { isAccessible = true }
    private val itemClass = Class.forName("com.ibm.icu.text.SimpleDateFormat\$PatternItem")
    private val type = itemClass.getDeclaredField("type").apply { isAccessible = true }
    private val length = itemClass.getDeclaredField("length").apply { isAccessible = true }
    private val numeric = itemClass.getDeclaredField("isNumeric").apply { isAccessible = true }
    private val formatter = Source("", ULocale.ROOT)

    private fun compare(pattern: String) {
        formatter.applyPattern(pattern)
        val source = (getItems.invoke(formatter) as Array<*>).map {
            if (it is String) it else listOf(type.getChar(it), length.getInt(it), numeric.getBoolean(it))
        }
        val port = SimpleDateFormatPatternCore.compile(pattern).map {
            if (it is String) it else (it as SimpleDateFormatPatternCore.PatternItem).let { listOf(it.type, it.length, it.isNumeric) }
        }
        assertEquals(source, port, pattern)
        val flags = SimpleDateFormatPatternCore.scan(pattern)
        for ((name, value) in listOf("hasMinute" to flags.hasMinute, "hasSecond" to flags.hasSecond, "hasHanYearChar" to flags.hasHanYearChar)) {
            assertEquals(sourceClass.getDeclaredField(name).apply { isAccessible = true }.getBoolean(formatter), value, "$name/$pattern")
        }
    }

    @Test fun quotedAndUnterminatedPatternsAndNumericRuns() {
        for (pattern in listOf("", "'", "''", "'''", "''''", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy'Q'QQQ", "'ms年'", "m's'", "'年", "年 ms", "yyyy''MM", "jjj CCC iii", "\u0000\ud800年\udfff")) compare(pattern)
        for (ch in 'A'..'z') for (count in 1..12) compare(ch.toString().repeat(count))
        val alphabet = charArrayOf('\'', 'y', 'M', 'm', 's', '年', ' ', ':', 'j')
        fun visit(prefix: String, depth: Int) {
            compare(prefix)
            if (depth > 0) for (ch in alphabet) visit(prefix + ch, depth - 1)
        }
        visit("", 4)
    }

    @Test fun everyUtf16CharacterHasSourceSyntaxAndFieldMapping() {
        val syntax = sourceClass.getDeclaredMethod("isSyntaxChar", Char::class.javaPrimitiveType).apply { isAccessible = true }
        val index = sourceClass.getDeclaredMethod("getIndexFromChar", Char::class.javaPrimitiveType).apply { isAccessible = true }
        val attribute = sourceClass.getDeclaredMethod("patternCharToDateFormatField", Char::class.javaPrimitiveType).apply { isAccessible = true }
        for (code in 0..65535) {
            val ch = code.toChar()
            assertEquals(syntax.invoke(null, ch), SimpleDateFormatPatternCore.isSyntaxChar(ch), "$code")
            assertEquals(index.invoke(null, ch), SimpleDateFormatPatternCore.index(ch), "$code")
            assertEquals(attribute.invoke(formatter, ch)?.toString()?.substringAfter('('), SimpleDateFormatPatternCore.attribute(ch)?.toString()?.substringAfter('('), "$code")
        }
        for ((name, read) in listOf("PATTERN_INDEX_TO_CALENDAR_FIELD" to SimpleDateFormatPatternCore::calendarField, "PATTERN_INDEX_TO_DATE_FORMAT_FIELD" to SimpleDateFormatPatternCore::dateFormatField)) {
            val values = sourceClass.getDeclaredField(name).apply { isAccessible = true }.get(null) as IntArray
            for (i in values.indices) assertEquals(values[i], read(i), "$name/$i")
        }
    }

    @Test fun compilationUsesSharedCachedArrayAndScanDoesNotValidate() {
        val pattern = "yyyy'PATTERNCORECACHE'j''MM"
        val a = SimpleDateFormatPatternCore.compile(pattern)
        assertSame(a, SimpleDateFormatPatternCore.compile(pattern))
        formatter.applyPattern(pattern)
        val source = getItems.invoke(formatter)
        assertSame(source, getItems.invoke(Source(pattern, ULocale.ROOT)))
        compare("yyyy'open ms年")
        compare("mm''ss")
    }

    @Test fun patternTranslationAndFieldUnitLevels() {
        val translate = sourceClass.getDeclaredMethod("translatePattern", String::class.java, String::class.java, String::class.java).apply { isAccessible = true }
        val ignored = sourceClass.getDeclaredMethod("isFieldUnitIgnored", String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val level = sourceClass.getDeclaredMethod("getLevelFromChar", Char::class.javaPrimitiveType).apply { isAccessible = true }
        for (code in 0..65535) assertEquals(level.invoke(null, code.toChar()), SimpleDateFormatPatternCore.level(code.toChar()), "$code")
        fun result(block: () -> Any?): Any? = try { block() } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException
            if (cause is VirtualMachineError) throw cause
            if (cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name + ":" + cause.message
        } catch (e: IndexOutOfBoundsException) { "bounds" } catch (e: IllegalArgumentException) { e.javaClass.name + ":" + e.message }
        val patterns = listOf("", "'", "''", "yyyy-MM-dd HH:mm:ss", "'yyyy' MM 'ss", "年 yyyy 'm'", "jjj BBB cc", "y''M", "'y''M'", "x'.'X") + ('A'..'z').map { it.toString().repeat(3) }
        for (pattern in patterns) {
            for (field in -1..24) assertEquals(result { ignored.invoke(null, pattern, field) }, result { SimpleDateFormatPatternCore.isFieldUnitIgnored(pattern, field) }, "$pattern/$field")
            for (from in listOf("GyMdkHmsSEDFwWahKzYeugAZvcLQqVUOXxrbB", "yyMM", "", "yM")) for (to in listOf("", "abc", from.reversed(), from + "extra")) {
                assertEquals(result { translate.invoke(formatter, pattern, from, to) }, result { SimpleDateFormatPatternCore.translate(pattern, from, to) }, "$pattern/$from/$to")
            }
        }
    }
}

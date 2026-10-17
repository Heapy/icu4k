package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class MessageFormatTest {
    @Test fun arraysMapsNestedPluralSelectAndModernNumbers() {
        val format = MessageFormat("{who}: {n,plural,one{# item} other{# items}}", ULocale.US)
        assertEquals("A: 2 items", format.format(mapOf("who" to "A", "n" to 2)))
        assertTrue(format.usesNamedArguments())
        assertEquals(setOf("who", "n"), format.getArgumentNames())
        assertEquals("{who}: {n}", MessageFormat("{who}: {n}", ULocale.US).format(emptyMap<String, Any?>()))
        assertEquals("3", MessageFormat("{0,number,::precision-integer}", ULocale.US).format(arrayOf(3.2)))
        assertIs<LocalizedNumberFormatterAsFormat>(MessageFormat("{0,number,::precision-integer}", ULocale.US).getFormats()[0])
        assertEquals("yes", MessageFormat("{0,select,a{yes} other{no}}", ULocale.US).format(arrayOf("a")))
    }
    @Test fun stockNumbersDatesAndExplicitParsing() {
        val format = MessageFormat("x{0,number,000}!", ULocale.US)
        val pos = ParsePosition(0)
        assertEquals("12", format.parse("x012!tail", pos)!![0].toString())
        assertEquals(5, pos.index)
        assertEquals("x012!", format.format(arrayOf(12)))
        assertEquals("1,234", MessageFormat("{0}", ULocale.US).format(arrayOf(1234)))
        val named = MessageFormat("{name}:{n,number}", ULocale.US)
        assertEquals("A", named.parseToMap("A:12")!!["name"])
        assertEquals("12", named.parseToMap("A:12")!!["n"].toString())
        assertFailsWith<IllegalArgumentException> { named.parse("A:12") }
    }
    @Test fun customCacheCloneIdentityAndLocaleFailureOrder() {
        val format = MessageFormat("{0} {0}", ULocale.US)
        val custom = DecimalFormat("000", DecimalFormatSymbols(ULocale.US))
        format.setFormatByArgumentIndex(0, custom)
        assertSame(custom, format.getFormats()[0])
        assertSame(custom, format.getFormats()[1])
        assertFailsWith<IllegalStateException> { format.toPattern() }
        assertFailsWith<IllegalStateException> { format.setLocale(ULocale.FRANCE) }
        assertEquals(ULocale.US, format.getULocale())
        val copy = format.clone()
        assertEquals(format, copy)
        assertSame(custom, copy.getFormats()[0])
        copy.setFormat(0, null)
        assertSame(custom, format.getFormats()[0])
        custom.setPositivePrefix("+")
        assertEquals("+012 +012", format.format(arrayOf(12)))
        format.applyPattern("{0,number}")
        format.setLocale(ULocale.FRANCE)
        assertEquals(ULocale.FRANCE, format.getULocale())
        assertEquals("1\u202f234", format.format(arrayOf(1234)))
    }
    @Test fun failedPatternsResetStateAndModeIsRetained() {
        val format = MessageFormat("{0}", ULocale.US)
        format.applyPattern("{0}", MessagePattern.ApostropheMode.DOUBLE_REQUIRED)
        assertFailsWith<IllegalArgumentException> { format.applyPattern("{") }
        assertEquals("", format.toPattern())
        assertEquals(MessagePattern.ApostropheMode.DOUBLE_REQUIRED, format.getApostropheMode())
        assertFailsWith<IndexOutOfBoundsException> { format.getFormats() }
        format.applyPattern("ok")
        assertEquals("ok", format.format(arrayOf<Any?>()))
        assertEquals("Don''t '{x}'", MessageFormat.autoQuoteApostrophe("Don't '{x}'"))
    }
    @Test fun attributedArgumentsAndVirtualParseDispatch() {
        val iterator = MessageFormat("Hello {name}!", ULocale.US).formatToCharacterIterator(mapOf("name" to "A"))
        iterator.setIndex(6)
        assertEquals("name", iterator.getAttribute(MessageFormat.Field.ARGUMENT))
        var calls = 0
        val format = object : MessageFormat("{0}", ULocale.US) {
            override fun parse(source: String?, position: ParsePosition?): Array<Any?>? { calls++; position!!.index = 1; return arrayOf("custom") }
        }
        assertEquals("custom", format.parse("x")!![0])
        assertEquals("custom", (format.parseObject("x", ParsePosition(0)) as Array<*>)[0])
        assertEquals(2, calls)
    }
}

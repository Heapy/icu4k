package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternGeneratorTest {
    @Test fun localeFactoriesPatternsAndDateFormatUseTheSharedCache() {
        val locale = ULocale("en_US@icu4kcase=publiccache")
        val cached = DateTimePatternGenerator.getFrozenInstance(locale)
        assertTrue(cached.isFrozen())
        assertSame(cached, DateTimePatternGenerator.getFrozenInstance(locale))
        val instance = DateTimePatternGenerator.getInstance(locale)
        assertFalse(cached.isFrozen()); assertFalse(instance.isFrozen())
        assertEquals("MMM d, y", instance.getBestPattern("yMMMd"))
        cached.addPattern("yyyy 'CACHE' MMMM dd", true, DateTimePatternGenerator.PatternInfo())
        assertEquals(cached.getBestPattern("yyyyMMMMdd"), (DateFormat.getInstanceForSkeleton("yyyyMMMMdd", locale) as SimpleDateFormat).toPattern())
        assertNotSame(cached, DateTimePatternGenerator.getInstanceNoStdPat(locale))
    }
    @Test fun clonesKeepSourceSharedFieldNamesAndIndependentPatternMaps() {
        val generator = DateTimePatternGenerator.getEmptyInstance()
        generator.addPattern("yyyy-MM-dd", true, DateTimePatternGenerator.PatternInfo())
        generator.freeze()
        val copy = generator.clone()
        assertTrue(generator.isFrozen()); assertFalse(copy.isFrozen())
        copy.setAppendItemName(DateTimePatternGenerator.YEAR, "shared year")
        assertEquals("shared year", generator.getAppendItemName(DateTimePatternGenerator.YEAR))
        copy.setAppendItemFormat(DateTimePatternGenerator.YEAR, "{0}/{1}")
        assertNotEquals(copy.getAppendItemFormat(1), generator.getAppendItemFormat(1))
        copy.addPattern("dd/MM/yyyy", true, DateTimePatternGenerator.PatternInfo())
        assertEquals("yyyy-MM-dd", generator.getSkeletons(null)["yyyyMMdd"])
        generator.setDefaultHourFormatChar('K') // Source permits this on a frozen object.
        assertEquals(DateFormat.HourCycle.HOUR_CYCLE_11, generator.getDefaultHourCycle())
        assertFailsWith<IllegalArgumentException> { generator.setDateTimeFormat(4, null) }
        assertFailsWith<UnsupportedOperationException> { generator.setAppendItemFormat(-1, null) }
        val thaw = generator.cloneAsThawed()
        assertFalse(generator.isFrozen()); assertFalse(thaw.isFrozen())
    }
    @Test fun nestedParserExposesLivePublicVariableFieldsAndVirtualHooks() {
        val parser = DateTimePatternGenerator.FormatParser().set("yyyy 'de' MM HH:mm")
        val items = parser.getItems()
        assertIs<DateTimePatternGenerator.VariableField>(items[0])
        assertTrue(parser.hasDateAndTimeFields())
        items.add(" literal")
        assertTrue(parser.toString().endsWith(" 'literal'"))
        assertSame(items, parser.set("HH:mm").getItems())
        assertFalse(parser.hasDateAndTimeFields())
        val custom = object : DateTimePatternGenerator.FormatParser() {
            override fun toString(start: Int, limit: Int): String = "slice:$start:$limit"
        }
        custom.set("y")
        assertEquals("slice:0:1", custom.toString())
        assertEquals("99", DateTimePatternGenerator.VariableField.getCanonicalCode(99))
        assertTrue(DateTimePatternGenerator.isSingleField("yyyy"))
        assertFailsWith<IndexOutOfBoundsException> { DateTimePatternGenerator.isSingleField("") }
    }
    @Test fun thawUsesVirtualCloneAndLeavesItsReturnedObjectFrozen() {
        val returned = DateTimePatternGenerator.getEmptyInstance().freeze()
        val original = object : DateTimePatternGenerator() {
            override fun clone(): DateTimePatternGenerator = returned
        }.freeze()
        assertSame(returned, original.cloneAsThawed())
        assertFalse(original.isFrozen()); assertTrue(returned.isFrozen())
    }
    @Test fun publicSubclassSelfCallsAndNativeCloneHook() {
        val calls = mutableListOf<String>()
        class Custom : DateTimePatternGenerator() {
            var extra = 42
            override fun getDefaultHourFormatChar(): Char = 'k'
            override fun getDateTimeFormat(style: Int): String { calls.add("glue:$style"); return "{1}/{0}" }
            override fun replaceFieldTypes(pattern: String?, skeleton: String?, options: Int): String = "virtual"
            override fun cloneNative(): DateTimePatternGenerator = Custom().also { it.extra = extra }
        }
        val custom = Custom()
        assertEquals(DateFormat.HourCycle.HOUR_CYCLE_24, custom.getDefaultHourCycle())
        assertEquals("{1}/{0}", custom.getDateTimeFormat())
        assertEquals(listOf("glue:2"), calls)
        assertEquals("virtual", custom.replaceFieldTypes("y", "yy"))
        val clone = custom.clone()
        assertIs<Custom>(clone); assertEquals(42, clone.extra)
    }
}

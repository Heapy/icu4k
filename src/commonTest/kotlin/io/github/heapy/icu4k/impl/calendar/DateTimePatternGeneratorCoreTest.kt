package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.text.DateTimePatternGenerator

import kotlin.test.*

class DateTimePatternGeneratorCoreTest {
    @Test fun standaloneIcu4jEmptyAndCustomPatternSnapshots() {
        val core=DateTimePatternGeneratorCore.empty()
        val empty=listOf("" to "","yMd" to "y ├'F3': M┤ ├'F7': d┤","Hm" to "H ├'F12': m┤","HmsSSS" to "H ├'F'12: m┤ ├'F'13: s?SSS┤","yMMMMEEEEdHm" to "y ├'F7': d┤ ├'F6': EEEE┤ ├'F3': MMMM┤ H ├'F12': m┤","jmm" to "H ├'F12': m┤","JJmm" to "H ├'F12': m┤")
        for((request,pattern) in empty) assertEquals(pattern,core.getBestPattern(request),request)
        val info=DateTimePatternGenerator.PatternInfo()
        for(pattern in listOf("yyyy-MM-dd","HH:mm","HH:mm:ss","EEEE, d MMMM y")) core.addPattern(pattern,true,info)
        core.setDecimal(".")
        for((request,pattern) in listOf("yMd" to "y-M-d","yyyyMMdd" to "yyyy-MM-dd","yMMMMEEEEdHm" to "EEEE, d MMMM y HH:mm","HmsSSS" to "HH:mm:ss.SSS","mSSS" to "m ├'F'13: s.SSS┤","jjjmm" to "HH:mm","JJmm" to "HH:mm")) assertEquals(pattern,core.getBestPattern(request),request)
    }
    @Test fun explicitSkeletonConflictPriorityAndNullInfoFailure() {
        val core=DateTimePatternGeneratorCore.empty(); val info=DateTimePatternGenerator.PatternInfo()
        val patterns=listOf("y-M-d","yy/MM/dd","yy/MM/dd","d MMM y","M/d/y","M-d-y")
        val skeletons=listOf(null,null,null,"yMd","yMd","yMd"); val overrides=listOf(true,false,true,true,true,false)
        val expected=listOf(0 to "",1 to "y-M-d",0 to "",0 to "",2 to "d MMM y",1 to "d MMM y")
        for(i in patterns.indices) {
            assertSame(core,core.addPatternWithSkeleton(patterns[i],skeletons[i],overrides[i],info))
            assertEquals(expected[i],info.status to info.conflictingPattern)
        }
        assertEquals("d MMM y",core.getSkeletons(null)["yMd"])
        val before=core.getSkeletons(null).toMap()
        assertFailsWith<NullPointerException> { core.addPattern("EEEE d",true,null) }
        assertEquals(before,core.getSkeletons(null))
    }
    @Test fun lengthOptionsAndStyleSensitiveDateTimeGlue() {
        val core=DateTimePatternGeneratorCore.empty(); val info=DateTimePatternGenerator.PatternInfo()
        core.addPatternWithSkeleton("HH:mm:ss","Hms",true,info)
        assertEquals("HH:mm:ss",core.getBestPattern("Hms")); assertEquals("HH:mm:ss",core.getBestPattern("Hms",0xffff))
        val derived=DateTimePatternGeneratorCore.empty().apply { addPattern("HH:mm:ss",true,DateTimePatternGenerator.PatternInfo()) }
        assertEquals("H:m:s",derived.getBestPattern("Hms",0xffff))
        for(style in 0..3) core.setDateTimeFormat(style,"{1} '$style' {0}")
        for((request,style) in listOf("yMMMMEEEEdH" to 0,"yMMMMdH" to 1,"yMMMdH" to 2,"yMdH" to 3)) assertTrue(core.getBestPattern(request).contains("'$style'"),request)
        assertEquals("HH:mm:ss",core.replaceFieldTypes("HH:mm:ss","Hms"))
        assertEquals("H:m:s",core.replaceFieldTypes("HH:mm:ss","Hms",0xffff))
    }
    @Test fun hourMetacharactersRequireActualAllowedFormats() {
        val core=DateTimePatternGeneratorCore.empty()
        assertFailsWith<NullPointerException> { core.getBestPattern("Cmm") }
        core.setAllowedHourFormats(emptyArray()); assertFailsWith<IndexOutOfBoundsException> { core.getBestPattern("Cmm") }
        core.setAllowedHourFormats(arrayOf("")); assertFailsWith<IndexOutOfBoundsException> { core.getBestPattern("Cmm") }
        core.setAllowedHourFormats(arrayOf("hB")); core.setDefaultHourFormatChar('h')
        assertTrue(core.getBestPattern("C").contains('B')); assertTrue(core.getBestPattern("j").contains('a'))
        assertFalse(core.getBestPattern("J").contains('a'))
        for((hour,cycle) in listOf('h' to "HOUR_CYCLE_12",'H' to "HOUR_CYCLE_23",'k' to "HOUR_CYCLE_24",'K' to "HOUR_CYCLE_11")) { core.setDefaultHourFormatChar(hour); assertEquals(cycle,core.getDefaultHourCycle().name) }
        core.setDefaultHourFormatChar('x'); assertFailsWith<AssertionError> { core.getDefaultHourCycle() }
    }
    @Test fun frozenValidationOrderAndSourceReceiverThawBehavior() {
        val core=DateTimePatternGeneratorCore.empty().freeze()
        assertFailsWith<UnsupportedOperationException> { core.addPattern(null,false,null) }
        assertFailsWith<IllegalArgumentException> { core.setDateTimeFormat(4,null) }
        assertFailsWith<UnsupportedOperationException> { core.setAppendItemFormat(-1,null) }
        core.setDefaultHourFormatChar('K'); assertEquals('K',core.getDefaultHourFormatChar())
        val thaw=core.cloneAsThawed(); assertNotSame(core,thaw); assertFalse(thaw.isFrozen()); assertFalse(core.isFrozen())
        // ICU4J 78.3 unfreezes the receiver as well; tracked as ICU4J-007.
        core.freeze(); val clone=core.copy(); assertTrue(core.isFrozen()); assertFalse(clone.isFrozen())
        clone.setAppendItemName(1,"shared year"); assertEquals("shared year",core.getAppendItemName(1))
        clone.setAppendItemFormat(1,"{0}/{1}"); assertNotEquals(clone.getAppendItemFormat(1),core.getAppendItemFormat(1))
        clone.setDateTimeFormat(0,"{0}/{1}"); assertNotEquals(clone.getDateTimeFormat(0),core.getDateTimeFormat(0))
        assertEquals("",clone.getFieldDisplayName(-1,null)); assertFailsWith<NullPointerException> { clone.getFieldDisplayName(0,null) }
        clone.setAppendItemName(-1,"ignored")
    }
    @Test fun outputCollectionsAliasesAndStructuralMutationFailure() {
        val core=DateTimePatternGeneratorCore.empty(); val info=DateTimePatternGenerator.PatternInfo()
        core.addPattern("y-M-d",true,info)
        val output=linkedMapOf<String,String?>("existing" to "value")
        assertSame(output,core.getSkeletons(output)); assertEquals("value",output["existing"]); assertEquals("y-M-d",output["yMd"])
        val bases=linkedSetOf("existing"); assertSame(bases,core.getBaseSkeletons(bases)); assertTrue("existing" in bases)
        val mapped=object: AbstractMutableMap<String,String?>() {
            private val storage=linkedMapOf<String,String?>()
            override val entries get()=storage.entries
            override fun put(key: String,value: String?): String? { core.addPattern("G yyyy QQQQ",true,info); return storage.put(key,value) }
        }
        assertFailsWith<ConcurrentModificationException> { core.getSkeletons(mapped) }; assertEquals(1,mapped.size)
    }
    @Test fun orderedStorageKeepsFirstComparatorEqualKeyAndShallowCopy() {
        class Key(val value: String): Comparable<Key> { override fun compareTo(other: Key): Int=value.lowercase().compareTo(other.value.lowercase()) }
        val map=PatternOrderedMap<Key,Any>(); val first=Key("Year"); val equal=Key("YEAR"); val value=Any()
        map[first]=Any(); map[equal]=value
        assertSame(first,map.single().first); assertSame(value,map[first]); assertEquals(1,map.count())
        val clone=map.copy(); assertSame(first,clone.single().first); assertSame(value,clone.single().second)
        val iterator=map.iterator(); map[Key("second")]=Any(); assertTrue(iterator.hasNext()); assertFailsWith<ConcurrentModificationException> { iterator.next() }
        val ended=PatternOrderedMap<Int,String>().iterator(); assertFalse(ended.hasNext()); assertFailsWith<NoSuchElementException> { ended.next() }
    }
    @Test fun baseSkeletonOutputReceivesTheLiveMutableKeySet() {
        val core=DateTimePatternGeneratorCore.empty()
        var captured: Collection<String>?=null
        val output=object: AbstractMutableSet<String>() {
            private val values=linkedSetOf<String>()
            override val size get()=values.size
            override fun iterator()=values.iterator()
            override fun add(element: String)=values.add(element)
            override fun addAll(elements: Collection<String>): Boolean { captured=elements; return values.addAll(elements) }
        }
        assertSame(output,core.getBaseSkeletons(output))
        val view=captured!! as MutableSet<String>
        assertTrue(view.remove("y")); assertFalse("y" in core.getBaseSkeletons(null))
        core.addPattern("y-M-d",true,DateTimePatternGenerator.PatternInfo()); assertTrue("yMd" in view)
        val iterator=view.iterator(); val first=iterator.next(); iterator.remove(); assertFalse(first in view)
        assertFailsWith<IllegalStateException> { iterator.remove() }
        core.getBaseSkeletons(output); assertSame(view,captured)
    }

}

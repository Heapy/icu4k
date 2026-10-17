package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class MessageFormatStyleCoreTest {
    private class Owner : MessageFormatStyleCore.Owner {
        override val locale=ULocale.US
        val calls=ArrayList<String>()
        override fun dateStyle(date: Boolean,style: Int,locale: ULocale?): Any { val call="$date:$style:$locale"; calls.add(call); return call }
        override fun dateSkeleton(skeleton: String,locale: ULocale?): Any { calls.add("skeleton:$skeleton"); return skeleton }
        override fun datePattern(pattern: String,locale: ULocale?): Any { calls.add("pattern:$pattern"); return pattern }
    }
    @Test fun numericPatternsSkeletonsAndRuleSetsUseRealCommonFormats() {
        val core=MessageFormatStyleCore(Owner())
        assertEquals("12",(core.create(" NUMBER "," INTEGER ") as NumberFormat).format(12.4))
        assertEquals("0012",(core.create("number","0000") as NumberFormat).format(12L))
        assertEquals("12",(core.create("number","\u200e:: precision-integer") as LocalizedNumberFormatterAsFormat).format(12.4))
        assertEquals("twelve",(core.create("spellout","%invalid") as RuleBasedNumberFormat).format(12L))
        assertEquals("Unknown format type \"unknown\"",assertFailsWith<IllegalArgumentException> { core.create("unknown",null) }.message)
    }
    @Test fun dateStylesAndSkeletonSignalsKeepWhitespaceRules() {
        val owner=Owner(); val core=MessageFormatStyleCore(owner)
        core.create("DATE","FULL"); core.create("time"," medium ")
        core.create("date","\u200e :: yMd"); core.create("time","\u00a0::Hm")
        assertEquals(listOf("true:0:en_US","false:2:en_US","skeleton: yMd","pattern:\u00a0::Hm"),owner.calls)
        assertEquals(-1,MessageFormatStyleCore.findKeyword("\u00a0number",arrayOf("number")))
        assertEquals(0,MessageFormatStyleCore.findKeyword("\u200fNUMBER\t",arrayOf("number")))
    }
}

// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.text

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RbnfScannerProviderTest {
    @Test fun primaryScanningExtraRulesAndCachedFailures() {
        val p=RbnfScannerProviderImpl()
        val extras="&[last primary ignorable] = ' ' = '-'"
        val s=p.get(ULocale.ENGLISH,extras)!!
        assertSame(s,p.get(ULocale.ENGLISH,extras))
        assertTrue(s.allIgnorable(" -")); assertFalse(s.allIgnorable("a"))
        assertEquals(4,s.prefixLength(" É-x!","ex"))
        assertContentEquals(intArrayOf(2,3),s.findText("zzÉ-x!","ex",0))
        val bad=p.get(ULocale.ENGLISH,"&")!!
        assertSame(bad,p.get(ULocale.ENGLISH,"&"))
        assertFailsWith<NullPointerException> { bad.allIgnorable("a") }
        assertContentEquals(intArrayOf(-1,0),bad.findText("","a",0))
        val collision=RbnfScannerProviderImpl()
        assertSame(collision.get(ULocale.ENGLISH,null),collision.get(ULocale.ENGLISH,"null"))
    }
    @Test fun englishLenientParsingDiscoversCommonProvider() {
        val f=RuleBasedNumberFormat(ULocale.ENGLISH,RuleBasedNumberFormat.SPELLOUT)
        f.setLenientParseMode(true)
        assertNotNull(f.getLenientScannerProvider())
        for(text in listOf("two hundred fifty-five","two hundred fifty five","TWO HUNDRED FIFTY-FIVE","twohundredfiftyfive","2 hundred fifty-5")) {
            val pos=ParsePosition(0)
            assertEquals(255L,f.parse(text,pos)!!.toLong(),text)
            assertEquals(text.length,pos.index,text)
        }
    }
}

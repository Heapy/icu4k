// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

internal class RuleTestSink(val calls: MutableList<String> = mutableListOf(),val failAt: Int=-1): CollationRuleParser.Sink() {
    val retained=mutableListOf<CharSequence>()
    private fun record(event: String) { calls.add(event);if(calls.size==failAt) throw IllegalArgumentException("sink") }
    override fun addReset(strength: Int,str: CharSequence) { retained.add(str);record("reset|$strength|$str") }
    override fun addRelation(strength: Int,prefix: CharSequence,str: CharSequence,extension: CharSequence) {
        retained.add(prefix);retained.add(str);retained.add(extension);record("relation|$strength|$prefix|$str|$extension")
    }
    override fun optimize(set: UnicodeSet) = record("optimize|"+set.toPattern(false))
    override fun suppressContractions(set: UnicodeSet) = record("suppress|"+set.toPattern(false))
}
class CollationRuleParserTest {
    @Test fun relationsQuotesStarredRangesAndSettings() {
        val sink=RuleTestSink();val p=CollationRuleParser(CollationRoot.getData());p.setSink(sink);val settings=CollationSettings()
        p.parse("[strength 4][alternate shifted][caseFirst upper][numericOrdering on] &a < x|b / c << 'd e' <<<* f-h",settings)
        assertEquals(listOf("reset|15|a","relation|0|x|b|c","relation|1||d e|","relation|2||f|","relation|2||g|","relation|2||h|"),sink.calls)
        assertEquals(3,settings.getStrength());assertTrue(settings.isNumeric());assertTrue(settings.getAlternateHandling());assertEquals(CollationSettings.CASE_FIRST_AND_UPPER_MASK,settings.getCaseFirst())
    }
    @Test fun specialResetsSetCallbacksAndImports() {
        val sink=RuleTestSink();val p=CollationRuleParser(CollationRoot.getData());p.setSink(sink)
        p.setImporter { locale,type -> assertEquals("de",locale);assertEquals("phonebook",type);"&a < ä" }
        p.parse("[optimize [a-c]][suppressContractions [x-z]][import de-u-co-phonebk] &[top] < z",CollationSettings())
        assertEquals("optimize|[a-c]",sink.calls[0]);assertEquals("suppress|[x-z]",sink.calls[1]);assertTrue(sink.calls.contains("relation|0||ä|"))
        assertTrue(sink.calls.contains("reset|15|"+charArrayOf(0xfffe.toChar(),0x2809.toChar()).concatToString()))
    }
    @Test fun wrappedCallbackFailuresKeepOffsetAndCause() {
        val p=CollationRuleParser(CollationRoot.getData());p.setSink(RuleTestSink(failAt=2))
        val e=assertFailsWith<ParseException> { p.parse("&a < b",CollationSettings()) }
        assertEquals(3,e.getErrorOffset());assertTrue(e.message!!.startsWith("adding relation failed: sink at index 3"));assertIs<IllegalArgumentException>(e.cause);assertEquals("sink",e.cause!!.message)
        val malformed=charArrayOf('&','a','<',0xd800.toChar()).concatToString()
        assertTrue(assertFailsWith<ParseException> { p.parse(malformed,CollationSettings()) }.message!!.startsWith("string contains an unpaired surrogate"))
    }
}

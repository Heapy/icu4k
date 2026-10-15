// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.util.ParseException
import kotlin.test.*

class CollationBuilderTest {
    private fun compare(t: CollationTailoring,a: String,b: String): Int = CollationCompare.compareUpToQuaternary(FCDUTF16CollationIterator(t.data,false,a,0),FCDUTF16CollationIterator(t.data,false,b,0),t.settings!!.readOnly())
    @Test fun ruleCompilerBuildsAllWeightLevels() {
        val rule="[strength 4]&a < b < c << d <<< e <<<< f"
        val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule)
        for((a,b) in listOf("a" to "b","b" to "c","c" to "d","d" to "e","e" to "f")) assertEquals(-1,compare(t,a,b),"$a/$b")
        assertSame(t.data,t.ownedData);assertSame(CollationRoot.getData(),t.data!!.base);assertTrue(t.data!!.unsafeBackwardSet!!.isFrozen());assertEquals(rule,t.getRules())
    }
    @Test fun canonicalClosureContractionsPrefixesAndImports() {
        val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&a < é < x|ch / d")
        assertEquals(0,compare(t,"é","e\u0301"));assertEquals(-1,compare(t,"a","é"))
        val imported=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("[import de-u-co-phonebk]")
        assertEquals(0,compare(imported,"ä","a\u0308"));assertTrue(imported.data!==CollationRoot.getData())
    }
    @Test fun settingsOnlyAliasesRootAndFailuresAreReported() {
        val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("[strength 1]")
        assertSame(CollationRoot.getData(),t.data);assertNull(t.ownedData);assertEquals(0,t.settings!!.readOnly()!!.getStrength())
        assertFailsWith<UnsupportedOperationException> { CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&a<<<<b<<<<c<<<<d<<<<e") }
        assertFailsWith<ParseException> { CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&[before 1]a << b") }
    }
}

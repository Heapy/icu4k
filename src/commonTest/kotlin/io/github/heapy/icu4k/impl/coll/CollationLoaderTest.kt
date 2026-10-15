// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CollationLoaderTest {
    @Test fun rootFallbackAndTailoringsAreAvailableWithoutHostIcu() {
        val out=Output<ULocale>()
        assertSame(CollationRoot.getRoot(),CollationLoader.loadTailoring(ULocale.ROOT,out));assertEquals(ULocale.ROOT,out.value)
        assertSame(CollationRoot.getRoot(),CollationLoader.loadTailoring(ULocale("zz_ZZ"),out));assertEquals(ULocale.ROOT,out.value)
        for(name in listOf("de@collation=phonebook","es@collation=traditional","zh","zh_Hant","ko@collation=searchjl","th","sv","ar")) {
            val t=CollationLoader.loadTailoring(ULocale(name),out)
            assertNotNull(t.data);assertNotNull(t.settings!!.readOnly());assertNotNull(out.value)
            assertEquals(CollationRoot.getRoot().getUCAVersion(),t.getUCAVersion())
        }
    }
    @Test fun rulesAreLazyAndTypeLookupIsAsciiCaseInsensitive() {
        val out=Output<ULocale>()
        val a=CollationLoader.loadTailoring(ULocale("de@collation=phonebook"),out)
        val b=CollationLoader.loadTailoring(ULocale("de@collation=PHONEBOOK"),out)
        assertEquals(a.version,b.version);assertEquals(a.getRules(),b.getRules())
        assertTrue(a.getRules().isNotEmpty())
        assertEquals(a.getRules(),CollationLoader.loadRules(ULocale("de"),"PHONEBOOK"))
        assertTrue(CollationLoader.getRootRules().isNotEmpty());assertSame(CollationLoader.getRootRules(),CollationLoader.getRootRules())
    }
}

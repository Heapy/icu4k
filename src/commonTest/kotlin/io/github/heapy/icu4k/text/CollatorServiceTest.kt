// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CollatorServiceTest {
    @Test fun localesKeywordsAndIndependentInstances() {
        val a=Collator.getInstance(ULocale("de@collation=phonebook")) as RuleBasedCollator
        val b=Collator.getInstance(ULocale("de@collation=phonebook")) as RuleBasedCollator
        assertNotSame(a,b); assertEquals(a,b)
        a.setStrength(0); assertEquals(2,b.getStrength())
        val numeric=Collator.getInstance(ULocale("en@colNumeric=yes"))
        assertEquals(-1,numeric.compare("2","10"))
        assertEquals(Collator.getInstance(ULocale.ROOT),Collator.getInstance(ULocale("zz_ZZ")))
        assertTrue(Collator.getAvailableULocales().isNotEmpty())
        assertContentEquals(arrayOf("collation"),Collator.getKeywords())
        assertTrue(Collator.getKeywordValues("collation").contains("phonebook"))
        assertTrue(Collator.getKeywordValuesForLocale("ignored",ULocale("de"),false).contains("phonebook"))
    }
    @Test fun registerInstanceAndFactoryVisibility() {
        val locale=ULocale("qaa")
        val original=RuleBasedCollator("&a<z")
        val key=Collator.registerInstance(original,locale)
        try {
            val c=Collator.getInstance(locale)
            assertNotSame(original,c); assertEquals(-1,c.compare("z","b"))
            assertEquals(locale,c.getLocale(ULocale.ACTUAL_LOCALE))
            assertTrue(Collator.getAvailableULocales().any { it.getName()=="qaa" })
        } finally { assertTrue(Collator.unregister(key)) }
        assertFalse(Collator.unregister(key))
        val factory=object : Collator.CollatorFactory() {
            override fun visible()=false
            override fun getSupportedLocaleIDs()=setOf("qaa")
            override fun createCollator(loc: ULocale?): Collator? = RuleBasedCollator("&a<z")
        }
        val registered=Collator.registerFactory(factory)
        try {
            assertEquals(-1,Collator.getInstance(ULocale("qaa_BB")).compare("z","b"))
            assertFalse(Collator.getAvailableULocales().any { it.getName()=="qaa" })
            assertNull(factory.getDisplayName(locale,ULocale.ENGLISH))
        } finally { assertTrue(Collator.unregister(registered)) }
    }
}

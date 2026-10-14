package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUData
import kotlin.test.*

class ULocaleCanonicalizationTest {
    @Test fun canonicalAliasesPreserveKeywordsAndExplicitFields() {
        for ((input, expected) in listOf("iw_IL" to "he_IL", "in_ID" to "id_ID", "sh_RS" to "sr_Latn_RS",
            "en_Qaai_BU" to "en_Zinh_MM", "art__LOJBAN" to "jbo", "zh_MIN_NAN" to "nan", "nb_NO_NY" to "nn_NO")) {
            assertEquals(expected, ULocale.canonicalize(input), input)
            assertEquals(expected, ULocale.createCanonical(input).getName())
            assertEquals(expected, ULocale.createCanonical(ULocale(input)).getName())
        }
        assertEquals("he_IL@calendar=japanese;currency=USD", ULocale.canonicalize("iw_IL@currency=USD;calendar=japanese"))
        assertEquals("sr_Cyrl_RS", ULocale.canonicalize("sh_Cyrl_RS"))
        assertEquals("uk_UA", ULocale.canonicalize("uk_SU"))
        assertEquals("ru_RU", ULocale.canonicalize("ru_SU"))
        assertEquals("hy_AM", ULocale.canonicalize("hy_SU"))
    }
    @Test fun metadataSubdivisionMappingsAndTransformedLanguage() {
        val aliases = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata")["alias"]["subdivision"]
        for (index in 0 until aliases.getSize()) {
            val item = aliases[index]
            val from = item.getKey()!!
            var to = item["replacement"].getString().split(' ')[0]
            if (to.length == 2) to += "zzzz"
            assertEquals(to, ULocale.createCanonical("en@sd=$from").getKeywordValue("sd"), from)
            assertEquals(to, ULocale.createCanonical("en@rg=$from").getKeywordValue("rg"), from)
        }
        assertEquals("he-il", ULocale.createCanonical("en@t=iw-il").getKeywordValue("t"))
        assertEquals("d0-charname-m0-ungegn", ULocale.createCanonical("en@t=m0-ungegn-d0-name").getKeywordValue("t"))
    }
    @Test fun normalizationAndNullBoundaries() {
        assertEquals("", ULocale.canonicalize(""))
        assertEquals("en_US", ULocale.canonicalize("EN-us"))
        assertEquals("zzzz_Zzzz_QQ", ULocale.canonicalize("zzzz_Zzzz_QQ"))
        assertFailsWith<NullPointerException> { ULocale.canonicalize(null) }
        assertFailsWith<NullPointerException> { ULocale.createCanonical(null as String?) }
        assertFailsWith<NullPointerException> { ULocale.createCanonical(null as ULocale?) }
    }
}

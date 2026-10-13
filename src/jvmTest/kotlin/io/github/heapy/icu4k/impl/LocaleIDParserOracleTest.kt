package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.LocaleIDParser as J
import com.ibm.icu.impl.LocaleIDs as JIDs
import kotlin.random.Random
import kotlin.test.*

class LocaleIDParserOracleTest {
    private fun compareCall(j: () -> Any?, k: () -> Any?, label: String = "") {
        val expected = runCatching(j); val actual = runCatching(k)
        if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), label)
        else {
            val je = expected.exceptionOrNull()!!; val ke = actual.exceptionOrNull() ?: fail("Expected $je: $label")
            if (je is IndexOutOfBoundsException) assertIs<IndexOutOfBoundsException>(ke)
            else { assertEquals(je.javaClass, ke.javaClass, label); assertEquals(je.message, ke.message, label) }
        }
    }
    @Test fun allIsoTablesMappingsObsoleteCodesAndArrayCopiesMatchOriginal() {
        assertContentEquals(JIDs.getISOCountries(), LocaleIDs.getISOCountries()); assertContentEquals(JIDs.getISOLanguages(), LocaleIDs.getISOLanguages())
        val values = mutableSetOf("", "unknown", "EN", "us")
        for (field in JIDs::class.java.declaredFields.filter { it.type == Array<String>::class.java }) {
            field.isAccessible = true; values.addAll((field.get(null) as Array<*>).map { it as String })
        }
        for (s in values) {
            assertEquals(JIDs.getISO3Country(s), LocaleIDs.getISO3Country(s), s); assertEquals(JIDs.getISO3Language(s), LocaleIDs.getISO3Language(s), s)
            assertEquals(JIDs.threeToTwoLetterLanguage(s), LocaleIDs.threeToTwoLetterLanguage(s), s)
            assertEquals(JIDs.threeToTwoLetterRegion(s), LocaleIDs.threeToTwoLetterRegion(s), s)
            assertEquals(JIDs.getCurrentCountryID(s), LocaleIDs.getCurrentCountryID(s), s)
            assertEquals(JIDs.getCurrentLanguageID(s), LocaleIDs.getCurrentLanguageID(s), s)
        }
        LocaleIDs.getISOCountries()[0] = "changed"; LocaleIDs.getISOLanguages()[0] = "changed"
        assertContentEquals(JIDs.getISOCountries(), LocaleIDs.getISOCountries()); assertContentEquals(JIDs.getISOLanguages(), LocaleIDs.getISOLanguages())
    }
    @Test fun parserGettersPosixFormsErrorsAndCallOrdersMatchOriginal() {
        val fixtures = listOf("", "en", "ENG_usa", "zh-hANT-twn-posix", "en__posix", "x-private_US", "i-klingon", "de_DE.UTF-8@euro", "de@euro",
            "en_US@ Currency = USD ;collation=phonebook;currency=EUR", "en@a=;b=foo", "en@a=one;;b=two", "en@foo", "en@=value", "en@a=x=b",
            "en__a,b-c", "en_US_", "en____", "en@\u00a0a\u00a0=\u00a0x\u00a0", "en@\tA\r=\nB\t", "en\uffff_US@a=b", "en@a=b\ufffftail", "😀_İ_é",
            "en_US_" + "v".repeat(179), "en_US_" + "v".repeat(180), "en__" + "😀".repeat(90))
        val random = Random(812701)
        val parts = listOf("en", "eng", "USA", "Latn", "Hant", "US", "POSIX", "utf8", "euro", "a", "=", ";", "@", ".", "-", "_", "", " ", "\uffff", "😀")
        val inputs = fixtures + List(2000) { List(random.nextInt(1, 10)) { parts.random(random) }.joinToString(listOf("", "_", "-").random(random)) }
        for (input in inputs) for (canonical in listOf(false, true)) {
            val j = J(input, canonical); val k = LocaleIDParser(input, canonical)
            val methods = listOf<Pair<() -> Any?, () -> Any?>>(
                { j.name } to { k.getName() }, { j.language } to { k.getLanguage() }, { j.script } to { k.getScript() },
                { j.country } to { k.getCountry() }, { j.variant } to { k.getVariant() },
                { j.languageScriptCountryVariant.toList() } to { k.getLanguageScriptCountryVariant().toList() },
                { j.baseName } to { k.getBaseName() }, { j.keywordMap.toList() } to { k.getKeywordMap().toList() },
                { j.keywords?.asSequence()?.toList() } to { k.getKeywords()?.asSequence()?.toList() })
            for ((index, pair) in methods.withIndex()) compareCall(pair.first, pair.second, "$input/$canonical/$index")
            for (key in listOf("a", "A", "currency", " missing ")) compareCall({ j.getKeywordValue(key) }, { k.getKeywordValue(key) }, input)
            for (base in listOf("OVERRIDE", "", null)) {
                j.setBaseName(base); k.setBaseName(base)
                compareCall({ j.baseName }, { k.getBaseName() }, input); compareCall({ j.name }, { k.getName() }, input)
            }
        }
    }
    @Test fun keywordMutationLiveMapsIteratorsAndFailuresMatchOriginal() {
        for (input in listOf("en_US@b=two;a=one;c=three", "en", "en@a=one;a=two", "en@a=;b=two")) {
            val j = J(input); val k = LocaleIDParser(input)
            for (reset in listOf(false, true)) for (key in listOf("A", "new", "", "  ", "\u00a0", null)) for (value in listOf("Value", "", "  ", null)) {
                compareCall({ if (reset) j.setKeywordValue(key, value) else j.defaultKeywordValue(key, value) },
                    { if (reset) k.setKeywordValue(key, value) else k.defaultKeywordValue(key, value) }, "$input/$reset/$key/$value")
                compareCall({ j.name }, { k.getName() }); compareCall({ j.keywordMap.toList() }, { k.getKeywordMap().toList() })
            }
        }
        val j = J("en@b=two;a=one;c=three"); val k = LocaleIDParser("en@b=two;a=one;c=three")
        val jm = j.keywordMap; val km = k.getKeywordMap()
        jm["UPPER"] = "Raw"; km["UPPER"] = "Raw"
        assertEquals(j.name, k.getName()); assertEquals(jm.toList(), km.toList())
        jm.entries.first().setValue("Updated"); km.entries.first().setValue("Updated")
        assertEquals(j.name, k.getName())
        val ji = j.keywords; val ki = k.getKeywords()!!
        assertEquals(ji.next(), ki.next()); ji.remove(); ki.remove(); assertEquals(j.name, k.getName())
        val jiter = jm.entries.iterator(); val kiter = km.entries.iterator()
        jiter.next(); kiter.next(); jm["zz"] = "new"; km["zz"] = "new"
        assertFailsWith<ConcurrentModificationException> { jiter.next() }; assertFailsWith<ConcurrentModificationException> { kiter.next() }
        j.setKeywordValue(null, null); k.setKeywordValue(null, null)
        assertEquals(jm.toList(), km.toList()); assertEquals(j.name, k.getName())
        assertFailsWith<UnsupportedOperationException> { j.keywordMap["x"] = "y" }
        assertFailsWith<UnsupportedOperationException> { k.getKeywordMap()["x"] = "y" }
        compareCall({ j.keywordMap.clear() }, { k.getKeywordMap().clear() })
        compareCall({ j.keywordMap.remove("x") }, { k.getKeywordMap().remove("x") })
    }
}

package io.github.heapy.icu4k.text

import kotlin.test.*

/** Independent ICU4J 78.3 fingerprints, generated without executing the port. */
class TransliteratorPublicTest {
    private val ids = listOf(
        "Null",
        "Remove",
        "Lower",
        "Upper",
        "Title",
        "CaseFold",
        "NFC",
        "NFD",
        "NFKC",
        "NFKD",
        "FCD",
        "FCC",
        "Name",
        "Name-Any",
        "Any-Hex",
        "Any-Hex/Unicode",
        "Any-Hex/Java",
        "Any-Hex/C",
        "Any-Hex/XML",
        "Any-Hex/XML10",
        "Any-Hex/Perl",
        "Any-Hex/Plain",
        "Hex-Any",
        "Hex-Any/Unicode",
        "Hex-Any/Java",
        "Hex-Any/C",
        "Hex-Any/XML",
        "Hex-Any/XML10",
        "Hex-Any/Perl",
        "Any-BreakInternal",
        "Latin-Greek",
        "Greek-Latin",
        "Cyrillic-Latin",
        "Latin-Cyrillic",
        "Latin-ASCII",
        "Any-Latin",
        "Any-Greek",
        "Han-Latin",
        "Devanagari-Latin",
        "Latin-Devanagari",
        "el-Latin",
    )
    private val expected = intArrayOf(
        -218551903, // Null
        1649076185, // Remove
        -374056993, // Lower
        -185115485, // Upper
        -1667148051, // Title
        1197733663, // CaseFold
        -1184928678, // NFC
        -580040312, // NFD
        -351908066, // NFKC
        -20432380, // NFKD
        -561331743, // FCD
        -176880784, // FCC
        699139403, // Name
        -460546027, // Name-Any
        -823707546, // Any-Hex
        1129207347, // Any-Hex/Unicode
        681584521, // Any-Hex/Java
        1358053978, // Any-Hex/C
        2112409403, // Any-Hex/XML
        -1889586803, // Any-Hex/XML10
        -1769229598, // Any-Hex/Perl
        -1548692373, // Any-Hex/Plain
        406801660, // Hex-Any
        442855695, // Hex-Any/Unicode
        -1182469639, // Hex-Any/Java
        -901313848, // Hex-Any/C
        1720162183, // Hex-Any/XML
        -332051595, // Hex-Any/XML10
        -1326812032, // Hex-Any/Perl
        1002485034, // Any-BreakInternal
        1952026690, // Latin-Greek
        -697148256, // Greek-Latin
        -1891474, // Cyrillic-Latin
        1923894900, // Latin-Cyrillic
        688832303, // Latin-ASCII
        -1793797452, // Any-Latin
        1420896437, // Any-Greek
        -1494043730, // Han-Latin
        -1868582650, // Devanagari-Latin
        -1483214330, // Latin-Devanagari
        -2045070113, // el-Latin
    )

    private fun fingerprint(index: Int): Int {
        var hash=1
        fun add(value:Any?){hash=31*hash+(value?.toString()?.hashCode() ?: 0)}
        val inputs=listOf("aBß ΣΟΣ á a\u0301 😀 Ａ ﾊﾟ", "\\u0041 U+0042 &#x43; \\N{ LATIN CAPITAL LETTER A }", "Москва 中文 नमस्ते ภาษาไทย")
        for(direction in 0..1) {
            val trans=try {Transliterator.getInstance(ids[index],direction).also {add("ok")}}
                catch(failure:RuntimeException){add(failure::class.simpleName+":"+failure.message);continue}
            add(trans.getID());add(trans.getMaximumContextLength());add(trans.toRules(false));add(trans.toRules(true))
            for(element in trans.getElements())add(element.getID())
            for(filter in listOf(null,"[a-zA-ZΣΙİß\\u0300-\\u036f]","[]")) {
                trans.setFilter(filter?.let(::UnicodeSet))
                for(input in inputs) {
                    add(trans.transliterate(input))
                    val text=ReplaceableString();val pos=Transliterator.Position()
                    for(c in input){trans.transliterate(text,pos,c.toString());add(text);add(pos)}
                    trans.finishTransliteration(text,pos);add(text);add(pos)
                }
            }
        }
        return hash
    }
    private fun check(range:IntRange){for(index in range)assertEquals(expected[index],fingerprint(index),ids[index])}
    @Test fun basicAndCaseBuiltins(){check(0..5)}
    @Test fun normalizationBuiltins(){check(6..11)}
    @Test fun unicodeNames(){check(12..13)}
    @Test fun escapeBuiltins(){check(14..21)}
    @Test fun unescapeBuiltins(){check(22..28)}
    @Test fun thaiWordBreakInsertion(){check(29..29)}
    @Test fun greekAndCyrillicRules(){check(30..33)}
    @Test fun asciiAndAnyScriptDispatch(){check(34..36)}
    @Test fun hanAndDevanagariRules(){check(37..39)}
    @Test fun localeRules(){check(40..40)}
    @Test fun completeOrderedInventoryMatchesIcu() {
        val ids=Transliterator.getAvailableIDs()
        var hash=1;var count=0
        while(ids.hasMoreElements()){hash=31*hash+ids.nextElement().hashCode();count++}
        assertEquals(816,count);assertEquals(1877515259,hash)
    }
    @Test fun customRuleBasedAndCompoundTransforms() {
        val rule=Transliterator.createFromRules("custom","::NFD; [[:Mn:]] > ; ::Upper;",0)
        assertEquals("A SS ΣΟΣ",rule.transliterate("á ß ΣΟΣ"))
        val data=Transliterator.createFromRules("rules","ab > X;",0) as RuleBasedTransliterator
        assertEquals(0,data.getMaximumContextLength())
        assertNotSame(data,data.safeClone());assertEquals(data.toRules(false),data.safeClone().toRules(false))
        val text=ReplaceableString();val pos=Transliterator.Position()
        data.transliterate(text,pos,"a");assertEquals("a",text.toString());assertEquals(0,pos.start)
        data.transliterate(text,pos,"b");data.finishTransliteration(text,pos)
        assertEquals("X",text.toString());assertEquals(1,pos.start)
    }
    @Test fun nullRegistrationIsDeferredAndNullAliasResolvesNullTransform() {
        val alias="HeapyNull-Alias"
        val factory="HeapyNull-Factory"
        try {
            Transliterator.registerAlias(alias,null)
            val trans=Transliterator.getInstance(alias)
            assertEquals(alias,trans.getID());assertEquals("abc",trans.transliterate("abc"))
            Transliterator.registerFactory(factory,null)
            val ids=Transliterator.getAvailableIDs()
            var found=false
            while(ids.hasMoreElements())if(ids.nextElement()==factory)found=true
            assertTrue(found)
            assertFailsWith<NullPointerException>{Transliterator.getInstance(factory)}
        }finally {Transliterator.unregister(alias);Transliterator.unregister(factory)}
    }

}

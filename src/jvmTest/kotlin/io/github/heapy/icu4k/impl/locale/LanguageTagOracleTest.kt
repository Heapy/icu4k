package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.LanguageTag as J
import com.ibm.icu.impl.locale.ParseStatus as JS
import kotlin.random.Random
import kotlin.test.*

class LanguageTagOracleTest {
    private fun compare(input: String) {
        val js = JS(); val ks = ParseStatus().also { it._errorIndex = 99; it._errorMsg = "old"; it._parseLength = 10 }
        val j = J.parse(input, js); val k = LanguageTag.parse(input, ks)
        assertEquals(j.toString(), k.toString(), input)
        assertEquals(j.language, k.getLanguage(), input); assertEquals(j.extlangs, k.getExtlangs(), input)
        assertEquals(j.script, k.getScript(), input); assertEquals(j.region, k.getRegion(), input)
        assertEquals(j.variants, k.getVariants(), input); assertEquals(j.extensions, k.getExtensions(), input)
        assertEquals(j.privateuse, k.getPrivateuse(), input)
        assertEquals(js.parseLength, ks.getParseLength(), input); assertEquals(js.errorIndex, ks.getErrorIndex(), input)
        assertEquals(js.errorMessage, ks.getErrorMessage(), input); assertEquals(js.isError, ks.isError(), input)
        assertEquals(j.toString(), LanguageTag.parse(input).toString())
    }
    @Test fun parsesComponentsDuplicatesErrorsAndGrandfatheredMappings() {
        val inputs = listOf("", "en", "EN-lAtN-us", "zh-cmn-Hans-CN", "abc-def-ghi-jkl-Latn-419-1901", "abcd-efg",
            "en-abc-def-ghi-jkl", "de-1901-1901-1996", "en-abcde-ABCDE", "en-a-ab-A-cd-b-XY-x-foo",
            "x-a-B", "X-a", "en-x", "x", "en-a", "en-a-b-xy", "en--US", "en_Us", "en-abcdefghi", "en-😀", "en-\ud800",
            "art-lojban", "cel-gaulish", "en-GB-oed", "i-ami", "i-bnn", "i-default", "i-enochian", "i-hak", "i-klingon",
            "i-lux", "i-mingo", "i-navajo", "i-pwn", "i-tao", "i-tay", "i-tsu", "no-bok", "no-nyn", "sgn-BE-FR",
            "sgn-BE-NL", "sgn-CH-DE", "zh-guoyu", "zh-hakka", "zh-min", "zh-min-nan", "zh-xiang",
            "I-KLINGON-Latn", "sgn-BE-FR-x-custom", "en-GB-oed-more", "en-u-ca-japanese-u-nu-thai")
        for (input in inputs) compare(input)
        val random = Random(311781)
        val parts = listOf("en", "abc", "Latn", "US", "419", "1901", "posix", "POSIX", "u", "a", "9", "x", "ca", "japanese", "", "!", "abcdefghi")
        repeat(3000) { compare(List(random.nextInt(1, 9)) { parts.random(random) }.joinToString("-")) }
    }
    @Test fun validatorsAndCanonicalizationMatchOriginal() {
        val names = listOf("isLanguage", "isExtlang", "isScript", "isRegion", "isVariant", "isTKey", "isExtensionSingleton",
            "isExtensionSubtag", "isPrivateusePrefix", "isPrivateuseSubtag", "canonicalizeLanguage", "canonicalizeExtlang",
            "canonicalizeScript", "canonicalizeRegion", "canonicalizeVariant", "canonicalizeExtension", "canonicalizeExtensionSingleton",
            "canonicalizeExtensionSubtag", "canonicalizePrivateuse", "canonicalizePrivateuseSubtag")
        val target = LanguageTag.Companion
        val random = Random(812655)
        val values = listOf("", "u-ca-true-nu-yes", "u-true-true-yes-yes", "u-yes-true", "a-true-yes", "1AbC", "İ", "😀") +
            List(1000) { buildString { repeat(random.nextInt(12)) { append("aAzZ09-xTRUEyesİ".random(random)) } } }
        for (name in names) {
            val j = J::class.java.getMethod(name, String::class.java)
            val k = target.javaClass.getMethod(name, String::class.java)
            for (value in values) assertEquals(j.invoke(null, value), k.invoke(target, value), "$name($value)")
        }
        for (code in 0..0xffff) {
            assertEquals(J.isExtensionSingletonChar(code.toChar()), LanguageTag.isExtensionSingletonChar(code.toChar()))
            assertEquals(J.isPrivateusePrefixChar(code.toChar()), LanguageTag.isPrivateusePrefixChar(code.toChar()))
        }
    }
    @Test fun defaultLocaleCasingMatchesOriginalIncludingTurkish() {
        val saved = java.util.Locale.getDefault()
        try {
            for (language in listOf("en", "tr", "az", "lt")) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(language))
                compare("en-variant-VARIANT-I-ab-i-cd")
                compare("en-posix-1901")
            }
        } finally { java.util.Locale.setDefault(saved) }
    }
    @Test fun malformedGrandfatheredSuffixesMatchReleaseAssertionPolicy() {
        // Java assertions are diagnostic-only; use a fresh ICU loader with release defaults.
        val location = J::class.java.protectionDomain.codeSource.location
        java.net.URLClassLoader(arrayOf(location), ClassLoader.getPlatformClassLoader()).use { loader ->
            loader.setDefaultAssertionStatus(false)
            val type = loader.loadClass("com.ibm.icu.impl.locale.LanguageTag")
            val statusType = loader.loadClass("com.ibm.icu.impl.locale.ParseStatus")
            val method = type.getMethod("parse", String::class.java, statusType)
            for (input in listOf("i-klingon-!", "i-default-a", "zh-min-x-", "en-GB-oed-", "sgn-BE-FR-abcdefghi")) {
                val status = statusType.getConstructor().newInstance()
                val j = method.invoke(null, input, status)
                val ks = ParseStatus(); val k = LanguageTag.parse(input, ks)
                assertEquals(j.toString(), k.toString(), input)
                assertEquals(statusType.getMethod("getParseLength").invoke(status), ks.getParseLength())
                assertEquals(statusType.getMethod("getErrorIndex").invoke(status), ks.getErrorIndex())
                assertEquals(statusType.getMethod("getErrorMessage").invoke(status), ks.getErrorMessage())
            }
        }
    }
}

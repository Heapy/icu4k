package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.BreakIteratorDataFactory
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class BreakIteratorDataFactoryOracleTest {
    @Test fun rulesBoundariesAndLocalesMatchOriginalPrivateFactory() {
        val method = Class.forName("com.ibm.icu.text.BreakIteratorFactory").getDeclaredMethod("createBreakInstance", com.ibm.icu.util.ULocale::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
        val texts = listOf("", "Hello. Next! Mr. Smith came.", "日本語の文章です。次の文。", "한국어 문장입니다. 다음 문장.", "你好。下一句。", "Γεια σας. Α. Β. Τέλος.", "a\u0301 😀 x", "ภาษาไทย ทดสอบ")
        for (id in listOf("root", "en_US", "en_US_POSIX", "en@ss=standard", "fr@ss=standard", "el", "ja", "ja@lb=loose", "ja@lb=normal;lw=phrase", "ko@lb=strict;lw=phrase", "zh_Hant", "zz_ZZ", "en@lb=bogus;lw=phrase")) for (kind in 0..4) {
            val j = method.invoke(null, com.ibm.icu.util.ULocale(id), kind) as com.ibm.icu.text.BreakIterator
            val k = BreakIteratorDataFactory.create(ULocale(id), kind)
            assertEquals(j.getLocale(com.ibm.icu.util.ULocale.ACTUAL_LOCALE)?.name, k.getLocale(ULocale.ACTUAL_LOCALE)?.getName(), "$id/$kind")
            assertEquals(j.getLocale(com.ibm.icu.util.ULocale.VALID_LOCALE)?.name, k.getLocale(ULocale.VALID_LOCALE)?.getName(), "$id/$kind")
            for (text in texts) {
                j.setText(text); k.setText(text)
                val a = mutableListOf<Int>(); val b = mutableListOf<Int>()
                var p = j.first(); while (p != -1) { a.add(p); p = j.next() }
                p = k.first(); while (p != -1) { b.add(p); p = k.next() }
                assertEquals(a, b, "$id/$kind/$text")
                assertEquals(j.clone().getLocale(com.ibm.icu.util.ULocale.ACTUAL_LOCALE)?.name, k.clone().getLocale(ULocale.ACTUAL_LOCALE)?.getName())
            }
        }
    }
    @Test fun invalidKindsRetainMissingResourceDetails() {
        val method = Class.forName("com.ibm.icu.text.BreakIteratorFactory").getDeclaredMethod("createBreakInstance", com.ibm.icu.util.ULocale::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
        for (kind in listOf(-1, 5, 99, Int.MAX_VALUE)) {
            val j = assertFailsWith<java.lang.reflect.InvocationTargetException> { method.invoke(null, com.ibm.icu.util.ULocale.ROOT, kind) }.cause as java.util.MissingResourceException
            val k = assertFailsWith<io.github.heapy.icu4k.util.MissingResourceException> { BreakIteratorDataFactory.create(ULocale.ROOT, kind) }
            assertEquals(j.message, k.message); assertEquals(j.className, k.getClassName()); assertEquals(j.key, k.getKey())
        }
    }
    @Test fun opaqueSelectorsAreMutableIdentityFieldsAndMetadataClones() {
        val j = com.ibm.icu.text.RuleBasedBreakIterator(".;")
        val k = RuleBasedBreakIterator(".;")
        val setter = com.ibm.icu.text.BreakIterator::class.java.getDeclaredMethod("setLocale", com.ibm.icu.util.ULocale::class.java, com.ibm.icu.util.ULocale::class.java).also { it.isAccessible = true }
        setter.invoke(j, com.ibm.icu.util.ULocale("en_US"), com.ibm.icu.util.ULocale.ROOT)
        k.setLocale(ULocale("en_US"), ULocale.ROOT)
        val ja = com.ibm.icu.util.ULocale.ACTUAL_LOCALE; val jv = com.ibm.icu.util.ULocale.VALID_LOCALE
        val ka = ULocale.ACTUAL_LOCALE; val kv = ULocale.VALID_LOCALE
        try {
            assertEquals(j.getLocale(null).name, k.getLocale(null)?.getName())
            com.ibm.icu.util.ULocale.ACTUAL_LOCALE = jv; ULocale.ACTUAL_LOCALE = kv
            assertEquals(j.getLocale(jv).name, k.getLocale(kv)?.getName())
            assertEquals(j.getLocale(ja).name, k.getLocale(ka)?.getName())
            com.ibm.icu.util.ULocale.ACTUAL_LOCALE = null; ULocale.ACTUAL_LOCALE = null
            assertEquals(j.getLocale(null).name, k.getLocale(null)?.getName())
            assertEquals(j.clone().getLocale(jv).name, k.clone().getLocale(kv)?.getName())
        } finally { com.ibm.icu.util.ULocale.ACTUAL_LOCALE = ja; ULocale.ACTUAL_LOCALE = ka }
        for (field in listOf("ACTUAL_LOCALE", "VALID_LOCALE")) {
            assertEquals(com.ibm.icu.util.ULocale::class.java.getField(field).modifiers, ULocale::class.java.getField(field).modifiers)
        }
    }

    @Test fun ruleIoFailureUsesSourceAssertionDiagnostic() {
        for (message in listOf(null, "bad rules", "ICU data file error: Header authentication failed")) {
            val original = java.io.IOException(message)
            val source = assertFailsWith<IllegalStateException> { com.ibm.icu.impl.Assert.fail(original) }
            val wrapped = io.github.heapy.icu4k.util.ICUUncheckedIOException(original)
            val actual = assertFailsWith<IllegalStateException> { io.github.heapy.icu4k.impl.failBreakRuleIOException(wrapped) }
            assertEquals(source.message, actual.message)
            if (message != null) assertEquals(source.message, assertFailsWith<IllegalStateException> { io.github.heapy.icu4k.impl.failBreakRuleIOException(io.github.heapy.icu4k.util.ICUUncheckedIOException(message)) }.message)
        }
    }

}

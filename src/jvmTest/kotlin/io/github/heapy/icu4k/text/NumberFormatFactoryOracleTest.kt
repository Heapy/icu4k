// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.NumberFormat as JNumber
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.*
import java.lang.reflect.Modifier
import java.util.Locale
import kotlin.test.*

class NumberFormatFactoryOracleTest {
    @Test fun commonAndJavaVirtualOverloadsDelegateBothWays() {
        val jEvents = arrayListOf<String>()
        val kEvents = arrayListOf<String>()
        val jf = object : JNumber.NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = emptySet()
            override fun createFormat(loc: Locale?, formatType: Int): JNumber? { jEvents.add("$loc:$formatType"); return null }
        }
        val kf = object : NumberFormat.NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = emptySet()
            override fun createFormat(loc: Locale?, formatType: Int): NumberFormat? { kEvents.add("$loc:$formatType"); return null }
        }
        for (id in listOf("en_US", "sr_Latn_RS@currency=EUR", "root", "ja_JP_JP", "en@calendar=islamic")) {
            jf.createFormat(JLocale(id), 7); kf.createFormat(ULocale(id), 7)
        }
        assertEquals(jEvents, kEvents)
        assertTrue(jf.visible()); assertTrue(kf.visible())
        val juf = object : JNumber.NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = emptySet()
            override fun createFormat(loc: JLocale?, formatType: Int): JNumber? { jEvents.add("${loc?.name}:$formatType"); return null }
        }
        val kuf = object : NumberFormatFactory() {
            override fun getSupportedLocaleNames(): Set<String> = emptySet()
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? { kEvents.add("${loc?.getName()}:$formatType"); return null }
        }
        for (locale in listOf(null, Locale.US, Locale.forLanguageTag("sr-Latn-RS-u-cu-eur"), Locale.ROOT)) {
            juf.createFormat(locale, 2); kuf.createFormat(locale, 2)
        }
        assertEquals(jEvents, kEvents)
    }
    @Test fun constructorNamesConstantsAndFinalMethods() {
        val source = JNumber.NumberFormatFactory::class.java
        val target = NumberFormatFactory::class.java
        for (field in source.fields) if (Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
            assertEquals(field.getInt(null), target.getField(field.name).getInt(null))
        }
        val sc = JNumber.SimpleNumberFormatFactory::class.java
        val tc = SimpleNumberFormatFactory::class.java
        for (c in sc.constructors) tc.getConstructor(*c.parameterTypes.map { if (it == JLocale::class.java) ULocale::class.java else it }.toTypedArray())
        for (name in listOf("visible", "getSupportedLocaleNames")) assertTrue(Modifier.isFinal(tc.getDeclaredMethod(name).modifiers))
        for (visible in listOf(false, true)) for (id in listOf("en_CA@currency=EUR", "root", "sr_Latn_RS")) {
            val sf = object : JNumber.SimpleNumberFormatFactory(JLocale(id), visible) {
                override fun createFormat(loc: JLocale?, formatType: Int): JNumber? = null
            }
            val kf = object : SimpleNumberFormatFactory(ULocale(id), visible) {
                override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? = null
            }
            assertEquals(sf.supportedLocaleNames, kf.getSupportedLocaleNames())
            assertEquals(sf.visible(), kf.visible())
            assertSame(kf.getSupportedLocaleNames(), kf.getSupportedLocaleNames())
            assertFailsWith<UnsupportedOperationException> { (kf.getSupportedLocaleNames() as MutableSet).add("x") }
        }
        val sj = object : JNumber.SimpleNumberFormatFactory(Locale.CANADA, false) {}
        val kj = object : SimpleNumberFormatFactory(Locale.CANADA, false) {}
        assertEquals(sj.supportedLocaleNames, kj.getSupportedLocaleNames())
        assertEquals(sj.visible(), kj.visible())
    }
}

package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import java.net.URLClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import kotlin.test.*
import com.ibm.icu.impl.StaticUnicodeSets as Original

class StaticUnicodeSetsOracleTest {
    @Test fun everyFrozenSetAndChoiceMatchesSource() {
        for (key in StaticUnicodeSets.Key.entries) {
            val sourceKey = Original.Key.valueOf(key.name)
            val expected = Original.get(sourceKey)
            val actual = StaticUnicodeSets.get(key)
            assertEquals(expected.toPattern(false), actual.toPattern(false), key.name)
            assertEquals(expected.size(), actual.size())
            assertEquals(expected.isFrozen, actual.isFrozen())
            for (str in listOf(".", ",", "،", "٫", " ", "’", "+", "-", "$", "£", "₹", "¥", "₩", "Rp", "Rs", "%", "‰", "0", "١", "約", "∞", "abc")) {
                assertEquals(Original.chooseFrom(str, sourceKey)?.name, StaticUnicodeSets.chooseFrom(str, key)?.name)
                assertEquals(Original.chooseFrom(str, sourceKey, Original.Key.DIGITS)?.name, StaticUnicodeSets.chooseFrom(str, key, StaticUnicodeSets.Key.DIGITS)?.name)
                assertEquals(Original.chooseCurrency(str)?.name, StaticUnicodeSets.chooseCurrency(str)?.name)
            }
            for (str in listOf(" \tvalue \t", "\u200e\ufe0f12\u202f", "abc", "\nvalue\n", "\u00adtest\u00ad")) {
                assertEquals(expected.span(str, com.ibm.icu.text.UnicodeSet.SpanCondition.CONTAINED), actual.span(str, UnicodeSet.SpanCondition.CONTAINED))
                assertEquals(expected.spanBack(str, com.ibm.icu.text.UnicodeSet.SpanCondition.CONTAINED), actual.spanBack(str, UnicodeSet.SpanCondition.CONTAINED))
            }
        }
        assertSame(com.ibm.icu.text.UnicodeSet.EMPTY, Original.get(null))
        assertSame(UnicodeSet.EMPTY, StaticUnicodeSets.get(null))
    }
    @Test fun coldConcurrentAccessPublishesOneFrozenSet() {
        val urls = arrayOf(StaticUnicodeSets::class.java.protectionDomain.codeSource.location,
            Original::class.java.protectionDomain.codeSource.location,
            Unit::class.java.protectionDomain.codeSource.location)
        URLClassLoader(urls, null).use { loader ->
            val cls = loader.loadClass("io.github.heapy.icu4k.impl.StaticUnicodeSets")
            val enum = loader.loadClass("io.github.heapy.icu4k.impl.StaticUnicodeSets\$Key")
            val key = enum.enumConstants.single { (it as Enum<*>).name == "ALL_SEPARATORS" }
            val method = cls.getMethod("get", enum)
            val executor = Executors.newFixedThreadPool(8)
            val start = CountDownLatch(1)
            try {
                val jobs = (0 until 8).map { executor.submit<Any> { start.await(); val instance = cls.getField("INSTANCE").get(null); method.invoke(instance, key) } }
                start.countDown()
                val sets = jobs.map { it.get() }
                sets.forEach { assertSame(sets[0], it); assertTrue(it.javaClass.getMethod("isFrozen").invoke(it) as Boolean) }
            } finally { executor.shutdownNow() }
        }
    }
}

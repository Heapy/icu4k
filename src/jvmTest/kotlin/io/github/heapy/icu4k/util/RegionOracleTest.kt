package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import java.net.URLClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import kotlin.test.*
import com.ibm.icu.util.Region as Original

class RegionOracleTest {
    @Test fun everyRegionTypeHierarchyAndContainmentMatchesOriginal() {
        val originals = Original.RegionType.entries.flatMap { Original.getAvailable(it) }
        val actuals = Region.RegionType.entries.flatMap { Region.getAvailable(it) }
        assertEquals(originals.map { it.toString() }, actuals.map { it.toString() })
        for ((expected, actual) in originals.zip(actuals)) {
            assertEquals(expected.numericCode, actual.getNumericCode(), "$expected numeric")
            assertEquals(expected.type.name, actual.getType().name)
            assertEquals(expected.containingRegion?.toString(), actual.getContainingRegion()?.toString())
            assertEquals(expected.containedRegions.map { it.toString() }, actual.getContainedRegions().map { it.toString() })
            assertEquals(expected.preferredValues?.map { it.toString() }, actual.getPreferredValues()?.map { it.toString() })
            for (type in Region.RegionType.entries) {
                val jtype = Original.RegionType.valueOf(type.name)
                assertEquals(expected.getContainingRegion(jtype)?.toString(), actual.getContainingRegion(type)?.toString(), "$expected/$type parent")
                assertEquals(expected.getContainedRegions(jtype).map { it.toString() }, actual.getContainedRegions(type).map { it.toString() }, "$expected/$type children")
            }
            assertEquals(expected.getContainedRegions(null).map { it.toString() }, actual.getContainedRegions(null).map { it.toString() })
            for ((otherExpected, otherActual) in originals.zip(actuals)) {
                assertEquals(expected.contains(otherExpected), actual.contains(otherActual), "$expected/$otherExpected contains")
                assertEquals(expected.compareTo(otherExpected), actual.compareTo(otherActual))
            }
        }
    }
    @Test fun everyAliasMappingAndNumericLookupMatchesOriginal() {
        fun result(block: () -> Any?): Any? = try { block()?.toString() } catch (failure: IllegalArgumentException) { failure.message }
        val alias = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata")["alias"]["territory"]
        val mapping = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData")["codeMappings"]
        val names = mutableListOf("", "us", "USA", "001", "000", "ZZ", "-1", "ZZZ", "U", "ZZZZ")
        for (index in 0 until alias.getSize()) names.add(alias[index].getKey()!!)
        for (index in 0 until mapping.getSize()) if (mapping[index].getType() == UResourceBundle.ARRAY) names.addAll(mapping[index].getStringArray())
        for (name in names) assertEquals(result { Original.getInstance(name) }, result { Region.getInstance(name) }, name)
        for (number in -20..1200) assertEquals(result { Original.getInstance(number) }, result { Region.getInstance(number) }, "$number")
    }
    @Test fun mutationsRejectWithoutEvaluatingCallbacks() {
        val values = Region.getAvailable(Region.RegionType.TERRITORY) as MutableSet<Region>
        assertFailsWith<UnsupportedOperationException> { values.removeIf { error("must not evaluate") } }
        assertFailsWith<UnsupportedOperationException> { values.removeAll(emptyList()) }
        val list = Region.getInstance("SU").getPreferredValues() as MutableList<Region>
        assertFailsWith<UnsupportedOperationException> { list.replaceAll { error("must not evaluate") } }
        assertFailsWith<UnsupportedOperationException> { list.sortWith(compareBy { it.toString() }) }
    }
    @Test fun coldConcurrentInitializationPublishesOneIdentity() {
        val urls = arrayOf(Region::class.java.protectionDomain.codeSource.location,
            Original::class.java.protectionDomain.codeSource.location,
            Unit::class.java.protectionDomain.codeSource.location)
        for (original in listOf(false, true)) URLClassLoader(urls, null).use { loader ->
            val name = if (original) "com.ibm.icu.util.Region" else "io.github.heapy.icu4k.util.Region"
            val cls = loader.loadClass(name)
            val receiver = if (original) null else cls.getField("Companion").get(null)
            val factoryClass = receiver?.javaClass ?: cls
            val factory = factoryClass.getMethod("getInstance", String::class.java)
            val executor = Executors.newFixedThreadPool(8)
            val start = CountDownLatch(1)
            try {
                val jobs = (0 until 8).map { executor.submit<Any> { start.await(); factory.invoke(receiver, "US") } }
                start.countDown()
                val regions = jobs.map { it.get() }
                regions.forEach {
                    assertSame(regions[0], it)
                    assertEquals("US", it.toString())
                    assertEquals(840, cls.getMethod("getNumericCode").invoke(it))
                }
            } finally { executor.shutdownNow() }
        }
    }
}

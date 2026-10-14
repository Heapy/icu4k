@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import java.util.Locale
import kotlin.test.*
import com.ibm.icu.util.LocaleData as Original
import com.ibm.icu.util.ULocale as OriginalLocale

class LocaleDataOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        listOf(failure.javaClass.simpleName, failure.message?.replace("io.github.heapy.icu4k", "com.ibm.icu"))
    }
    // Compare implicit region-array failures by type; generated JVM messages can be absent.
    private fun regionOutcome(block: () -> Any?): Any? = try { block() } catch (failure: ArrayIndexOutOfBoundsException) {
        listOf(failure.javaClass.simpleName)
    } catch (failure: Throwable) {
        listOf(failure.javaClass.simpleName, failure.message?.replace("io.github.heapy.icu4k", "com.ibm.icu"))
    }
    @Test fun exemplarsDelimitersAndDisplayPatternsMatchSource() {
        val saved = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.ROOT)
            for (name in listOf("root", "en", "fr", "ar", "ja", "en_US", "fr_CA", "zzzz", "en_US_POSIX@calendar=japanese"))
                for (noSubstitute in listOf(false, true)) {
                    val expected = Original.getInstance(OriginalLocale(name))
                    val actual = LocaleData.getInstance(ULocale(name))
                    expected.setNoSubstitute(noSubstitute)
                    actual.setNoSubstitute(noSubstitute)
                    assertEquals(expected.noSubstitute, actual.getNoSubstitute())
                    for (type in -1..5) for (options in listOf(0, 1, 2, 4, 16, 256)) {
                        assertEquals(outcome { expected.getExemplarSet(options, type)?.let { listOf(it.toPattern(false), it.isFrozen) } },
                            outcome { actual.getExemplarSet(options, type)?.let { listOf(it.toPattern(false), it.isFrozen()) } }, "$name/$noSubstitute/$type/$options")
                    }
                    for (type in -1..4) assertEquals(outcome { expected.getDelimiter(type) }, outcome { actual.getDelimiter(type) }, "$name/$noSubstitute/$type delimiter")
                    assertEquals(outcome { expected.localeDisplayPattern }, outcome { actual.getLocaleDisplayPattern() }, "$name pattern")
                    assertEquals(outcome { expected.localeSeparator }, outcome { actual.getLocaleSeparator() }, "$name separator")
                }
        } finally {
            Locale.setDefault(saved)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
    @Test fun measurementPaperAndSupplementalRegionSelectionMatchEveryRegion() {
        fun system(value: Original.MeasurementSystem?): String? = when (value) { Original.MeasurementSystem.SI -> "SI"; Original.MeasurementSystem.US -> "US"; Original.MeasurementSystem.UK -> "UK"; else -> null }
        fun system(value: LocaleData.MeasurementSystem?): String? = when (value) { LocaleData.MeasurementSystem.SI -> "SI"; LocaleData.MeasurementSystem.US -> "US"; LocaleData.MeasurementSystem.UK -> "UK"; else -> null }
        val names = Region.RegionType.entries.flatMap { Region.getAvailable(it) }.map { "und_$it" } +
            listOf("en", "fr", "ja", "zzzz", "en_US@rg=gbzzzz", "en@sd=usca", "en_GB@sd=usca", "fr@rg=usfoo", "en@rg=001zzzz", "en@rg=xxzzzz", "en@sd=fi01", "en@rg=ßdzzz", "en@rg=us------")
        for (name in names) {
            val j = OriginalLocale(name)
            val k = ULocale(name)
            for (infer in listOf(false, true)) assertEquals(outcome { OriginalLocale.getRegionForSupplementalData(j, infer) }, outcome { ULocale.getRegionForSupplementalData(k, infer) }, "$name/$infer")
            assertEquals(outcome { system(Original.getMeasurementSystem(j)) }, outcome { system(LocaleData.getMeasurementSystem(k)) }, "$name system")
            assertEquals(outcome { Original.getPaperSize(j).let { listOf(it.height, it.width) } }, outcome { LocaleData.getPaperSize(k).let { listOf(it.getHeight(), it.getWidth()) } }, "$name paper")
        }
        assertEquals(Original.getCLDRVersion().toString(), LocaleData.getCLDRVersion().toString())
    }
    @Test fun regionValidationMaskAndHostCaseMatchSource() {
        val saved = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            for (host in listOf(Locale.ROOT, Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("az-AZ"))) {
                Locale.setDefault(host)
                val j = OriginalLocale.RegionValidateMap()
                val k = ULocale.RegionValidateMap()
                for (a in 'A'..'Z') for (b in 'A'..'Z') for (region in listOf("$a$b", "$a$b".lowercase()))
                    assertEquals(regionOutcome { j.isSet(region) }, regionOutcome { k.isSet(region) }, "$host/$region")
                for (value in listOf("idzzzz", "IDzzzz", "inabc", "iDabc", "uszzzz", "ßdzzz", "ıdzzzz", "001zzzz", "gb", "xxzzzz")) {
                    val name = "en@rg=$value"
                    assertEquals(regionOutcome { OriginalLocale.getRegionForSupplementalData(OriginalLocale(name), true) }, regionOutcome { ULocale.getRegionForSupplementalData(ULocale(name), true) }, "$host/$value")
                }
                assertEquals(j.equals(OriginalLocale.RegionValidateMap()), k.equals(ULocale.RegionValidateMap()))
            }
        } finally {
            Locale.setDefault(saved)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
    @Test fun defaultFactoryUsesFormatAndNullFactoryUsesGeneral() {
        val saved = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.ENGLISH)
            Locale.setDefault(Locale.Category.FORMAT, Locale.FRENCH)
            assertEquals(Original.getInstance().getDelimiter(0), LocaleData.getInstance().getDelimiter(0))
            assertEquals(Original.getInstance(null).getDelimiter(0), LocaleData.getInstance(null).getDelimiter(0))
            assertNotEquals(LocaleData.getInstance().getDelimiter(0), LocaleData.getInstance(null).getDelimiter(0))
        } finally {
            Locale.setDefault(saved)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
}

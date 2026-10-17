// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.LocaleMatcher as Source
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.LocalePriorityList as SourcePriority
import kotlin.random.Random
import kotlin.test.*
import java.util.Locale

class LocaleMatcherOracleTest {
    private val pool = listOf("en", "en_US", "en_GB", "en_AU", "en_NZ", "en_CA", "en_IN", "fr", "fr_CA", "fr_CH", "de", "de_AT", "de_CH_1901",
        "es", "es_419", "es_MX", "pt", "pt_PT", "pt_BR", "zh", "zh_Hant", "zh_TW", "zh_HK", "sr", "sr_Latn", "sr_Cyrl", "hr", "bs", "ru", "uk",
        "ar", "az_Arab", "fa", "nb", "no", "nn", "da", "ja", "ko", "he", "iw_IL", "id", "in_ID", "root", "", "und", "und_Arab", "en_XA", "ar_XB",
        "en__PSACCENT", "@x=abc", "@x=def", "gsw", "af", "yue", "en@calendar=buddhist;numbers=arab", "de_CH@collation=phonebook", "de_CH_BAD", "en__A", "fr_FR_EURO")
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Exception) {
        listOf(e.javaClass.simpleName, if (e is NullPointerException) null else e.message)
    }
    private fun sourceResult(result: Source.Result): List<Any?> = listOf(result.desiredULocale?.toString(), result.supportedULocale?.toString(),
        result.desiredIndex, result.supportedIndex, outcome { result.makeResolvedULocale()?.toString() },
        result.desiredLocale?.toString(), result.supportedLocale?.toString(), outcome { result.makeResolvedLocale()?.toString() })
    private fun portResult(result: LocaleMatcher.Result): List<Any?> = listOf(result.getDesiredULocale()?.toString(), result.getSupportedULocale()?.toString(),
        result.getDesiredIndex(), result.getSupportedIndex(), outcome { result.makeResolvedULocale()?.toString() },
        result.getDesiredLocale()?.toString(), result.getSupportedLocale()?.toString(), outcome { result.makeResolvedLocale()?.toString() })

    @Test fun builderConfigurationsDesiredListsOrderingAndResolvedResults() {
        val random = Random(78_305)
        repeat(600) { iteration ->
            val supported = List(random.nextInt(0, 15)) { pool.random(random) }
            val sb = Source.builder().setSupportedULocales(supported.map(::SourceLocale))
            val pb = LocaleMatcher.builder().setSupportedULocales(supported.map(::ULocale))
            val favor = (Source.FavorSubtag.values().toList() + null).random(random)
            val direction = (Source.Direction.values().toList() + null).random(random)
            val demotion = (Source.Demotion.values().toList() + null).random(random)
            sb.setFavorSubtag(favor).setDirection(direction).setDemotionPerDesiredLocale(demotion)
            pb.setFavorSubtag(favor?.let { LocaleMatcher.FavorSubtag.valueOf(it.name) }).setDirection(direction?.let { LocaleMatcher.Direction.valueOf(it.name) })
                .setDemotionPerDesiredLocale(demotion?.let { LocaleMatcher.Demotion.valueOf(it.name) })
            when (random.nextInt(3)) {
                0 -> { sb.setNoDefaultLocale(); pb.setNoDefaultLocale() }
                1 -> { val default = pool.random(random); sb.setDefaultULocale(SourceLocale(default)); pb.setDefaultULocale(ULocale(default)) }
            }
            when (random.nextInt(3)) {
                0 -> { val threshold = listOf(-10, -1, 0, 1, 5, 10, 50, 100, 101, Int.MAX_VALUE).random(random); sb.internalSetThresholdDistance(threshold); pb.internalSetThresholdDistance(threshold) }
                1 -> { val d = pool.random(random); val s = pool.random(random); sb.setMaxDistance(SourceLocale(d), SourceLocale(s)); pb.setMaxDistance(ULocale(d), ULocale(s)) }
            }
            assertEquals(sb.toString(), pb.toString(), "builder $iteration")
            val source = sb.build(); val port = pb.build()
            assertEquals(source.toString(), port.toString(), "matcher $iteration")
            repeat(20) {
                val desired = List(random.nextInt(0, 7)) { pool.random(random) }
                val sl = desired.map(::SourceLocale); val pl = desired.map(::ULocale)
                val label = "$iteration supported $supported desired $desired config $source"
                assertEquals(source.getBestMatch(sl)?.toString(), port.getBestMatch(pl)?.toString(), label)
                assertEquals(sourceResult(source.getBestMatchResult(sl)), portResult(port.getBestMatchResult(pl)), label)
                if (desired.isNotEmpty()) {
                    assertEquals(source.getBestMatch(sl[0])?.toString(), port.getBestMatch(pl[0])?.toString(), label)
                    assertEquals(sourceResult(source.getBestMatchResult(sl[0])), portResult(port.getBestMatchResult(pl[0])), label)
                }
                val d = pool.random(random); val s = pool.random(random)
                assertEquals(source.isMatch(SourceLocale(d), SourceLocale(s)), port.isMatch(ULocale(d), ULocale(s)), label)
                assertEquals(source.match(SourceLocale(d), null, SourceLocale(s), null), port.match(ULocale(d), null, ULocale(s), null), label)
            }
        }
    }

    @Test fun prioritiesDefaultsBuilderReuseAndCanonicalization() {
        for (supported in listOf("en, fr, de", "fr;q=0.5, en;q=1, de;q=0", "en_GB, en_US, en, fr_CA", "", "und, root, en")) {
            val source = Source(supported); val port = LocaleMatcher(supported)
            val fromPriority = LocaleMatcher(LocalePriorityList.add(supported).build())
            for (desired in listOf("fr_CA;q=0.9, en_AU;q=1", "de_CH, fr_FR", "", "und", "@x=abc")) {
                assertEquals(source.getBestMatch(desired)?.toString(), port.getBestMatch(desired)?.toString(), "$supported/$desired")
                assertEquals(port.getBestMatch(desired), fromPriority.getBestMatch(desired))
            }
            for (locale in pool) assertEquals(source.canonicalize(SourceLocale(locale)).toString(), port.canonicalize(ULocale(locale)).toString())
        }
        val sl = arrayListOf(SourceLocale("en"), SourceLocale("fr")); val pl = arrayListOf(ULocale("en"), ULocale("fr"))
        val sb = Source.builder().setSupportedULocales(sl); val pb = LocaleMatcher.builder().setSupportedULocales(pl)
        sl.clear(); pl.clear()
        val s1 = sb.build(); val p1 = pb.build()
        sb.addSupportedULocale(SourceLocale("de")).setDefaultULocale(SourceLocale("fr"))
        pb.addSupportedULocale(ULocale("de")).setDefaultULocale(ULocale("fr"))
        assertEquals(s1.toString(), p1.toString())
        assertEquals(sb.build().toString(), pb.build().toString())
        assertEquals(s1.getBestMatch(SourceLocale("de"))?.toString(), p1.getBestMatch(ULocale("de"))?.toString())
    }

    @Test fun lazyDesiredIterationStopsAndPreservesOriginalIdentities() {
        val source = Source("en_US, fr_FR"); val port = LocaleMatcher("en_US, fr_FR")
        val sl = listOf(SourceLocale("en_US"), SourceLocale("fr_FR")); val pl = listOf(ULocale("en_US"), ULocale("fr_FR"))
        val st = mutableListOf<String>(); val pt = mutableListOf<String>()
        fun <T> traced(values: List<T>, trace: MutableList<String>): Iterable<T> = Iterable {
            var i = 0
            object : Iterator<T> {
                override fun hasNext(): Boolean { trace.add("has:$i"); return i < values.size }
                override fun next(): T { trace.add("next:$i"); return values[i++] }
            }
        }
        val sr = source.getBestMatchResult(traced(sl, st)); val pr = port.getBestMatchResult(traced(pl, pt))
        assertEquals(st, pt); assertEquals(sourceResult(sr), portResult(pr))
        assertSame(sl[0], sr.desiredULocale); assertSame(pl[0], pr.getDesiredULocale())
        val supported = ULocale("en_US")
        val m = LocaleMatcher.builder().addSupportedULocale(supported).build()
        assertSame(supported, m.getBestMatch(ULocale("en")))
        assertSame(supported, m.getBestMatchResult(ULocale("en")).getSupportedULocale())
    }

    @Test fun javaLocaleOverloadsPrivateUseUndAndResultIdentity() {
        val locales = listOf(Locale.ROOT, Locale("und"), Locale.US, Locale.UK, Locale.forLanguageTag("sr-Latn"),
            Locale.forLanguageTag("und-x-abc"), Locale.forLanguageTag("en-XA"), Locale("en", "", "PSACCENT"),
            Locale.forLanguageTag("de-CH-1901-u-ca-buddhist-nu-arab"), Locale("no", "NO", "NY"))
        for (supported in listOf(locales, locales.reversed(), listOf(Locale.US, Locale.FRANCE))) {
            val sb = Source.builder().setSupportedLocales(supported).setDefaultLocale(Locale.JAPAN)
            val pb = LocaleMatcher.builder().setSupportedLocales(supported).setDefaultLocale(Locale.JAPAN)
            sb.setMaxDistance(Locale.US, Locale.UK); pb.setMaxDistance(Locale.US, Locale.UK)
            val source = sb.build(); val port = pb.build()
            assertEquals(source.toString(), port.toString())
            for (desired in locales) {
                assertEquals(source.getBestLocale(desired), port.getBestLocale(desired), "$supported/$desired")
                val sr = source.getBestLocaleResult(desired); val pr = port.getBestLocaleResult(desired)
                assertEquals(sourceResult(sr), portResult(pr), "$supported/$desired")
                if (pr.getDesiredIndex() == 0) assertSame(desired, pr.getDesiredLocale())
                for (s in locales) assertEquals(source.isMatch(desired, s), port.isMatch(desired, s), "$desired/$s")
            }
            assertEquals(source.getBestLocale(locales.reversed()), port.getBestLocale(locales.reversed()))
            assertEquals(sourceResult(source.getBestLocaleResult(locales.reversed())), portResult(port.getBestLocaleResult(locales.reversed())))
            assertEquals(sourceResult(source.getBestLocaleResult(emptyList<Locale>())), portResult(port.getBestLocaleResult(emptyList<Locale>())))
        }
    }

    @Test fun nullArgumentsAndFailureTimingMatchSource() {
        val sb = Source.builder(); val pb = LocaleMatcher.builder()
        assertEquals(outcome { sb.setSupportedULocales(null) }, outcome { pb.setSupportedULocales(null) })
        assertEquals(outcome { sb.setMaxDistance(null as SourceLocale?, SourceLocale.US) }, outcome { pb.setMaxDistance(null as ULocale?, ULocale.US) })
        assertEquals(outcome { sb.setMaxDistance(null as Locale?, Locale.US) }, outcome { pb.setMaxDistance(null as Locale?, Locale.US) })
        sb.addSupportedULocale(null); pb.addSupportedULocale(null)
        assertEquals(sb.toString(), pb.toString())
        assertEquals(outcome { sb.build() }, outcome { pb.build() })
        val source = Source("en"); val port = LocaleMatcher("en")
        assertEquals(outcome { source.getBestMatch(null as SourceLocale?) }, outcome { port.getBestMatch(null as ULocale?) })
        assertEquals(outcome { source.getBestMatch(null as Iterable<SourceLocale>?) }, outcome { port.getBestMatch(null as Iterable<ULocale>?) })
        assertEquals(outcome { source.getBestMatch(listOf(null, SourceLocale.US)) }, outcome { port.getBestMatch(listOf(null, ULocale.US)) })
        assertEquals(outcome { source.getBestLocale(null as Locale?) }, outcome { port.getBestLocale(null as Locale?) })
        assertEquals(outcome { source.canonicalize(null) }, outcome { port.canonicalize(null) })
        assertEquals(outcome { source.getBestMatchResult(listOf(SourceLocale.US, null)).desiredIndex }, outcome { port.getBestMatchResult(listOf(ULocale.US, null)).getDesiredIndex() })
    }
}

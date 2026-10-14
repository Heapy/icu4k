package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.*
import java.net.URLClassLoader
import java.nio.file.Files
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.*
import com.ibm.icu.util.ULocale as Original

class ULocaleCanonicalizationOracleTest {
    private fun outcome(block: () -> String): String = try { block() } catch (failure: Throwable) { "${failure.javaClass.simpleName}:${failure.message}" }
    private fun compare(input: String) {
        assertEquals(outcome { Original.canonicalize(input) }, outcome { ULocale.canonicalize(input) }, "$input static")
        assertEquals(outcome { Original.createCanonical(input).name }, outcome { ULocale.createCanonical(input).getName() }, "$input factory")
        assertEquals(outcome { Original.createCanonical(Original(input)).name }, outcome { ULocale.createCanonical(ULocale(input)).getName() }, "$input locale factory")
    }
    @Test fun everyAliasTableEntryAndContextMatchesOriginal() {
        val aliases = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata")["alias"]
        for (kind in listOf("language", "script", "territory", "variant", "subdivision")) {
            val table = aliases[kind]
            for (index in 0 until table.getSize()) {
                val key = table[index].getKey()!!
                val names = when (kind) {
                    "language" -> listOf(key, "$key@calendar=japanese", "$key@t=iw-il", "$key@sd=fi01")
                    "script" -> listOf("en_${key}_US", "und_$key", "sh_${key}_SU", "en_$key@t=sh-rs")
                    "territory" -> listOf("en_$key", "ru_$key", "hy_$key", "uk_$key", "und_$key", "sr_Latn_$key@currency=EUR")
                    "variant" -> listOf("ja__$key", "ja__HEPBURN_$key", "en_US_${key}_POSIX", "und__$key@calendar=japanese")
                    else -> listOf("en@sd=$key", "en@rg=$key", "en@sd=${key.uppercase()}", "iw_BU@sd=$key;rg=$key")
                }
                names.forEach(::compare)
            }
        }
    }
    @Test fun legacyNamesMalformedValuesAndTransformedExtensionsMatch() {
        val names = listOf("", "root", "C", "und", "UND", "und_", "und_Latn", "_US", "en_US.UTF-8", "en_US@euro", "nb_NO_NY", "no_NO_NY", "art__LOJBAN", "cel__GAULISH", "de__1901", "hy__AREVELA", "hy__AREVMDA", "zh__GUOYU", "zh__HAKKA", "zh__XIANG", "zh_GAN", "zh_MIN", "zh_MIN_NAN", "zh_WUU", "zh_YUE", "i-default", "i-enochian", "i-mingo", "en-u-ca-japanese", "ja__HEPBURN_HEPLOC", "ja__HEPLOC_HEPBURN", "sh_Cyrl_RS", "und_AQ", "zzzz_Zzzz_QQ", "en@a=x;sd=fi01;t=iw-il;rg=fi01")
        names.forEach(::compare)
        for (value in listOf("iw-il", "sh-rs", "en-us", "no-no-ny", "m0-names", "m0-ungegn-d0-name", "d0-name-m0-ungegn", "iw-il-m0-ungegn-d0-name", "und-d0-name", "x-test", "en-m0-foo-h0-hybrid", "m0-foo-", "m0-", "m0-foo--bar", "M0-FOO", "en_m0_foo", "a0-foo-a0-bar", "de-h0-hybrid-s0-foo-d0-name")) compare("en@t=$value")
    }
    @Test fun malformedMetadataAndAliasCycleDiagnosticsMatchSource() {
        val property = "com.ibm.icu.impl.ICUBinary.dataPath"
        val previous = System.getProperty(property)
        val directory = Files.createTempDirectory("icu4k-canonical-metadata")
        val metadataFile = directory.resolve("metadata.res")
        fun metadata(kind: String, entries: Map<String, String>): ByteArray {
            val tables = listOf("language", "script", "territory", "variant", "subdivision").associateWith { name ->
                if (name == kind) entries.mapValues { mapOf("replacement" to it.value) } else emptyMap()
            }
            return BundleFixture.write(mapOf("alias" to tables))
        }
        fun cold(original: Boolean, input: String): Pair<String, String?> {
            val urls = arrayOf(ULocale::class.java.protectionDomain.codeSource.location,
                Original::class.java.protectionDomain.codeSource.location,
                Unit::class.java.protectionDomain.codeSource.location)
            return URLClassLoader(urls, null).use { loader ->
                val cls = loader.loadClass(if (original) "com.ibm.icu.util.ULocale" else "io.github.heapy.icu4k.util.ULocale")
                val receiver = if (original) null else cls.getField("Companion").get(null)
                val method = (receiver?.javaClass ?: cls).getMethod("canonicalize", String::class.java)
                try { "value" to (method.invoke(receiver, input) as String) }
                catch (failure: InvocationTargetException) { failure.cause!!.javaClass.simpleName to failure.cause!!.message }
            }
        }
        try {
            System.setProperty(property, directory.toString())
            val cases = listOf(
                Triple("language", mapOf("zzza" to "zzzb", "zzzb" to "zzza"), "zzza"),
                Triple("language", mapOf("und_US" to "zzzb"), "und_US"),
                Triple("script", mapOf("bad" to "Latn"), "zzza"),
                Triple("territory", mapOf("TOOLONG" to "US"), "zzza"),
                Triple("variant", mapOf("bad" to "valid"), "zzza"),
                Triple("variant", mapOf("valid" to "bad"), "zzza"),
                Triple("subdivision", mapOf("ab" to "xy"), "zzza"),
                Triple("subdivision", mapOf("abc" to "x"), "zzza")
            )
            for ((kind, entries, input) in cases) {
                Files.write(metadataFile, metadata(kind, entries))
                val expected = cold(true, input)
                val actual = cold(false, input)
                assertEquals(expected, actual, "$kind/$entries/$input")
                if (kind == "language" && entries.size == 1) assertEquals("value" to "zzzb", actual)
                else assertEquals("IllegalArgumentException", actual.first)
            }
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
            Files.deleteIfExists(metadataFile)
            Files.deleteIfExists(directory)
        }
    }
    @Test fun coldConcurrentCanonicalizationPublishesCompleteAliasData() {
        val urls = arrayOf(ULocale::class.java.protectionDomain.codeSource.location,
            Original::class.java.protectionDomain.codeSource.location,
            Unit::class.java.protectionDomain.codeSource.location)
        URLClassLoader(urls, null).use { loader ->
            val cls = loader.loadClass("io.github.heapy.icu4k.util.ULocale")
            val companion = cls.getField("Companion").get(null)
            val method = companion.javaClass.getMethod("canonicalize", String::class.java)
            val executor = Executors.newFixedThreadPool(8)
            val start = CountDownLatch(1)
            try {
                val inputs = listOf("iw_IL", "sh_RS", "uk_SU", "en_Qaai_BU", "ja__HEPBURN_HEPLOC", "en@sd=fi01", "en@t=iw-il", "hy_SU")
                val jobs = inputs.map { input -> executor.submit<String> { start.await(); method.invoke(companion, input) as String } }
                start.countDown()
                assertEquals(inputs.map(Original::canonicalize), jobs.map { it.get() })
            } finally { executor.shutdownNow() }
        }
    }
}

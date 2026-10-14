package io.github.heapy.icu4k.impl

import kotlin.random.Random
import kotlin.test.*

class LocaleFallbackOracleTest {
    @Test fun pinnedMapsAndParentAlgorithmMatchOriginal() {
        val data = Class.forName("com.ibm.icu.impl.LocaleFallbackData")
        for ((name, port) in listOf("DEFAULT_SCRIPT_TABLE" to LocaleFallbackData.DEFAULT_SCRIPT_TABLE,
            "PARENT_LOCALE_TABLE" to LocaleFallbackData.PARENT_LOCALE_TABLE)) {
            val original = data.getDeclaredField(name).also { it.isAccessible = true }.get(null) as Map<*, *>
            assertEquals(original, port, name)
        }
        val examples = mutableListOf<Pair<String, String>>()
        for (name in LocaleFallbackData.PARENT_LOCALE_TABLE.keys + LocaleFallbackData.DEFAULT_SCRIPT_TABLE.keys) examples.add(name to name)
        val random = Random(78)
        val languages = listOf("en", "es", "zh", "sr", "az", "pa", "ff", "und", "zz", "", "sh", "iw")
        val scripts = listOf("", "Latn", "Cyrl", "Arab", "Hans", "Hant", "Deva", "Zzzz")
        val regions = listOf("", "US", "GB", "TW", "HK", "IR", "RS", "419", "ZZ")
        repeat(3000) {
            val language = languages.random(random)
            val script = scripts.random(random)
            val region = regions.random(random)
            var name = language + (if (script.isNotEmpty()) "_$script" else "") + (if (region.isNotEmpty()) "_$region" else "")
            if (random.nextInt(5) == 0) name += "_POSIX"
            if (random.nextInt(9) == 0) name += "_"
            val original = if (random.nextBoolean()) name else language + "_" + scripts.random(random) + "_" + regions.random(random)
            examples.add(name to original)
        }
        for ((name, original) in examples) for (mode in ICUResourceBundle.OpenType.entries) {
            val jmode = com.ibm.icu.impl.ICUResourceBundle.OpenType.valueOf(mode.name)
            assertEquals(com.ibm.icu.impl.ICUResourceBundle.getParentLocaleID(name, original, jmode),
                ICUResourceBundle.getParentLocaleID(name, original, mode), "$name/$original/$mode")
        }
    }
    @Test fun javaDefaultMapMutatorsAreAlsoRejected() {
        val map = LocaleFallbackData.DEFAULT_SCRIPT_TABLE as MutableMap<String, String>
        val key = map.keys.first()
        val value = map[key]!!
        assertFailsWith<UnsupportedOperationException> { map.putIfAbsent(key, value) }
        assertFailsWith<UnsupportedOperationException> { map.replace(key, value) }
        assertFailsWith<UnsupportedOperationException> { map.replace(key, value, value) }
        assertFailsWith<UnsupportedOperationException> { map.remove("missing", "missing") }
        assertFailsWith<UnsupportedOperationException> { map.computeIfAbsent(key) { error("callback must not run") } }
        assertFailsWith<UnsupportedOperationException> { map.computeIfPresent(key) { _, _ -> error("callback must not run") } }
        assertFailsWith<UnsupportedOperationException> { map.replaceAll { _, _ -> error("callback must not run") } }
        assertFailsWith<UnsupportedOperationException> { map.merge(key, value) { _, _ -> error("callback must not run") } }
    }

}

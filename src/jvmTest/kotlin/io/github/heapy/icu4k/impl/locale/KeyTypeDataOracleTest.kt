package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.KeyTypeData as J
import com.ibm.icu.util.Output as JO
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.Output
import kotlin.random.Random
import kotlin.test.*

class KeyTypeDataOracleTest {
    private fun check(key: String, type: String) {
        val jk = JO<Boolean>(); val js = JO<Boolean>(); val kk = Output<Boolean>(); val ks = Output<Boolean>()
        for (bcp in listOf(true, false)) {
            jk.value = true; js.value = true; kk.value = true; ks.value = true
            val expected = if (bcp) J.toBcpType(key, type, jk, js) else J.toLegacyType(key, type, jk, js)
            val actual = if (bcp) KeyTypeData.toBcpType(key, type, kk, ks) else KeyTypeData.toLegacyType(key, type, kk, ks)
            assertEquals(expected, actual, "$key/$type/$bcp")
            assertEquals(jk.value, kk.value); assertEquals(js.value, ks.value)
            assertEquals(if (bcp) J.toBcpType(key, type, null, null) else J.toLegacyType(key, type, null, null),
                if (bcp) KeyTypeData.toBcpType(key, type) else KeyTypeData.toLegacyType(key, type))
            val ja = JO<Boolean>(); val ka = Output<Boolean>()
            assertEquals(if (bcp) J.toBcpType(key, type, ja, ja) else J.toLegacyType(key, type, ja, ja),
                if (bcp) KeyTypeData.toBcpType(key, type, ka, ka) else KeyTypeData.toLegacyType(key, type, ka, ka))
            assertEquals(ja.value, ka.value)
        }
    }
    @Test fun allKeysTypesAliasesMetadataAndResourceMatchOriginal() {
        val jbytes = javaClass.classLoader.getResourceAsStream("com/ibm/icu/impl/data/icudata/keyTypeData.res")!!.use { it.readBytes() }
        assertContentEquals(jbytes, ICUData.getBytes("keyTypeData.res"))
        assertEquals(J.getBcp47Keys().toList(), KeyTypeData.getBcp47Keys().toList())
        val map = J::class.java.getDeclaredField("KEYMAP").apply { isAccessible = true }.get(null) as Map<*, *>
        for ((key, data) in map) {
            key as String
            assertEquals(J.toBcpKey(key), KeyTypeData.toBcpKey(key)); assertEquals(J.toLegacyKey(key), KeyTypeData.toLegacyKey(key))
            val types = data!!.javaClass.getDeclaredField("typeMap").apply { isAccessible = true }.get(data) as Map<*, *>
            for (type in types.keys) { check(key, type as String); check(key.uppercase(), type.uppercase()) }
            for (type in J.getBcp47KeyTypes(J.toBcpKey(key))) {
                assertEquals(J.isDeprecated(key, type), KeyTypeData.isDeprecated(key, type))
            }
        }
        for (key in J.getBcp47Keys() + listOf("", "unknown", "CA", "calendar")) {
            assertEquals(J.getBcp47KeyTypes(key)?.toList(), KeyTypeData.getBcp47KeyTypes(key)?.toList(), key)
            assertEquals(J.isDeprecated(key), KeyTypeData.isDeprecated(key), key)
            assertEquals(J.getValueType(key).name, KeyTypeData.getValueType(key).name, key)
        }
        for (set in listOf(KeyTypeData.getBcp47Keys(), KeyTypeData.getBcp47KeyTypes("ca")!!)) {
            val mutable = set as MutableSet<String>
            assertFailsWith<UnsupportedOperationException> { mutable.clear() }
            assertFailsWith<UnsupportedOperationException> { mutable.remove("missing") }
            assertFailsWith<UnsupportedOperationException> { mutable.addAll(emptySet()) }
            assertFailsWith<UnsupportedOperationException> { mutable.iterator().remove() }
        }
    }
    @Test fun specialTypePatternsAcceptAndRejectExactlyAsOriginal() {
        val keys = listOf("vt", "kr", "rg", "dx", "sd", "x0", "calendar", "unknown")
        val candidates = listOf("", "abcd", "ABCD-123456", "123", "1234567", "10ffff-ffffff", "Latn-Grek", "space-punct", "abc-defghijk", "uszzzz", "419ZZZZ",
            "us", "419", "usca", "abc-def", "ab", "foo-bar", "foo--bar", "abc\n", "İabc", "😀", "abcd-", "-abcd")
        for (key in keys) for (type in candidates) check(key, type)
        val random = Random(811752)
        repeat(3000) {
            val type = buildString { repeat(random.nextInt(20)) { append("abcXYZ019-fzİ_\n".random(random)) } }
            for (key in keys) check(key, type)
        }
        for (value in listOf<Any?>(null, true, 17, "value")) {
            val j = JO(value); val k = Output(value)
            assertEquals(j.toString(), k.toString()); j.value = "changed"; k.value = "changed"
            assertEquals(j.toString(), k.toString())
        }
    }
}

package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.UnicodeLocaleExtension as J
import kotlin.random.Random
import kotlin.test.*

class UnicodeLocaleExtensionOracleTest {
    private fun original(attributes: java.util.SortedSet<String>?, keywords: java.util.SortedMap<String, String>?): J =
        J::class.java.getDeclaredConstructor(java.util.SortedSet::class.java, java.util.SortedMap::class.java).apply { isAccessible = true }.newInstance(attributes, keywords)
    private fun compare(j: J, k: UnicodeLocaleExtension) {
        assertEquals(j.key, k.getKey()); assertEquals(j.value, k.getValue()); assertEquals(j.id, k.getID()); assertEquals(j.toString(), k.toString())
        assertEquals(j.unicodeLocaleAttributes.toList(), k.getUnicodeLocaleAttributes().toList())
        assertEquals(j.unicodeLocaleKeys.toList(), k.getUnicodeLocaleKeys().toList())
        for (key in j.unicodeLocaleKeys + listOf("missing", "CA", "ca", "NU")) assertEquals(j.getUnicodeLocaleType(key), k.getUnicodeLocaleType(key))
    }
    @Test fun serializationOrderingBackingOwnershipAndConstantsMatchOriginal() {
        compare(J.CA_JAPANESE, UnicodeLocaleExtension.CA_JAPANESE); compare(J.NU_THAI, UnicodeLocaleExtension.NU_THAI)
        compare(original(null, null), UnicodeLocaleExtension(null, null))
        val random = Random(188399)
        for (reverse in listOf(false, true)) repeat(100) {
            val order = if (reverse) reverseOrder<String>() else naturalOrder()
            val ja = java.util.TreeSet(order); val jk = java.util.TreeMap<String, String>(order)
            val ka = linkedSetOf<String>(); val kk = linkedMapOf<String, String>()
            repeat(random.nextInt(1, 8)) { val s = listOf("abc", "def", "ghi", "jkl").random(random); ja.add(s); ka.add(s) }
            repeat(random.nextInt(1, 8)) { val key = listOf("ca", "nu", "co", "kf").random(random); val value = listOf("", "true", "japanese", "foo-bar").random(random); jk[key] = value; kk[key] = value }
            val j = original(ja, jk); val k = UnicodeLocaleExtension(ka, kk, order, order)
            compare(j, k)
            val jaView = j.unicodeLocaleAttributes; val kaView = k.getUnicodeLocaleAttributes()
            val jkView = j.unicodeLocaleKeys; val kkView = k.getUnicodeLocaleKeys()
            ja.add("new"); ka.add("new"); jk["zz"] = "new"; kk["zz"] = "new"
            assertEquals(jaView.toList(), kaView.toList()); assertEquals(jkView.toList(), kkView.toList())
            // The views change, while the serialized extension value remains cached.
            compare(j, k)
            ja.clear(); ka.clear(); jk.clear(); kk.clear(); compare(j, k)
        }
        val ja = java.util.TreeSet<String>(); val jk = java.util.TreeMap<String, String>()
        val ka = mutableSetOf<String>(); val kk = mutableMapOf<String, String>()
        val j = original(ja, jk); val k = UnicodeLocaleExtension(ka, kk)
        ja.add("abc"); ka.add("abc"); jk["ca"] = "japanese"; kk["ca"] = "japanese"
        compare(j, k) // Initially empty backing collections are not adopted.
    }
    @Test fun readonlyViewsRejectEveryMutationEvenWhenEmpty() {
        for (entry in listOf(original(null, null) to UnicodeLocaleExtension(null, null), J.CA_JAPANESE to UnicodeLocaleExtension.CA_JAPANESE)) {
            for ((j, k) in listOf(entry.first.unicodeLocaleAttributes to entry.second.getUnicodeLocaleAttributes(), entry.first.unicodeLocaleKeys to entry.second.getUnicodeLocaleKeys())) {
                val a = j as MutableSet<String>; val b = k as MutableSet<String>
                val operations: List<(MutableSet<String>) -> Unit> = listOf({ it.add("x") }, { it.addAll(emptyList()) }, { it.remove("missing") },
                    { it.removeAll(emptySet()) }, { it.retainAll(emptySet()) }, { it.clear() }, { it.iterator().remove() })
                for (operation in operations) {
                    assertFailsWith<UnsupportedOperationException> { operation(a) }
                    assertFailsWith<UnsupportedOperationException> { operation(b) }
                }
            }
        }
    }
    @Test fun predicatesMatchOriginalForAllUnitsAndRandomTypes() {
        for (code in 0..0xffff) assertEquals(J.isSingletonChar(code.toChar()), UnicodeLocaleExtension.isSingletonChar(code.toChar()))
        val random = Random(918012)
        val strings = listOf("", "abc", "ab-cde", "abc-def", "-abc", "abc-", "abc--def", "İ23", "1a", "a1") +
            List(3000) { buildString { repeat(random.nextInt(16)) { append("aAzZ09-İ_😀".random(random)) } } }
        for (s in strings) {
            assertEquals(J.isAttribute(s), UnicodeLocaleExtension.isAttribute(s), s); assertEquals(J.isKey(s), UnicodeLocaleExtension.isKey(s), s)
            assertEquals(J.isTypeSubtag(s), UnicodeLocaleExtension.isTypeSubtag(s), s); assertEquals(J.isType(s), UnicodeLocaleExtension.isType(s), s)
        }
        val constructor = com.ibm.icu.impl.locale.Extension::class.java.getDeclaredConstructor(Char::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
        for (value in listOf(null, "", "ABC", "foo-bar")) {
            val j = constructor.newInstance('a', value); val k = Extension('a', value)
            assertEquals(j.key, k.getKey()); assertEquals(j.value, k.getValue()); assertEquals(j.id, k.getID()); assertEquals(j.toString(), k.toString())
        }
    }
}

package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetCollectionOracleTest {
    @Test fun collectionAndCodePointOverloadsMatchIcu() {
        val random = Random(117)
        repeat(600) {
            val items = List(random.nextInt(20)) { CharArray(random.nextInt(5)) { random.nextInt(65, 75).toChar() }.concatToString() }
            val other = items.filter { random.nextBoolean() } + "😀"
            val j = JavaSet().addAll(items); val k = UnicodeSet().addAll(items)
            assertEquals(j.toPattern(true), k.toPattern(true))
            assertEquals(j.containsAll(other), k.containsAll(other))
            assertEquals(j.containsNone(other), k.containsNone(other))
            assertEquals(j.containsSome(other), k.containsSome(other))
            assertEquals(JavaSet(j).retainAll(other).toPattern(true), UnicodeSet(k).retainAll(other).toPattern(true))
            assertEquals(JavaSet(j).removeAll(other).toPattern(true), UnicodeSet(k).removeAll(other).toPattern(true))
            val text = items.joinToString("") + "😀😀"
            assertEquals(JavaSet.fromAll(text).toPattern(true), UnicodeSet.fromAll(text).toPattern(true))
            assertEquals(JavaSet(j).addAll(text).toPattern(true), UnicodeSet(k).addAll(text).toPattern(true))
            assertEquals(JavaSet(j).retainAll(text).toPattern(true), UnicodeSet(k).retainAll(text).toPattern(true))
            assertEquals(JavaSet(j).removeAll(text).toPattern(true), UnicodeSet(k).removeAll(text).toPattern(true))
            assertEquals(JavaSet(j).complementAll(text).toPattern(true), UnicodeSet(k).complementAll(text).toPattern(true))
        }
    }
}

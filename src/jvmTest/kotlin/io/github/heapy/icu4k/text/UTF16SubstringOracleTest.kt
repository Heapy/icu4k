package io.github.heapy.icu4k.text

import com.ibm.icu.text.UTF16 as J
import kotlin.random.Random
import kotlin.test.*

class UTF16SubstringOracleTest {
    private fun check(expected: () -> Any, actual: () -> Any) {
        val j = runCatching(expected); val k = runCatching(actual)
        if (j.isSuccess) assertEquals(j.getOrThrow(), k.getOrThrow())
        else {
            assertTrue(j.exceptionOrNull() is IndexOutOfBoundsException)
            assertTrue(k.exceptionOrNull() is IndexOutOfBoundsException)
        }
    }
    @Test fun substringSearchAndReplacementRetainOriginalBoundaryBehavior() {
        val random = Random(581939)
        val atoms = listOf("", "A", "B", "😀", "\ud800", "\udc00", "\ud83d", "\ude00")
        val strings = atoms.toMutableList()
        repeat(300) { strings += buildString { repeat(random.nextInt(1, 8)) { append(atoms.random(random)) } } }
        for (source in strings) for (needle in atoms + List(5) { strings.random(random) }) {
            check({ J.indexOf(source, needle) }, { UTF16.indexOf(source, needle) })
            check({ J.lastIndexOf(source, needle) }, { UTF16.lastIndexOf(source, needle) })
            for (from in listOf(-5, 0, 1, source.length - 1, source.length, source.length + 2, Int.MAX_VALUE)) {
                check({ J.indexOf(source, needle, from) }, { UTF16.indexOf(source, needle, from) })
                check({ J.lastIndexOf(source, needle, from) }, { UTF16.lastIndexOf(source, needle, from) })
            }
            val replacement = atoms.random(random)
            check({ J.replace(source, needle, replacement) }, { UTF16.replace(source, needle, replacement) })
        }
    }
}

package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CaseMapImpl as JavaCaseMapImpl
import kotlin.random.Random
import kotlin.test.*

class CaseMapContextOracleTest {
    @Test fun arbitraryUtf16AndInterleavedContextOperationsMatchIcu() {
        repeat(100) { seed ->
            val random = Random(seed)
            val text = CharArray(80) {
                when (random.nextInt(4)) {
                    0 -> random.nextInt(0xd800, 0xe000).toChar()
                    else -> random.nextInt(0x10000).toChar()
                }
            }.concatToString()
            val k = CaseMapImpl.StringContextIterator(text)
            val j = JavaCaseMapImpl.StringContextIterator(text)
            repeat(300) {
                when (random.nextInt(8)) {
                    0 -> {
                        val n = random.nextInt(-2, text.length + 3)
                        k.setLimit(n); j.setLimit(n)
                    }
                    1 -> { k.moveToLimit(); j.moveToLimit() }
                    2 -> {
                        val n = random.nextInt(text.length + 1)
                        k.moveTo(n); j.moveTo(n)
                    }
                    3 -> {
                        val a = random.nextInt(text.length + 1)
                        val b = random.nextInt(a, text.length + 1)
                        k.setCPStartAndLimit(a, b); j.setCPStartAndLimit(a, b)
                    }
                    4 -> {
                        val n = random.nextInt(-2, 3)
                        k.reset(n); j.reset(n)
                    }
                    5 -> assertEquals(j.nextCaseMapCP(), k.nextCaseMapCP())
                    else -> assertEquals(j.next(), k.next())
                }
                assertEquals(j.cpStart, k.getCPStart())
                assertEquals(j.cpLimit, k.getCPLimit())
                assertEquals(j.cpLength, k.getCPLength())
            }
        }
    }

    @Test fun titleOptionConflictsMatchIcu() {
        for (options in 0..0x7ff) {
            for (option in listOf(0, 0x20, 0x40, 0x200, 0x400)) {
                val j = try { JavaCaseMapImpl.addTitleIteratorOption(options, option) } catch (_: IllegalArgumentException) { null }
                if (j == null) assertFailsWith<IllegalArgumentException> { CaseMapImpl.addTitleIteratorOption(options, option) }
                else assertEquals(j, CaseMapImpl.addTitleIteratorOption(options, option))
                val a = try { JavaCaseMapImpl.addTitleAdjustmentOption(options, option) } catch (_: IllegalArgumentException) { null }
                if (a == null) assertFailsWith<IllegalArgumentException> { CaseMapImpl.addTitleAdjustmentOption(options, option) }
                else assertEquals(a, CaseMapImpl.addTitleAdjustmentOption(options, option))
            }
        }
    }
}

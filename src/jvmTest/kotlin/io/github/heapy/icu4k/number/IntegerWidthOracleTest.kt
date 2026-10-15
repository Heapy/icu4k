package io.github.heapy.icu4k.number

import com.ibm.icu.number.IntegerWidth as JWidth
import kotlin.test.*

class IntegerWidthOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { "${e.javaClass.simpleName}:${e.message}" }
    private fun fields(value: Any): List<Int> = listOf("minInt", "maxInt").map { value.javaClass.getDeclaredField(it).apply { isAccessible = true }.getInt(value) }
    @Test fun allBoundaryConfigurationsAndReturnIdentityMatchSource() {
        val inputs = listOf(Int.MIN_VALUE, -2, -1, 0, 1, 2, 3, 998, 999, 1000, Int.MAX_VALUE)
        for (min in inputs) {
            assertEquals(outcome { fields(JWidth.zeroFillTo(min)) }, outcome { fields(IntegerWidth.zeroFillTo(min)) }, "$min/create")
            val j = try { JWidth.zeroFillTo(min) } catch (_: Exception) { continue }
            val k = IntegerWidth.zeroFillTo(min)
            for (max in inputs) {
                assertEquals(outcome { fields(j.truncateAt(max)) }, outcome { fields(k.truncateAt(max)) }, "$min/$max")
                val jt = try { j.truncateAt(max) } catch (_: Exception) { continue }
                val kt = k.truncateAt(max)
                assertEquals(jt === j, kt === k, "$min/$max/self")
                assertEquals(jt.truncateAt(-1) === JWidth.zeroFillTo(1), kt.truncateAt(-1) === IntegerWidth.zeroFillTo(1), "$min/$max/default")
            }
            assertEquals(JWidth.zeroFillTo(min) === JWidth.zeroFillTo(min), IntegerWidth.zeroFillTo(min) === IntegerWidth.zeroFillTo(min), "$min/cache")
        }
    }
}

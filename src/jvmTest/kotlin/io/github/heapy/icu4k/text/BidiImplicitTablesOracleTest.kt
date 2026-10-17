// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiImplicitTablesOracleTest {
    @Test fun tablesActionsAndPairSharingMatchSource() {
        val source = com.ibm.icu.text.Bidi::class.java
        val port = BidiImplicitTables::class.java
        fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        fun contents(value: Any?): Any? = when (value) {
            is ByteArray -> value.toList()
            is ShortArray -> value.toList()
            is Array<*> -> value.map(::contents)
            else -> value?.let { listOf(contents(field(it.javaClass, "imptab").get(it)), contents(field(it.javaClass, "impact").get(it))) }
        }
        val fields = source.declaredFields.filter {
            it.name == "groupProp" || it.name == "impTabProps" || it.name.startsWith("impAct") ||
                it.name.startsWith("impTabL_") || it.name.startsWith("impTabR_") || it.name.startsWith("impTab_")
        }
        assertEquals(25, fields.size)
        for (f in fields) {
            f.isAccessible = true
            assertEquals(contents(f.get(null)), contents(field(port, f.name).get(null)), f.name)
        }
        for (f in fields.filter { it.name.startsWith("impTab_") }) {
            val s = f.get(null); val p = field(port, f.name).get(null)
            for (member in listOf("imptab", "impact")) {
                val sa = field(s.javaClass, member).get(s) as Array<*>
                val pa = field(p.javaClass, member).get(p) as Array<*>
                for (i in 0..1) for (a in fields.filter { it.type.isArray }) {
                    assertEquals(a.get(null) === sa[i], field(port, a.name).get(null) === pa[i], "${f.name}.$member[$i] ${a.name}")
                }
            }
        }
    }
}

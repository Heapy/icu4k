package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterProperty as JavaProps
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class PropertySourcesOracleTest {
    @Test fun sourceIdsAndAllBoundarySetsMatchOriginal() {
        val method = JavaProps::class.java.getDeclaredMethod("getSource", Int::class.javaPrimitiveType).apply { isAccessible = true }
        for (id in -10..0x8000) assertEquals(method.invoke(JavaProps.INSTANCE, id), UCharacterProperty.INSTANCE.getSource(id), "$id")
        for (name in listOf("addPropertyStarts", "upropsvec_addPropertyStarts", "ublock_addPropertyStarts", "mathCompat_addPropertyStarts", "mcm_addPropertyStarts")) {
            val j = JavaSet().add(0x10ffff); val k = UnicodeSet().add(0x10ffff)
            val m = JavaProps::class.java.getDeclaredMethod(name, JavaSet::class.java).apply { isAccessible = true }
            m.invoke(JavaProps.INSTANCE, j)
            when (name) {
                "addPropertyStarts" -> assertSame(k, UCharacterProperty.INSTANCE.addPropertyStarts(k))
                "upropsvec_addPropertyStarts" -> UCharacterProperty.INSTANCE.upropsvec_addPropertyStarts(k)
                "ublock_addPropertyStarts" -> UCharacterProperty.INSTANCE.ublock_addPropertyStarts(k)
                "mathCompat_addPropertyStarts" -> UCharacterProperty.mathCompat_addPropertyStarts(k)
                else -> UCharacterProperty.mcm_addPropertyStarts(k)
            }
            assertEquals(j.toPattern(true), k.toPattern(true), name)
        }
        val layout = JavaProps::class.java.getDeclaredMethod("ulayout_addPropertyStarts", Int::class.javaPrimitiveType, JavaSet::class.java).apply { isAccessible = true }
        for (src in listOf(UCharacterProperty.SRC_INPC, UCharacterProperty.SRC_INSC, UCharacterProperty.SRC_VO)) {
            val j = JavaSet(); val k = UnicodeSet()
            layout.invoke(null, src, j)
            assertSame(k, UCharacterProperty.ulayout_addPropertyStarts(src, k))
            assertEquals(j.toPattern(true), k.toPattern(true), "layout $src")
        }
    }
}

package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class UnicodeSetPropertyIntegrationTest {
    @Test fun caseClosuresAndPropertyStartsUseCommonSet() {
        val set = UnicodeSet().add("existing")
        UCaseProps.INSTANCE.addCaseClosure(0xdf, set)
        assertTrue(set.contains("ss")); assertTrue(set.contains("existing"))
        set.clear()
        assertTrue(UCaseProps.INSTANCE.addStringCaseClosure("ss", set))
        assertTrue(set.contains(0xdf))
        set.clear()
        UCaseProps.INSTANCE.addSimpleCaseClosure(65, set)
        assertTrue(set.contains(97))
        set.clear()
        UCaseProps.INSTANCE.addPropertyStarts(set)
        assertTrue(set.contains(0)); assertTrue(set.size() > 100)
        set.clear()
        UBiDiProps.INSTANCE.addPropertyStarts(set)
        assertTrue(set.contains(0)); assertTrue(set.size() > 100)
    }
}

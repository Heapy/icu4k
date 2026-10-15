// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormat
import com.ibm.icu.text.RbnfSourceTestProcessor as JProcessor
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RuleBasedNumberFormatPostProcessorOracleTest {
    private fun source(flags: String = "") = JFormat("%%post-process: com.ibm.icu.text.RbnfSourceTestProcessor;$flags %main: 0: main; %other: 0: other;", JLocale.ENGLISH).also { it.setDefaultRuleSet("%main") }
    private fun target(flags: String = "") = RuleBasedNumberFormat("%%post-process: io.github.heapy.icu4k.text.RbnfTargetTestProcessor;$flags %main: 0: main; %other: 0: other;", ULocale.ENGLISH).also { it.setDefaultRuleSet("%main") }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) { "${e.javaClass.simpleName}:${e.message}" }
    @Test fun initReceivesInvokedFacadeAndCloneSharesOnlyAnExistingProcessor() {
        JProcessor.initializations = 0; RbnfTargetTestProcessor.initializations = 0
        val s = source(); val t = target()
        val sc = s.clone(); val tc = t.clone()
        assertEquals(s.getDefaultRuleSetName(), t.getDefaultRuleSetName())
        assertEquals(sc.format(0L), tc.format(0L))
        assertSame(sc, JProcessor.lastOwner); assertSame(tc, RbnfTargetTestProcessor.lastOwner)
        assertEquals("%main", s.defaultRuleSetName); assertEquals("%main", t.getDefaultRuleSetName())
        assertEquals(s.format(0L), t.format(0L))
        assertSame(s, JProcessor.lastOwner); assertSame(t, RbnfTargetTestProcessor.lastOwner)
        assertEquals(2, JProcessor.initializations); assertEquals(2, RbnfTargetTestProcessor.initializations)
        val sharedSource = s.clone(); val sharedTarget = t.clone()
        sharedSource.setDefaultRuleSet("%main"); sharedTarget.setDefaultRuleSet("%main")
        assertEquals(sharedSource.format(0L), sharedTarget.format(0L))
        assertEquals(2, RbnfTargetTestProcessor.initializations)
    }
    @Test fun discoveryInitAndProcessFailuresKeepSourcePolicy() {
        for (flags in listOf("initFailure;", "processFailure;")) {
            val s = source(flags); val t = target(flags)
            repeat(3) { assertEquals(outcome { s.format(0L) }, outcome { t.format(0L) }, flags) }
        }
        JProcessor.failConstructor = true; RbnfTargetTestProcessor.failConstructor = true
        try {
            val s = source(); val t = target()
            assertEquals(outcome { s.format(0L) }, outcome { t.format(0L) })
            JProcessor.failConstructor = false; RbnfTargetTestProcessor.failConstructor = false
            assertEquals(s.format(0L), t.format(0L)) // Errors were not swallowed or used to disable discovery.
        } finally { JProcessor.failConstructor = false; RbnfTargetTestProcessor.failConstructor = false }
    }
}

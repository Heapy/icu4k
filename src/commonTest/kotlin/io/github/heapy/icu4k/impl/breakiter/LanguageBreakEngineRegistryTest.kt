package io.github.heapy.icu4k.impl.breakiter

import kotlin.test.*

class LanguageBreakEngineRegistryTest {
    @Test fun originalDispatchAndFallbackPrecedence() {
        val registry = LanguageBreakEngineRegistry()
        assertIs<LSTMBreakEngine>(registry.get(0x0e01))
        assertIs<LSTMBreakEngine>(registry.get(0x1000))
        assertIs<LaoBreakEngine>(registry.get(0x0e81))
        assertIs<KhmerBreakEngine>(registry.get(0x1780))
        assertIs<CjkBreakEngine>(registry.get(0x4e00))
        assertSame(registry.get(0x4e00), registry.get(0x3042))
        assertSame(registry.get(0x3042), registry.get(0x30ab))
        assertNotSame(registry.get(0x4e00), registry.get(0xac00))
        assertSame(registry.get(65), registry.get(0x3a3))
        // If Common was first assigned to the fallback, it retains precedence over CJ.
        val before = LanguageBreakEngineRegistry()
        val fallback = before.get(0x30fc)
        before.get(0x4e00)
        assertSame(fallback, before.get(0x30fc))
        val after = LanguageBreakEngineRegistry()
        val cj = after.get(0x4e00)
        assertSame(cj, after.get(0x30fc))
    }
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.MissingResourceException
import kotlin.concurrent.atomics.AtomicReference

/** Rule iterator engine selection, published as one immutable Common snapshot. */
internal class LanguageBreakEngineRegistry(private val useMlPhraseBreaking: Boolean = false) {
    private class State(val engines: List<LanguageBreakEngine>, val unhandled: UnicodeSet)
    private val state = AtomicReference(State(emptyList(), UnicodeSet()))
    private val fallback = UnhandledBreakEngine { state.load().unhandled }

    fun get(c: Int): LanguageBreakEngine? {
        var candidate: LanguageBreakEngine? = null
        var scriptSet: UnicodeSet? = null
        while (true) {
            val original = state.load()
            if (original.unhandled.contains(c)) return fallback
            for (engine in original.engines) if (engine.handles(c)) return engine
            var script = UCharacter.getIntPropertyValue(c, UProperty.SCRIPT)
            if (script == UScript.HIRAGANA || script == UScript.KATAKANA) script = UScript.HAN
            if (candidate == null && scriptSet == null) {
                try {
                    candidate = when (script) {
                        UScript.THAI -> try { LSTMBreakEngine.create(script, LSTMBreakEngine.createData(script)!!) }
                            catch (_: MissingResourceException) { ThaiBreakEngine() }
                        UScript.MYANMAR -> try { LSTMBreakEngine.create(script, LSTMBreakEngine.createData(script)!!) }
                            catch (_: MissingResourceException) { BurmeseBreakEngine() }
                        UScript.LAO -> LaoBreakEngine()
                        UScript.KHMER -> KhmerBreakEngine()
                        UScript.HAN -> CjkBreakEngine(false, useMlPhraseBreaking)
                        UScript.HANGUL -> CjkBreakEngine(true, useMlPhraseBreaking)
                        else -> null
                    }
                } catch (_: ICUUncheckedIOException) { return null }
                if (candidate == null) scriptSet = UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT, script)
            }
            val updated = if (candidate != null) State(original.engines + candidate, original.unhandled)
                else State(original.engines, UnicodeSet(original.unhandled).addAll(scriptSet!!))
            if (state.compareAndSet(original, updated)) return candidate ?: fallback
        }
    }
    companion object {
        /** Shared default registry for iterator owners; configurable registries remain independent. */
        val shared = LanguageBreakEngineRegistry()
    }
}

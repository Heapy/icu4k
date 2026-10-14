// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.impl.RBBIRuleEngine
import io.github.heapy.icu4k.impl.breakiter.LanguageBreakEngineRegistry
import io.github.heapy.icu4k.impl.breakiter.RBBIBreakCache
import io.github.heapy.icu4k.impl.breakiter.RBBIDictionaryCache
import io.github.heapy.icu4k.util.BinaryBuffer

/** Rule compilation and boundary iteration. Locale factories are being ported. */
class RuleBasedBreakIterator private constructor(private val data: RBBIDataWrapper, private val phraseBreaking: Boolean,
                                                  private val registry: LanguageBreakEngineRegistry) : BreakIterator() {
    /** Compiles ICU break-rule syntax into a reusable boundary iterator. */
    constructor(rules: String) : this(RBBIDataWrapper.get(BinaryBuffer(compileRules(rules))), false, LanguageBreakEngineRegistry.shared)

    private var engine = RBBIRuleEngine(data, StringCharacterIterator(""))
    private var dictionary = RBBIDictionaryCache(engine.text, data, registry::get, phraseBreaking)
    private var cache = RBBIBreakCache(engine, dictionary)
    override fun clone(): RuleBasedBreakIterator = RuleBasedBreakIterator(data, phraseBreaking, registry).also {
        copyLocaleMetadataTo(it)
        it.engine = engine.copyFor(engine.text.clone())
        it.dictionary = dictionary.copyFor(it.engine.text, registry::get)
        it.cache = cache.copyFor(it.engine, it.dictionary)
    }
    override fun first(): Int {
        engine.text.first()
        val start = engine.text.getIndex()
        if (!cache.seek(start)) cache.populateNear(start)
        cache.current()
        return engine.position
    }
    override fun last(): Int {
        val end = engine.text.getEndIndex()
        isBoundary(end)
        return end
    }
    override fun next(n: Int): Int {
        var result = 0
        var remaining = n
        if (remaining > 0) while (remaining > 0 && result != DONE) { result = next(); remaining-- }
        else if (remaining < 0) while (remaining < 0 && result != DONE) { result = previous(); remaining++ }
        else result = current()
        return result
    }
    override fun next(): Int { cache.next(); return if (engine.done) DONE else engine.position }
    override fun previous(): Int { cache.previous(); return if (engine.done) DONE else engine.position }
    override fun following(offset: Int): Int {
        if (offset < engine.text.getBeginIndex()) return first()
        val start = RBBIRuleEngine.setIndex32(engine.text, offset)
        cache.following(start)
        return if (engine.done) DONE else engine.position
    }
    override fun preceding(offset: Int): Int {
        if (offset > engine.text.getEndIndex()) return last()
        if (offset < engine.text.getBeginIndex()) return DONE
        // ICU4J deliberately does not adjust a trail-surrogate offset here.
        cache.preceding(offset)
        return if (engine.done) DONE else engine.position
    }
    override fun isBoundary(offset: Int): Boolean {
        checkOffset(offset, engine.text)
        val adjusted = RBBIRuleEngine.setIndex32(engine.text, offset)
        var result = false
        if (cache.seek(adjusted) || cache.populateNear(adjusted)) result = cache.current() == offset
        if (!result) next()
        return result
    }
    override fun current(): Int = engine.position
    override fun getRuleStatus(): Int = data.fStatusTable[engine.ruleStatusIndex + data.fStatusTable[engine.ruleStatusIndex]]
    override fun getRuleStatusVec(fillInArray: IntArray?): Int {
        val count = data.fStatusTable[engine.ruleStatusIndex]
        if (fillInArray != null) for (i in 0 until minOf(count, fillInArray.size)) {
            fillInArray[i] = data.fStatusTable[engine.ruleStatusIndex + i + 1]
        }
        return count
    }
    override fun getText(): CharacterIterator = engine.text
    override fun setText(newText: CharacterIterator) {
        cache.reset(newText.getBeginIndex(), 0)
        dictionary.reset()
        engine.text = newText
        dictionary.setText(newText)
        first()
    }
    override fun equals(other: Any?): Boolean = this === other || (other is RuleBasedBreakIterator &&
        data.fRuleSource == other.data.fRuleSource && engine.text == other.engine.text && engine.position == other.engine.position)
    override fun hashCode(): Int = data.fRuleSource.hashCode()
    override fun toString(): String = data.fRuleSource
    companion object {
        /** Compiles rules to ICU binary data without platform stream dependencies. */
        fun compileRules(rules: String): ByteArray = RBBIRuleBuilder.compileRules(rules)

        fun getInstanceFromCompiledRules(bytes: BinaryBuffer): RuleBasedBreakIterator =
            RuleBasedBreakIterator(RBBIDataWrapper.get(bytes), false, LanguageBreakEngineRegistry.shared)
        fun getInstanceFromCompiledRules(bytes: BinaryBuffer, phraseBreaking: Boolean,
                                         useMlPhraseBreaking: Boolean = false): RuleBasedBreakIterator =
            RuleBasedBreakIterator(RBBIDataWrapper.get(bytes), phraseBreaking,
                if (useMlPhraseBreaking) LanguageBreakEngineRegistry(true) else LanguageBreakEngineRegistry.shared)
        protected fun checkOffset(offset: Int, text: CharacterIterator) {
            if (offset < text.getBeginIndex() || offset > text.getEndIndex()) throw IndexOutOfBoundsException(offset.toString())
        }
    }
}

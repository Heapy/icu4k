// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.createTextTrieMapLock

/** A transliterator backed by parsed ICU rules. Instances are created through Transliterator. */
open class RuleBasedTransliterator internal constructor(id: String?, private val data: TransliterationData, filter: UnicodeFilter?) : Transliterator(id,filter) {
    private val dataLock=data.lock
    init {setMaximumContextLength(data.ruleSet.getMaximumContextLength())}
    override fun handleTransliterate(text: Replaceable, pos: Position, incremental: Boolean) = dataLock.withLock {data.ruleSet.handleTransliterate(text,pos,incremental)}
    override fun toRules(escapeUnprintable: Boolean): String = data.ruleSet.toRules(escapeUnprintable)
    override fun addSourceTargetSet(inputFilter: UnicodeSet, sourceSet: UnicodeSet, targetSet: UnicodeSet) = data.ruleSet.addSourceTargetSet(inputFilter,sourceSet,targetSet)
    open fun safeClone(): Transliterator = RuleBasedTransliterator(getID(),data,(getFilter() as UnicodeSet?)?.let(::UnicodeSet))
}

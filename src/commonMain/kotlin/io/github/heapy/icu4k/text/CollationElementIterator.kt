// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.CollationElementIteratorCore
import io.github.heapy.icu4k.impl.coll.collationIteratorClassAssertionsEnabled
import kotlin.jvm.JvmStatic

/** Bidirectional legacy 32-bit CE cursor, created by RuleBasedCollator. */
class CollationElementIterator private constructor(private val rbc: RuleBasedCollator) {
    private val core = CollationElementIteratorCore(rbc,{ rbc.data },{ rbc.settings.readOnly() },{ rbc.tailoring.maxExpansions },assertionsEnabled)
    internal constructor(source: String?, collator: RuleBasedCollator) : this(collator) { setText(source) }
    internal constructor(source: CharacterIterator?, collator: RuleBasedCollator) : this(collator) { setText(source) }
    internal constructor(source: UCharacterIterator?, collator: RuleBasedCollator) : this(collator) { setText(source) }
    fun getOffset(): Int = core.getOffset()
    fun next(): Int = core.next()
    fun previous(): Int = core.previous()
    fun reset() = core.reset()
    fun setOffset(newOffset: Int) = core.setOffset(newOffset)
    fun setText(source: String?) = core.setText(source)
    fun setText(source: UCharacterIterator?) = core.setText(source)
    fun setText(source: CharacterIterator?) = core.setText(source)
    fun getMaxExpansion(ce: Int): Int = core.getMaxExpansion(ce)
    override fun equals(other: Any?): Boolean = this === other || (other is CollationElementIterator && core == other.core)
    override fun hashCode(): Int = core.hashCode()
    @Deprecated("This API is ICU internal only.") fun getRuleBasedCollator(): RuleBasedCollator = rbc
    companion object {
        private val assertionsEnabled = collationIteratorClassAssertionsEnabled(CollationElementIterator::class)
        const val NULLORDER = -1
        const val IGNORABLE = 0
        @JvmStatic fun primaryOrder(ce: Int): Int = CollationElementIteratorCore.primaryOrder(ce)
        @JvmStatic fun secondaryOrder(ce: Int): Int = CollationElementIteratorCore.secondaryOrder(ce)
        @JvmStatic fun tertiaryOrder(ce: Int): Int = CollationElementIteratorCore.tertiaryOrder(ce)
    }
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal class CompoundTransliterator private constructor(id: String?, filter: UnicodeFilter?, private val elements: Array<Transliterator>, private val passes: Int) : Transliterator(id,filter) {
    constructor(elements: List<Transliterator>, passes: Int=0) : this("",null,elements.toTypedArray(),passes) {
        setMaximumContextLength(core.computeMaximumContextLength())
    }
    private val core=CompoundTransliteratorCore(elements.map {t->object : CompoundTransliteratorCore.Element {
        override val id get()=t.getID()
        override val maximumContextLength get()=t.getMaximumContextLength()
        override fun filteredTransliterate(text: Replaceable,pos: Position,incremental: Boolean)=t.filteredTransliterate(text,pos,incremental)
        override fun toRules(escapeUnprintable: Boolean)=t.toRules(escapeUnprintable)
        override fun baseToRules(escapeUnprintable: Boolean)=t.elementBaseToRules(escapeUnprintable)
        override fun addSourceTargetSet(filter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet)=t.addSourceTargetSet(filter,sourceSet,targetSet)
    }}.toTypedArray(),passes,{getFilter() as UnicodeSet?},::getFilterAsUnicodeSet)
    fun getCount(): Int = elements.size
    fun getTransliterator(index: Int): Transliterator {if(index !in elements.indices)throw IndexOutOfBoundsException();return elements[index]}
    override fun toRules(escapeUnprintable: Boolean): String = core.toRules(escapeUnprintable)
    override fun addSourceTargetSet(inputFilter: UnicodeSet,sourceSet: UnicodeSet,targetSet: UnicodeSet)=core.addSourceTargetSet(inputFilter,sourceSet,targetSet)
    override fun handleTransliterate(text: Replaceable,pos: Position,incremental: Boolean)=core.handleTransliterate(text,pos,incremental)
    fun safeClone(): Transliterator = CompoundTransliterator(getID(),(getFilter() as UnicodeSet?)?.let(::UnicodeSet),elements,passes)
}

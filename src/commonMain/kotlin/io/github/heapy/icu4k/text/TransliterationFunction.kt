// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Registry boundary used by parsed function replacements and ID instantiation. */
internal interface TransliterationFunction {
    fun getID(): String
    fun setFilter(filter: UnicodeSet)
    fun getTargetSet(): UnicodeSet
    fun transliterate(text: Replaceable, start: Int, limit: Int): Int
}
internal fun interface TransliterationFactory {
    fun getBasicInstance(basicID: String, canonicalID: String?): TransliterationFunction?
}
internal class FunctionReplacer(private val translit: TransliterationFunction, private val replacer: UnicodeReplacer) : UnicodeReplacer {
    override fun replace(text: Replaceable, start: Int, limit: Int, cursor: IntArray): Int {
        val len = replacer.replace(text,start,limit,cursor)
        return translit.transliterate(text,start,start+len) - start
    }
    override fun toReplacerPattern(escapeUnprintable: Boolean): String = "&" + translit.getID() + "( " + replacer.toReplacerPattern(escapeUnprintable) + " )"
    override fun addReplacementSetTo(toUnionTo: UnicodeSet) { toUnionTo.addAll(translit.getTargetSet()) }
}

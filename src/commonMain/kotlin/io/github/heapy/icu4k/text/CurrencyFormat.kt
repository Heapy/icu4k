// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*

/** Source legacy currency-only MeasureFormat implementation. */
internal class CurrencyFormat : MeasureFormat {
    constructor(locale: ULocale?) : super(locale,FormatWidth.DEFAULT_CURRENCY)
    private constructor(other: CurrencyFormat) : super(other,Unit)
    override fun format(input: Any?,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder {
        if(input !is CurrencyAmount) throw IllegalArgumentException("Invalid type: "+dateFormatClassName(input!!))
        return super.format(input,output,position)
    }
    override fun parseObject(source: String?,position: ParsePosition?): CurrencyAmount? = getNumberFormatInternal()!!.parseCurrency(source,position)
    protected override fun cloneNative(): MeasureFormat = CurrencyFormat(this)
    private fun writeReplace(): Any = toCurrencyProxy()
    private fun readResolve(): Any = CurrencyFormat(getLocale(ULocale.ACTUAL_LOCALE))
    companion object { private const val serialVersionUID = -931679363692504634L }
}

package io.github.heapy.icu4k.text

import java.util.Date
import io.github.heapy.icu4k.text.CurrencyMetaInfo.CurrencyFilter

fun CurrencyFilter.withDate(date: Date?): CurrencyFilter = withDateRange(date!!.time, date.time)
fun CurrencyFilter.withDateRange(from: Date?, to: Date?): CurrencyFilter = withDateRange(from?.time ?: Long.MIN_VALUE, to?.time ?: Long.MAX_VALUE)
fun CurrencyFilter.Companion.onDate(date: Date?): CurrencyFilter = all().withDate(date)
fun CurrencyFilter.Companion.onDateRange(from: Date?, to: Date?): CurrencyFilter = all().withDateRange(from, to)

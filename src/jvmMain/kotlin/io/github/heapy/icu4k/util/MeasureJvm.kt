package io.github.heapy.icu4k.util

internal actual fun measureNumberText(number: Number, kind: Int): String = number.toString()
internal actual fun measureNumbersEqual(first: Number, second: Number, firstKind: Int, secondKind: Int): Boolean = first == second || first.toDouble() == second.toDouble()

fun CurrencyAmount(number: Number?, currency: java.util.Currency?): CurrencyAmount = CurrencyAmount(number, Currency.fromJavaCurrency(currency))
fun CurrencyAmount(number: Double, currency: java.util.Currency?): CurrencyAmount = CurrencyAmount(number, Currency.fromJavaCurrency(currency))
fun CurrencyAmount(number: Float, currency: java.util.Currency?): CurrencyAmount = CurrencyAmount(number, Currency.fromJavaCurrency(currency))

internal actual fun measureInputNumber(number: Number, kind: Int): Number = number

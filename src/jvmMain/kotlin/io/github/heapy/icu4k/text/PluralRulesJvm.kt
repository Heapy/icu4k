package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.io.Serializable
import java.util.Locale

fun PluralRules.Companion.forLocale(locale: Locale?): PluralRules = forLocale(ULocale.forLocale(locale))
fun PluralRules.Companion.forLocale(locale: Locale?, type: PluralRules.PluralType?): PluralRules = forLocale(ULocale.forLocale(locale), type)

internal actual fun pluralRulesSerialProxy(description: String): Any = PluralRulesSerialProxy(description)
internal actual typealias PluralObjectInputStream = java.io.ObjectInputStream
internal actual typealias PluralObjectOutputStream = java.io.ObjectOutputStream
private class PluralRulesSerialProxy(private val data: String) : Serializable {
    private fun readResolve(): Any? = PluralRules.createRules(data)
    companion object { private const val serialVersionUID: Long = 42L }
}

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.ULocale
internal actual class RbnfObjectStreamField
internal actual fun rbnfSerializationFields(): Array<RbnfObjectStreamField> = emptyArray()
internal actual fun createRbnfScannerProvider(): RbnfLenientScannerProvider =
    io.github.heapy.icu4k.impl.text.RbnfScannerProviderImpl()
internal actual fun createRbnfPostProcessor(name: String): RbnfCorePostProcessor = RbnfFormatterCore.builtinPostProcessor(name)
internal actual fun writeRbnf(output: PluralObjectOutputStream, owner: RuleBasedNumberFormat, locale: ULocale?, mode: Int): Unit =
    throw UnsupportedOperationException("Java serialization is available on JVM")
internal actual fun readRbnf(input: PluralObjectInputStream, owner: RuleBasedNumberFormat): Unit =
    throw UnsupportedOperationException("Java serialization is available on JVM")

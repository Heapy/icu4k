// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.io.ObjectStreamField
import java.util.Locale

/** JVM Locale conveniences follow the project's common-class factory/extension convention. */
fun RuleBasedNumberFormat(description: String?, locale: Locale?): RuleBasedNumberFormat = RuleBasedNumberFormat(description, ULocale.forLocale(locale))
fun RuleBasedNumberFormat(locale: Locale?, format: Int): RuleBasedNumberFormat = RuleBasedNumberFormat(ULocale.forLocale(locale), format)
internal actual typealias RbnfObjectStreamField = ObjectStreamField
internal actual fun rbnfSerializationFields(): Array<ObjectStreamField> = arrayOf(
    ObjectStreamField("capitalizationForListOrMenu", Boolean::class.javaPrimitiveType),
    ObjectStreamField("capitalizationForStandAlone", Boolean::class.javaPrimitiveType),
    ObjectStreamField("capitalizationInfoIsSet", Boolean::class.javaPrimitiveType),
    ObjectStreamField("lenientParse", Boolean::class.javaPrimitiveType),
    ObjectStreamField("roundingMode", Int::class.javaPrimitiveType),
    ObjectStreamField("locale", ULocale::class.java),
    ObjectStreamField("publicRuleSetNames", Array<String>::class.java),
    ObjectStreamField("ruleSetDisplayNames", Map::class.java),
)
internal actual fun createRbnfScannerProvider(): RbnfLenientScannerProvider =
    Class.forName("io.github.heapy.icu4k.impl.text.RbnfScannerProviderImpl").newInstance() as RbnfLenientScannerProvider
internal actual fun createRbnfPostProcessor(name: String): RbnfCorePostProcessor =
    if (name == "com.ibm.icu.text.RBNFChinesePostProcessor") RbnfFormatterCore.builtinPostProcessor(name)
    else RbnfFacadePostProcessorAdapter(Class.forName(name).newInstance() as RBNFPostProcessor)
internal actual fun writeRbnf(output: PluralObjectOutputStream, owner: RuleBasedNumberFormat, locale: ULocale?, mode: Int) {
    output.writeUTF(owner.toString())
    output.writeObject(locale)
    output.writeInt(mode)
}
internal actual fun readRbnf(input: PluralObjectInputStream, owner: RuleBasedNumberFormat) {
    val description = input.readUTF()
    val locale = try { input.readObject() as ULocale? } catch (_: Exception) { ULocale.getDefault(ULocale.Category.FORMAT) }
    val mode = try { input.readInt() } catch (_: Exception) { 0 }
    owner.restoreFromStream(description, locale, mode)
}

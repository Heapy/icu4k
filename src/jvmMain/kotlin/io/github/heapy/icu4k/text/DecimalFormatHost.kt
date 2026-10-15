// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.DecimalFormatCore
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.impl.number.Properties
import io.github.heapy.icu4k.util.Currency
import java.io.IOException

internal actual fun decimalFormatClassName(value: DecimalFormat): String = value.javaClass.name
internal actual fun decimalFormatWrite(stream: PluralObjectOutputStream, owner: DecimalFormat) {
    stream.defaultWriteObject()
    stream.writeInt(0)
    stream.writeObject(owner.runtime?.rawProperties)
    stream.writeObject(owner.runtime?.rawSymbols)
}
internal actual fun decimalFormatRead(stream: PluralObjectInputStream, owner: DecimalFormat) {
    val fields = stream.readFields()
    val serialized = fields.objectStreamClass.fields
    val version = fields.get("serialVersionOnStream", -1)
    if (version > 5) throw IOException("Cannot deserialize newer com.ibm.icu.text.DecimalFormat (v$version)")
    if (version == 5) {
        if (serialized.size > 1) throw IOException("Too many fields when reading serial version 5")
        stream.readInt()
        val properties = stream.readObject()
        val core = DecimalFormatCore.empty()
        owner.runtime = core
        core.rawProperties = if (properties is DecimalFormatProperties) properties else (properties as Properties?)!!.getInstance()
        core.rawSymbols = stream.readObject() as DecimalFormatSymbols?
        core.exportedProperties = DecimalFormatProperties()
        core.actualLocale = owner.getLocale(io.github.heapy.icu4k.util.ULocale.ACTUAL_LOCALE)
        core.refreshFormatter()
        return
    }
    val core = DecimalFormatCore.empty()
    owner.runtime = core
    core.properties = DecimalFormatProperties()
    var pp: String? = null; var ppp: String? = null; var ps: String? = null; var psp: String? = null
    var np: String? = null; var npp: String? = null; var ns: String? = null; var nsp: String? = null
    for (field in serialized) when (field.name) {
        "decimalSeparatorAlwaysShown" -> owner.setDecimalSeparatorAlwaysShown(fields.get("decimalSeparatorAlwaysShown", false))
        "exponentSignAlwaysShown" -> owner.setExponentSignAlwaysShown(fields.get("exponentSignAlwaysShown", false))
        "formatWidth" -> owner.setFormatWidth(fields.get("formatWidth", 0))
        "groupingSize" -> owner.setGroupingSize(fields.get("groupingSize", 3.toByte()).toInt())
        "groupingSize2" -> owner.setSecondaryGroupingSize(fields.get("groupingSize2", 0.toByte()).toInt())
        "maxSignificantDigits" -> owner.setMaximumSignificantDigits(fields.get("maxSignificantDigits", 6))
        "minExponentDigits" -> owner.setMinimumExponentDigits(fields.get("minExponentDigits", 0.toByte()))
        "minSignificantDigits" -> owner.setMinimumSignificantDigits(fields.get("minSignificantDigits", 1))
        "multiplier" -> owner.setMultiplier(fields.get("multiplier", 1))
        "pad" -> owner.setPadCharacter(fields.get("pad", ' '))
        "padPosition" -> owner.setPadPosition(fields.get("padPosition", 0))
        "parseBigDecimal" -> owner.setParseBigDecimal(fields.get("parseBigDecimal", false))
        "parseRequireDecimalPoint" -> owner.setDecimalPatternMatchRequired(fields.get("parseRequireDecimalPoint", false))
        "roundingMode" -> owner.setRoundingMode(fields.get("roundingMode", 0))
        "useExponentialNotation" -> owner.setScientificNotation(fields.get("useExponentialNotation", false))
        "useSignificantDigits" -> owner.setSignificantDigitsUsed(fields.get("useSignificantDigits", false))
        "currencyPluralInfo" -> owner.setCurrencyPluralInfo(fields.get("currencyPluralInfo", null) as CurrencyPluralInfo?)
        "mathContext" -> owner.setMathContextICU(fields.get("mathContext", null) as io.github.heapy.icu4k.math.MathContext?)
        "roundingIncrement" -> owner.setRoundingIncrement(fields.get("roundingIncrement", null) as java.math.BigDecimal?)
        "symbols" -> owner.setDecimalFormatSymbols(fields.get("symbols", null) as DecimalFormatSymbols?)
        "negPrefixPattern" -> npp = fields.get("negPrefixPattern", null) as String?
        "negSuffixPattern" -> nsp = fields.get("negSuffixPattern", null) as String?
        "negativePrefix" -> np = fields.get("negativePrefix", null) as String?
        "negativeSuffix" -> ns = fields.get("negativeSuffix", null) as String?
        "posPrefixPattern" -> ppp = fields.get("posPrefixPattern", null) as String?
        "posSuffixPattern" -> psp = fields.get("posSuffixPattern", null) as String?
        "positivePrefix" -> pp = fields.get("positivePrefix", null) as String?
        "positiveSuffix" -> ps = fields.get("positiveSuffix", null) as String?
    }
    if (npp == null) core.properties.setNegativePrefix(np) else core.properties.setNegativePrefixPattern(npp)
    if (nsp == null) core.properties.setNegativeSuffix(ns) else core.properties.setNegativeSuffixPattern(nsp)
    if (ppp == null) core.properties.setPositivePrefix(pp) else core.properties.setPositivePrefixPattern(ppp)
    if (psp == null) core.properties.setPositiveSuffix(ps) else core.properties.setPositiveSuffixPattern(psp)
    try {
        fun value(name: String): Any? = NumberFormat::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
        owner.setGroupingUsed(value("groupingUsed") as Boolean)
        owner.setParseIntegerOnly(value("parseIntegerOnly") as Boolean)
        owner.setMaximumIntegerDigits(value("maximumIntegerDigits") as Int)
        owner.setMinimumIntegerDigits(value("minimumIntegerDigits") as Int)
        owner.setMaximumFractionDigits(value("maximumFractionDigits") as Int)
        owner.setMinimumFractionDigits(value("minimumFractionDigits") as Int)
        owner.setCurrency(value("currency") as Currency?)
        owner.setParseStrict(value("parseStrict") as Boolean)
    } catch (e: IllegalArgumentException) { throw IOException(e) }
      catch (e: IllegalAccessException) { throw IOException(e) }
      catch (e: NoSuchFieldException) { throw IOException(e) }
      catch (e: SecurityException) { throw IOException(e) }
    if (core.rawSymbols == null) core.symbols = DecimalFormatSymbols.getInstance()
    core.exportedProperties = DecimalFormatProperties()
    core.actualLocale = owner.getLocale(io.github.heapy.icu4k.util.ULocale.ACTUAL_LOCALE)
    core.refreshFormatter()
}

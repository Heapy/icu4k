// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.util.ULocale

/** Collator's locale keyword processing, in source application/failure order. */
internal object CollationLocaleOptions {
    interface CollatorSink {
        fun setStrength(value: Int)
        fun setDecomposition(value: Int)
        fun setReorderCodes(order: IntArray)
        fun setMaxVariable(value: Int)
    }
    interface RuleBasedSink {
        fun setFrenchCollation(value: Boolean)
        fun setCaseLevel(value: Boolean)
        fun setLowerCaseFirst(value: Boolean)
        fun setUpperCaseFirst(value: Boolean)
        fun setAlternateHandlingShifted(value: Boolean)
        fun setNumericCollation(value: Boolean)
    }
    fun apply(locale: ULocale?, config: RuleBasedCollatorSettings) {
        val sink = object : CollatorSink, RuleBasedSink {
            override fun setStrength(value: Int) = config.setStrength(value)
            override fun setDecomposition(value: Int) = config.setDecomposition(value)
            override fun setReorderCodes(order: IntArray) = config.setReorderCodes(order)
            override fun setMaxVariable(value: Int) = config.setMaxVariable(value)
            override fun setFrenchCollation(value: Boolean) = config.setFrenchCollation(value)
            override fun setCaseLevel(value: Boolean) = config.setCaseLevel(value)
            override fun setLowerCaseFirst(value: Boolean) = config.setLowerCaseFirst(value)
            override fun setUpperCaseFirst(value: Boolean) = config.setUpperCaseFirst(value)
            override fun setAlternateHandlingShifted(value: Boolean) = config.setAlternateHandlingShifted(value)
            override fun setNumericCollation(value: Boolean) = config.setNumericCollation(value)
        }
        apply(locale,sink,sink)
    }
    fun apply(loc: ULocale?, coll: CollatorSink?, rbc: RuleBasedSink?) {
        var value = loc!!.getKeywordValue("colHiraganaQuaternary")
        if (value != null) throw UnsupportedOperationException("locale keyword kh/colHiraganaQuaternary")
        value = loc.getKeywordValue("variableTop")
        if (value != null) throw UnsupportedOperationException("locale keyword vt/variableTop")
        value = loc.getKeywordValue("colStrength")
        if (value != null) {
            val strength = getIntValue("colStrength",value,"primary","secondary","tertiary","quaternary","identical")
            coll!!.setStrength(if (strength <= 3) strength else 15)
        }
        value = loc.getKeywordValue("colBackwards")
        if (value != null) {
            if (rbc != null) rbc.setFrenchCollation(getYesOrNo("colBackwards",value))
            else throw UnsupportedOperationException("locale keyword kb/colBackwards only settable for RuleBasedCollator")
        }
        value = loc.getKeywordValue("colCaseLevel")
        if (value != null) {
            if (rbc != null) rbc.setCaseLevel(getYesOrNo("colCaseLevel",value))
            else throw UnsupportedOperationException("locale keyword kb/colBackwards only settable for RuleBasedCollator")
        }
        value = loc.getKeywordValue("colCaseFirst")
        if (value != null) {
            if (rbc != null) {
                when (getIntValue("colCaseFirst",value,"no","lower","upper")) {
                    0 -> { rbc.setLowerCaseFirst(false); rbc.setUpperCaseFirst(false) }
                    1 -> rbc.setLowerCaseFirst(true)
                    else -> rbc.setUpperCaseFirst(true)
                }
            } else throw UnsupportedOperationException("locale keyword kf/colCaseFirst only settable for RuleBasedCollator")
        }
        value = loc.getKeywordValue("colAlternate")
        if (value != null) {
            if (rbc != null) rbc.setAlternateHandlingShifted(getIntValue("colAlternate",value,"non-ignorable","shifted") != 0)
            else throw UnsupportedOperationException("locale keyword ka/colAlternate only settable for RuleBasedCollator")
        }
        value = loc.getKeywordValue("colNormalization")
        if (value != null) {
            val decomposition = if (getYesOrNo("colNormalization",value)) 17 else 16
            coll!!.setDecomposition(decomposition)
        }
        value = loc.getKeywordValue("colNumeric")
        if (value != null) {
            if (rbc != null) rbc.setNumericCollation(getYesOrNo("colNumeric",value))
            else throw UnsupportedOperationException("locale keyword kn/colNumeric only settable for RuleBasedCollator")
        }
        value = loc.getKeywordValue("colReorder")
        if (value != null) {
            val codes = IntArray(UScript.CODE_LIMIT + 5)
            var codesLength = 0
            var start = 0
            while (true) {
                if (codesLength == codes.size) throw IllegalArgumentException("too many script codes for colReorder locale keyword: $value")
                var limit = start
                while (limit < value.length && value[limit] != '-') ++limit
                val script = value.substring(start,limit)
                val code = if (script.length == 4) UCharacter.getPropertyValueEnum(UProperty.SCRIPT,script) else getReorderCode("colReorder",script)
                codes[codesLength++] = code
                if (limit == value.length) break
                start = limit + 1
            }
            if (codesLength == 0) throw IllegalArgumentException("no script codes for colReorder locale keyword")
            coll!!.setReorderCodes(codes.copyOf(codesLength))
        }
        value = loc.getKeywordValue("kv")
        if (value != null) {
            val code = getReorderCode("kv",value)
            coll!!.setMaxVariable(code)
        }
    }
    internal fun equalIgnoreCase(left: CharSequence?, right: CharSequence?): Boolean {
        val length = left!!.length
        if (length != right!!.length) return false
        for (i in 0..<length) {
            val lc = left[i]; val rc = right[i]
            if (lc == rc) continue
            if (lc in 'A'..'Z') { if (lc.code + 0x20 == rc.code) continue }
            else if (rc in 'A'..'Z') { if (rc.code + 0x20 == lc.code) continue }
            return false
        }
        return true
    }
    internal fun getYesOrNo(keyword: String?, s: String?): Boolean {
        if (equalIgnoreCase(s,"yes")) return true
        if (equalIgnoreCase(s,"no")) return false
        throw IllegalArgumentException("illegal locale keyword=value: $keyword=$s")
    }
    internal fun getIntValue(keyword: String?, s: String?, vararg values: String?): Int {
        for (i in values.indices) if (equalIgnoreCase(s,values[i])) return i
        throw IllegalArgumentException("illegal locale keyword=value: $keyword=$s")
    }
    internal fun getReorderCode(keyword: String?, s: String?): Int = 0x1000 + getIntValue(keyword,s,"space","punct","symbol","currency","digit")
}

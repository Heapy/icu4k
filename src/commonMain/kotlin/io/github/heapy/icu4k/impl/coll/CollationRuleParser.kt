// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.Normalizer2
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.attachParseExceptionCause

/** Rule syntax and settings parser; tailoring construction is supplied by Sink. */
internal class CollationRuleParser(private val baseData: CollationData?) {
    enum class Position { FIRST_TERTIARY_IGNORABLE,LAST_TERTIARY_IGNORABLE,FIRST_SECONDARY_IGNORABLE,LAST_SECONDARY_IGNORABLE,FIRST_PRIMARY_IGNORABLE,LAST_PRIMARY_IGNORABLE,FIRST_VARIABLE,LAST_VARIABLE,FIRST_REGULAR,LAST_REGULAR,FIRST_IMPLICIT,LAST_IMPLICIT,FIRST_TRAILING,LAST_TRAILING }
    abstract class Sink {
        abstract fun addReset(strength: Int,str: CharSequence)
        abstract fun addRelation(strength: Int,prefix: CharSequence,str: CharSequence,extension: CharSequence)
        open fun suppressContractions(set: UnicodeSet) { }
        open fun optimize(set: UnicodeSet) { }
    }
    fun interface Importer { fun getRules(localeID: String,collationType: String): String? }
    private val rawBuilder=StringBuilder()
    private val nfd=Normalizer2.getNFDInstance()
    private val nfc=Normalizer2.getNFCInstance()
    private var rules: String?=null
    private var settings: CollationSettings?=null
    private var sink: Sink?=null
    private var importer: Importer?=null
    private var ruleIndex=0
    private val r: String get()=rules!!
    fun setSink(sinkAlias: Sink?) { sink=sinkAlias }
    fun setImporter(importerAlias: Importer?) { importer=importerAlias }
    fun parse(ruleString: String?,outSettings: CollationSettings?) { settings=outSettings;parse(ruleString) }
    private fun parse(ruleString: String?) {
        rules=ruleString;ruleIndex=0
        while(ruleIndex<r.length) {
            val c=r[ruleIndex]
            if(PatternProps.isWhiteSpace(c.code)) { ++ruleIndex;continue }
            when(c) {
                '&' -> parseRuleChain()
                '[' -> parseSetting()
                '#' -> ruleIndex=skipComment(ruleIndex+1)
                '@' -> { settings!!.setFlag(CollationSettings.BACKWARD_SECONDARY,true);++ruleIndex }
                '!' -> ++ruleIndex
                else -> setParseError("expected a reset or setting or comment")
            }
        }
    }
    private fun parseRuleChain() {
        val resetStrength=parseResetAndPosition();var first=true
        while(true) {
            val result=parseRelationOperator()
            if(result<0) {
                if(ruleIndex<r.length && r[ruleIndex]=='#') { ruleIndex=skipComment(ruleIndex+1);continue }
                if(first) setParseError("reset not followed by a relation")
                return
            }
            val strength=result and STRENGTH_MASK
            if(resetStrength<15) {
                if(first) { if(strength!=resetStrength) setParseError("reset-before strength differs from its first relation") }
                else if(strength<resetStrength) setParseError("reset-before strength followed by a stronger relation")
            }
            val i=ruleIndex+(result shr OFFSET_SHIFT)
            if(result and STARRED_FLAG==0) parseRelationStrings(strength,i) else parseStarredCharacters(strength,i)
            first=false
        }
    }
    private fun parseResetAndPosition(): Int {
        var i=skipWhiteSpace(ruleIndex+1);var j=i+BEFORE.length;var before=false
        if(r.regionMatches(i,BEFORE,0,BEFORE.length) && j<r.length && PatternProps.isWhiteSpace(r[j].code)) {
            j=skipWhiteSpace(j+1)
            before=j+1<r.length && r[j] in '1'..'3' && r[j+1]==']'
        }
        val strength=if(before) { val s=r[j]-'1';i=skipWhiteSpace(j+2);s } else 15
        if(i>=r.length) setParseError("reset without position")
        i=if(r[i]=='[') parseSpecialPosition(i,rawBuilder) else parseTailoringString(i,rawBuilder)
        try { sink!!.addReset(strength,rawBuilder) } catch(e: Exception) { setParseError("adding reset failed",e) }
        ruleIndex=i;return strength
    }
    private fun parseRelationOperator(): Int {
        ruleIndex=skipWhiteSpace(ruleIndex)
        if(ruleIndex>=r.length) return -1
        var i=ruleIndex;val c=r[i++];var strength: Int
        when(c) {
            '<' -> {
                strength=0
                while(strength<3 && i<r.length && r[i]=='<') { ++i;++strength }
                if(i<r.length && r[i]=='*') { ++i;strength=strength or STARRED_FLAG }
            }
            ';' -> strength=1
            ',' -> strength=2
            '=' -> { strength=15;if(i<r.length && r[i]=='*') { ++i;strength=strength or STARRED_FLAG } }
            else -> return -1
        }
        return ((i-ruleIndex) shl OFFSET_SHIFT) or strength
    }
    private fun parseRelationStrings(strength: Int,start: Int) {
        var prefix="";var extension: CharSequence="";var i=parseTailoringString(start,rawBuilder)
        var next=if(i<r.length) r[i] else '\u0000'
        if(next=='|') { prefix=rawBuilder.toString();i=parseTailoringString(i+1,rawBuilder);next=if(i<r.length) r[i] else '\u0000' }
        if(next=='/') { val ext=StringBuilder();i=parseTailoringString(i+1,ext);extension=ext }
        if(prefix.isNotEmpty() && (!nfc.hasBoundaryBefore(codePointAt(prefix,0)) || !nfc.hasBoundaryBefore(codePointAt(rawBuilder,0)))) setParseError("in 'prefix|str', prefix and str must each start with an NFC boundary")
        try { sink!!.addRelation(strength,prefix,rawBuilder,extension) } catch(e: Exception) { setParseError("adding relation failed",e) }
        ruleIndex=i
    }
    private fun parseStarredCharacters(strength: Int,start: Int) {
        var i=parseString(skipWhiteSpace(start),rawBuilder)
        if(rawBuilder.isEmpty()) setParseError("missing starred-relation string")
        var prev=-1;var j=0
        while(true) {
            while(j<rawBuilder.length) {
                val c=codePointAt(rawBuilder,j)
                if(!nfd.isInert(c)) setParseError("starred-relation string is not all NFD-inert")
                try { sink!!.addRelation(strength,"",UTF16.valueOf(c),"") } catch(e: Exception) { setParseError("adding relation failed",e) }
                j+=charCount(c);prev=c
            }
            if(i>=r.length || r[i]!='-') break
            if(prev<0) setParseError("range without start in starred-relation string")
            i=parseString(i+1,rawBuilder)
            if(rawBuilder.isEmpty()) setParseError("range without end in starred-relation string")
            val c=codePointAt(rawBuilder,0)
            if(c<prev) setParseError("range start greater than end in starred-relation string")
            while(++prev<=c) {
                if(!nfd.isInert(prev)) setParseError("starred-relation string range is not all NFD-inert")
                if(isSurrogate(prev)) setParseError("starred-relation string range contains a surrogate")
                if(prev in 0xfffd..0xffff) setParseError("starred-relation string range contains U+FFFD, U+FFFE or U+FFFF")
                try { sink!!.addRelation(strength,"",UTF16.valueOf(prev),"") } catch(e: Exception) { setParseError("adding relation failed",e) }
            }
            prev=-1;j=charCount(c)
        }
        ruleIndex=skipWhiteSpace(i)
    }
    private fun parseTailoringString(start: Int,raw: StringBuilder): Int {
        val i=parseString(skipWhiteSpace(start),raw)
        if(raw.isEmpty()) setParseError("missing relation string")
        return skipWhiteSpace(i)
    }
    private fun parseString(start: Int,raw: StringBuilder): Int {
        var i=start;raw.setLength(0)
        while(i<r.length) {
            var c=r[i++]
            if(isSyntaxChar(c.code)) {
                if(c=='\'') {
                    if(i<r.length && r[i]=='\'') { raw.append('\'');++i;continue }
                    while(true) {
                        if(i==r.length) setParseError("quoted literal text missing terminating apostrophe")
                        c=r[i++]
                        if(c=='\'') { if(i<r.length && r[i]=='\'') ++i else break }
                        raw.append(c)
                    }
                } else if(c=='\\') {
                    if(i==r.length) setParseError("backslash escape at the end of the rule string")
                    val cp=codePointAt(r,i);raw.append(UTF16.valueOf(cp));i+=charCount(cp)
                } else { --i;break }
            } else if(PatternProps.isWhiteSpace(c.code)) { --i;break } else raw.append(c)
        }
        var j=0
        while(j<raw.length) {
            val c=codePointAt(raw,j)
            if(isSurrogate(c)) setParseError("string contains an unpaired surrogate")
            if(c in 0xfffd..0xffff) setParseError("string contains U+FFFD, U+FFFE or U+FFFF")
            j+=charCount(c)
        }
        return i
    }
    private fun parseSpecialPosition(start: Int,str: StringBuilder): Int {
        var j=readWords(start+1,rawBuilder)
        if(j>start && r[j]==']' && rawBuilder.isNotEmpty()) {
            ++j;val raw=rawBuilder.toString();str.setLength(0)
            val pos=positions.indexOf(raw)
            if(pos>=0) { str.append(POS_LEAD).append((POS_BASE.code+pos).toChar());return j }
            if(raw=="top" || raw=="variable top") { str.append(POS_LEAD).append((POS_BASE.code+if(raw=="top") Position.LAST_REGULAR.ordinal else Position.LAST_VARIABLE.ordinal).toChar());return j }
        }
        setParseError("not a valid special reset position")
    }
    private fun parseSetting() {
        val i=ruleIndex+1;var j=readWords(i,rawBuilder)
        if(j<=i || rawBuilder.isEmpty()) setParseError("expected a setting/option at '['")
        var raw=rawBuilder.toString()
        if(r[j]==']') {
            ++j
            if(raw.startsWith("reorder") && (raw.length==7 || raw[7]==' ')) { parseReordering(raw);ruleIndex=j;return }
            if(raw=="backwards 2") { settings!!.setFlag(CollationSettings.BACKWARD_SECONDARY,true);ruleIndex=j;return }
            val valueIndex=raw.lastIndexOf(' ');val v=if(valueIndex>=0) raw.substring(valueIndex+1) else ""
            if(valueIndex>=0) raw=raw.substring(0,valueIndex)
            if(raw=="strength" && v.length==1) {
                val value=if(v[0] in '1'..'4') v[0]-'1' else if(v[0]=='I') 15 else -1
                if(value!=-1) { settings!!.setStrength(value);ruleIndex=j;return }
            } else if(raw=="alternate") {
                val value=when(v) { "non-ignorable" -> 0;"shifted" -> 1;else -> -1 }
                if(value!=-1) { settings!!.setAlternateHandlingShifted(value>0);ruleIndex=j;return }
            } else if(raw=="maxVariable") {
                val value=when(v) { "space" -> 0;"punct" -> 1;"symbol" -> 2;"currency" -> 3;else -> -1 }
                if(value!=-1) { settings!!.setMaxVariable(value,0);settings!!.variableTop=baseData!!.getLastPrimaryForGroup(4096+value);checkAssertion { settings!!.variableTop!=0L };ruleIndex=j;return }
            } else if(raw=="caseFirst") {
                val value=when(v) { "off" -> 0;"lower" -> CollationSettings.CASE_FIRST;"upper" -> CollationSettings.CASE_FIRST_AND_UPPER_MASK;else -> -1 }
                if(value!=-1) { settings!!.setCaseFirst(value);ruleIndex=j;return }
            } else if(raw=="caseLevel" || raw=="normalization" || raw=="numericOrdering") {
                val value=getOnOffValue(v)
                if(value!=-1) { settings!!.setFlag(when(raw) { "caseLevel" -> CollationSettings.CASE_LEVEL;"normalization" -> CollationSettings.CHECK_FCD;else -> CollationSettings.NUMERIC },value>0);ruleIndex=j;return }
            } else if(raw=="hiraganaQ") {
                val value=getOnOffValue(v)
                if(value!=-1) { if(value==1) setParseError("[hiraganaQ on] is not supported");ruleIndex=j;return }
            } else if(raw=="import") {
                val localeID: ULocale
                try { localeID=ULocale.Builder().setLanguageTag(v).build() } catch(e: Exception) { setParseError("expected language tag in [import langTag]",e) }
                val baseID=localeID.getBaseName();val type=localeID.getKeywordValue("collation")
                if(importer==null) setParseError("[import langTag] is not supported")
                val importedRules: String?
                try { importedRules=importer!!.getRules(baseID,type ?: "standard") } catch(e: Exception) { setParseError("[import langTag] failed",e) }
                val outerRules=rules;val outerRuleIndex=ruleIndex
                try { parse(importedRules) } catch(e: Exception) { ruleIndex=outerRuleIndex;setParseError("parsing imported rules failed",e) }
                rules=outerRules;ruleIndex=j;return
            }
        } else if(r[j]=='[') {
            val set=UnicodeSet();j=parseUnicodeSet(j,set)
            if(raw=="optimize" || raw=="suppressContractions") {
                try { if(raw=="optimize") sink!!.optimize(set) else sink!!.suppressContractions(set) } catch(e: Exception) { setParseError("[$raw set] failed",e) }
                ruleIndex=j;return
            }
        }
        setParseError("not a valid setting/option")
    }
    private fun parseReordering(raw: CharSequence) {
        var i=7
        if(i==raw.length) { settings!!.resetReordering();return }
        val codes=mutableListOf<Int>()
        while(i<raw.length) {
            ++i;var limit=i
            while(limit<raw.length && raw[limit]!=' ') ++limit
            val code=getReorderCode(raw.subSequence(i,limit).toString())
            if(code<0) setParseError("unknown script or reorder code")
            codes.add(code);i=limit
        }
        if(codes.isEmpty()) settings!!.resetReordering() else settings!!.setReordering(baseData,codes.toIntArray())
    }
    private fun parseUnicodeSet(start: Int,set: UnicodeSet): Int {
        var level=0;var j=start
        while(true) {
            if(j==r.length) setParseError("unbalanced UnicodeSet pattern brackets")
            val c=r[j++]
            if(c=='[') ++level else if(c==']' && --level==0) break
        }
        try { set.applyPattern(r.substring(start,j)) } catch(e: Exception) { setParseError("not a valid UnicodeSet pattern: "+e.message) }
        j=skipWhiteSpace(j)
        if(j==r.length || r[j]!=']') setParseError("missing option-terminating ']' after UnicodeSet pattern")
        return j+1
    }
    private fun readWords(start: Int,raw: StringBuilder): Int {
        raw.setLength(0);var i=skipWhiteSpace(start)
        while(true) {
            if(i>=r.length) return 0
            val c=r[i]
            if(isSyntaxChar(c.code) && c!='-' && c!='_') {
                if(raw.isEmpty()) return i
                if(raw[raw.length-1]==' ') raw.setLength(raw.length-1)
                return i
            }
            if(PatternProps.isWhiteSpace(c.code)) { raw.append(' ');i=skipWhiteSpace(i+1) } else { raw.append(c);++i }
        }
    }
    private fun skipComment(start: Int): Int {
        var i=start
        while(i<r.length) { val c=r[i++].code;if(c==10 || c==12 || c==13 || c==0x85 || c==0x2028 || c==0x2029) break }
        return i
    }
    private fun skipWhiteSpace(start: Int): Int { var i=start;while(i<r.length && PatternProps.isWhiteSpace(r[i].code)) ++i;return i }
    private fun setParseError(reason: String): Nothing = throw makeParseException(reason)
    private fun setParseError(reason: String,e: Exception): Nothing = throw attachParseExceptionCause(makeParseException(reason+": "+e.message),e)
    private fun makeParseException(reason: String): ParseException = ParseException(appendErrorContext(reason),ruleIndex)
    private fun appendErrorContext(reason: String): String {
        val msg=StringBuilder(reason).append(" at index ").append(ruleIndex).append(" near \"")
        var start=ruleIndex-15
        if(start<0) start=0 else if(start>0 && r[start].code in 0xdc00..0xdfff) ++start
        msg.append(r,start,ruleIndex).append('!')
        var length=r.length-ruleIndex
        if(length>=16) { length=15;if(r[ruleIndex+length-1].code in 0xd800..0xdbff) --length }
        return msg.append(r,ruleIndex,ruleIndex+length).append('"').toString()
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        val POSITION_VALUES=Position.entries.toTypedArray()
        const val POS_LEAD='\ufffe'
        const val POS_BASE='\u2800'
        private const val STRENGTH_MASK=15
        private const val STARRED_FLAG=16
        private const val OFFSET_SHIFT=8
        private const val BEFORE="[before"
        private val positions=arrayOf("first tertiary ignorable","last tertiary ignorable","first secondary ignorable","last secondary ignorable","first primary ignorable","last primary ignorable","first variable","last variable","first regular","last regular","first implicit","last implicit","first trailing","last trailing")
        private val specialReorderCodes=arrayOf("space","punct","symbol","currency","digit")
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationRuleParser::class)
        fun getReorderCode(word: String?): Int {
            for(i in specialReorderCodes.indices) if(word!!.equals(specialReorderCodes[i],ignoreCase=true)) return 4096+i
            try { val script=UCharacter.getPropertyValueEnum(UProperty.SCRIPT,word!!);if(script>=0) return script } catch(e: IllegalIcuArgumentException) { }
            return if(word!!.equals("others",ignoreCase=true)) 103 else -1
        }
        private fun getOnOffValue(s: String): Int = when(s) { "on" -> 1;"off" -> 0;else -> -1 }
        private fun isSurrogate(c: Int): Boolean = c and -2048==0xd800
        private fun isSyntaxChar(c: Int): Boolean = c in 0x21..0x7e && (c<=0x2f || c in 0x3a..0x40 || c in 0x5b..0x60 || c>=0x7b)
        private fun charCount(c: Int): Int = if(c>=0x10000) 2 else 1
        private fun codePointAt(s: CharSequence,i: Int): Int {
            if(i<0 || i>=s.length) throw IndexOutOfBoundsException()
            val c=s[i].code
            if(c in 0xd800..0xdbff && i+1<s.length) { val t=s[i+1].code;if(t in 0xdc00..0xdfff) return ((c-0xd800) shl 10)+t-0xdc00+0x10000 }
            return c
        }
    }
}

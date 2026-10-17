// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UCharacterCategory
import io.github.heapy.icu4k.lang.UCharacterDirection as D
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.IDNA
import io.github.heapy.icu4k.text.Normalizer2
import io.github.heapy.icu4k.text.StringPrepParseException
import io.github.heapy.icu4k.util.ICUException

internal class UTS46(private val options: Int) : IDNA() {
    override fun labelToASCII(label: CharSequence,dest: StringBuilder,info: Info): StringBuilder = process(label,true,true,dest,info)
    override fun labelToUnicode(label: CharSequence,dest: StringBuilder,info: Info): StringBuilder = process(label,true,false,dest,info)
    override fun nameToUnicode(name: CharSequence,dest: StringBuilder,info: Info): StringBuilder = process(name,false,false,dest,info)
    override fun nameToASCII(name: CharSequence,dest: StringBuilder,info: Info): StringBuilder {
        process(name,false,true,dest,info)
        if(dest.length>=254 && Error.DOMAIN_NAME_TOO_LONG !in info.getErrors() && isASCIIString(dest) && (dest.length>254 || dest[253]!='.'))addError(info,Error.DOMAIN_NAME_TOO_LONG)
        return dest
    }
    private fun process(src: CharSequence,isLabel: Boolean,toASCII: Boolean,dest: StringBuilder,info: Info): StringBuilder {
        if(dest===src)throw IllegalArgumentException()
        dest.clear();resetInfo(info)
        val length=src.length
        if(length==0){addError(info,Error.EMPTY_LABEL);return dest}
        val std3=options and USE_STD3_RULES!=0
        var labelStart=0;var i=0
        while(true) {
            if(i==length) {
                if(toASCII) {
                    if(i-labelStart>63)addLabelError(info,Error.LABEL_TOO_LONG)
                    if(!isLabel && i>=254 && (i>254 || labelStart<i))addError(info,Error.DOMAIN_NAME_TOO_LONG)
                }
                promoteAndResetLabelErrors(info);return dest
            }
            val c=src[i]
            if(c.code>0x7f)break
            val data=asciiData[c.code]
            if(data>0)dest.append((c.code+0x20).toChar())
            else if(data<0 && std3)break
            else {
                dest.append(c)
                if(c=='-') {
                    if(i==labelStart+3 && src[i-1]=='-'){i++;break}
                    if(i==labelStart)addLabelError(info,Error.LEADING_HYPHEN)
                    if(i+1==length || src[i+1]=='.')addLabelError(info,Error.TRAILING_HYPHEN)
                }else if(c=='.') {
                    if(isLabel){i++;break}
                    if(i==labelStart)addLabelError(info,Error.EMPTY_LABEL)
                    if(toASCII && i-labelStart>63)addLabelError(info,Error.LABEL_TOO_LONG)
                    promoteAndResetLabelErrors(info);labelStart=i+1
                }
            }
            i++
        }
        promoteAndResetLabelErrors(info)
        processUnicode(src,labelStart,i,isLabel,toASCII,dest,info)
        if(isBiDi(info) && !hasCertainErrors(info,severeErrors) && (!isOkBiDi(info) || (labelStart>0 && !isASCIIOkBiDi(dest,labelStart))))addError(info,Error.BIDI)
        return dest
    }
    private fun processUnicode(src: CharSequence,initialLabelStart: Int,mappingStart: Int,isLabel: Boolean,toASCII: Boolean,dest: StringBuilder,info: Info): StringBuilder {
        var labelStart=initialLabelStart
        if(mappingStart==0)norm.normalize(src,dest) else norm.normalizeSecondAndAppend(dest,src.subSequence(mappingStart,src.length))
        var map=options and (if(toASCII)NONTRANSITIONAL_TO_ASCII else NONTRANSITIONAL_TO_UNICODE)==0
        var length=dest.length;var limit=labelStart
        while(limit<length) {
            val c=dest[limit]
            if(c=='.' && !isLabel) {
                val oldLength=limit-labelStart
                val newLength=processLabel(dest,labelStart,oldLength,toASCII,info)
                promoteAndResetLabelErrors(info);length+=newLength-oldLength
                labelStart+=newLength+1;limit=labelStart;continue
            }else if(c.code<0xdf) {
            }else if(c.code<=0x200d && (c.code==0xdf || c.code==0x3c2 || c.code>=0x200c)) {
                setTransitionalDifferent(info)
                if(map){length=mapDevChars(dest,labelStart,limit);map=false;continue}
            }else if(c.isSurrogate()) {
                if(if(c.isHighSurrogate())limit+1==length || !dest[limit+1].isLowSurrogate() else limit==labelStart || !dest[limit-1].isHighSurrogate()) {
                    addLabelError(info,Error.DISALLOWED);dest[limit]='\ufffd'
                }
            }
            limit++
        }
        if(labelStart==0 || labelStart<limit){processLabel(dest,labelStart,limit-labelStart,toASCII,info);promoteAndResetLabelErrors(info)}
        return dest
    }
    private fun mapDevChars(dest: StringBuilder,labelStart: Int,mappingStart: Int): Int {
        var length=dest.length;var changed=false;var i=mappingStart
        while(i<length)when(dest[i].code) {
            0xdf->{changed=true;dest[i++]='s';dest.insert(i++,'s');length++}
            0x3c2->{changed=true;dest[i++]='\u03c3'}
            0x200c,0x200d->{changed=true;dest.deleteAt(i);length--}
            else->i++
        }
        if(changed){val normalized=norm.normalize(dest.subSequence(labelStart,dest.length));dest.deleteRange(labelStart,dest.length);dest.append(normalized);return dest.length}
        return length
    }
    private fun replaceLabel(dest: StringBuilder,start: Int,oldLength: Int,label: CharSequence,length: Int): Int {
        if(label!==dest){dest.deleteRange(start,start+oldLength);dest.insert(start,label)}
        return length
    }
    private fun processLabel(dest: StringBuilder,initialStart: Int,initialLength: Int,toASCII: Boolean,info: Info): Int {
        var start=initialStart;var length=initialLength;val destStart=start;var destLength=length
        val label: StringBuilder
        val wasPunycode=length>=4 && startsWithXNDashDash(dest.subSequence(start,start+length))
        if(wasPunycode) {
            if(length==4 || (length>5 && dest[start+length-1]=='-')){addLabelError(info,Error.INVALID_ACE_LABEL);return markBadACELabel(dest,start,length,toASCII,info)}
            val decoded=try { Punycode.decode(dest.subSequence(start+4,start+length),null) }catch(e: StringPrepParseException){addLabelError(info,Error.PUNYCODE);return markBadACELabel(dest,start,length,toASCII,info)}
            if(!norm.isNormalized(decoded) || startsWithXNDashDash(decoded)){addLabelError(info,Error.INVALID_ACE_LABEL);return markBadACELabel(dest,start,length,toASCII,info)}
            label=decoded;start=0;length=decoded.length
        }else label=dest
        if(length==0){addLabelError(info,Error.EMPTY_LABEL);return replaceLabel(dest,destStart,destLength,label,length)}
        if(length>=4 && label[start+2]=='-' && label[start+3]=='-')addLabelError(info,Error.HYPHEN_3_4)
        if(label[start]=='-')addLabelError(info,Error.LEADING_HYPHEN)
        if(label[start+length-1]=='-')addLabelError(info,Error.TRAILING_HYPHEN)
        var ored=0
        val std3=options and USE_STD3_RULES!=0
        for(i in start until start+length) {
            val c=label[i]
            if(c.code<=0x7f) {
                if(c=='.'){addLabelError(info,Error.LABEL_HAS_DOT);label[i]='\ufffd'}
                else if(std3 && asciiData[c.code]<0){addLabelError(info,Error.DISALLOWED);label[i]='\ufffd'}
            }else {ored=ored or c.code;if(c=='\ufffd')addLabelError(info,Error.DISALLOWED)}
        }
        val c=cpAt(label,start)
        if((1 shl UCharacter.getType(c)) and MARK_MASK!=0) {
            addLabelError(info,Error.LEADING_COMBINING_MARK);label[start]='\ufffd'
            if(c>0xffff){label.deleteAt(start+1);length--;if(label===dest)destLength--}
        }
        if(!hasCertainLabelErrors(info,severeErrors)) {
            if(options and CHECK_BIDI!=0 && (!isBiDi(info) || isOkBiDi(info)))checkLabelBiDi(label,start,length,info)
            if(options and CHECK_CONTEXTJ!=0 && ored and 0x200c==0x200c && !isLabelOkContextJ(label,start,length))addLabelError(info,Error.CONTEXTJ)
            if(options and CHECK_CONTEXTO!=0 && ored>=0xb7)checkLabelContextO(label,start,length,info)
            if(toASCII) {
                if(wasPunycode){if(destLength>63)addLabelError(info,Error.LABEL_TOO_LONG);return destLength}
                else if(ored>=0x80) {
                    val encoded=try {Punycode.encode(label.subSequence(start,start+length),null)}catch(e: StringPrepParseException){throw ICUException(e)}
                    encoded.insert(0,"xn--")
                    if(encoded.length>63)addLabelError(info,Error.LABEL_TOO_LONG)
                    return replaceLabel(dest,destStart,destLength,encoded,encoded.length)
                }else if(length>63)addLabelError(info,Error.LABEL_TOO_LONG)
            }
        }else if(wasPunycode){addLabelError(info,Error.INVALID_ACE_LABEL);return markBadACELabel(dest,destStart,destLength,toASCII,info)}
        return replaceLabel(dest,destStart,destLength,label,length)
    }
    private fun markBadACELabel(dest: StringBuilder,start: Int,initialLength: Int,toASCII: Boolean,info: Info): Int {
        var length=initialLength;val std3=options and USE_STD3_RULES!=0
        var ascii=true;var ldh=true
        for(i in start+4 until start+length) {
            val c=dest[i]
            if(c.code<=0x7f) {
                if(c=='.'){addLabelError(info,Error.LABEL_HAS_DOT);dest[i]='\ufffd';ascii=false;ldh=false}
                else if(asciiData[c.code]<0){ldh=false;if(std3){dest[i]='\ufffd';ascii=false}}
            }else {ascii=false;ldh=false}
        }
        if(ldh){dest.insert(start+length,'\ufffd');length++}
        else if(toASCII && ascii && length>63)addLabelError(info,Error.LABEL_TOO_LONG)
        return length
    }
    private fun checkLabelBiDi(label: CharSequence,start: Int,length: Int,info: Info) {
        var c=cpAt(label,start);var i=start+charCount(c)
        val first=mask(UBiDiProps.INSTANCE.getClass(c))
        if(first and L_R_AL.inv()!=0)setNotOkBiDi(info)
        var limit=start+length
        var last: Int
        while(true) {
            if(i>=limit){last=first;break}
            c=cpBefore(label,limit);limit-=charCount(c)
            val dir=UBiDiProps.INSTANCE.getClass(c)
            if(dir!=D.DIR_NON_SPACING_MARK){last=mask(dir);break}
        }
        if(if(first and L!=0)last and L_EN.inv()!=0 else last and R_AL_EN_AN.inv()!=0)setNotOkBiDi(info)
        var all=first or last
        while(i<limit){c=cpAt(label,i);i+=charCount(c);all=all or mask(UBiDiProps.INSTANCE.getClass(c))}
        if(first and L!=0){if(all and L_EN_OTHER.inv()!=0)setNotOkBiDi(info)}
        else {if(all and R_AL_EN_AN_OTHER.inv()!=0)setNotOkBiDi(info);if(all and EN_AN==EN_AN)setNotOkBiDi(info)}
        if(all and R_AL_AN!=0)setBiDi(info)
    }
    private fun isLabelOkContextJ(label: CharSequence,start: Int,length: Int): Boolean {
        val limit=start+length
        for(i in start until limit) {
            if(label[i]=='\u200c') {
                if(i==start)return false
                var j=i;var c=cpBefore(label,j);j-=charCount(c)
                if(norm.getCombiningClass(c)==9)continue
                while(true) {
                    when(UBiDiProps.INSTANCE.getJoiningType(c)) {
                        UCharacter.JoiningType.TRANSPARENT->{if(j==0)return false;c=cpBefore(label,j);j-=charCount(c)}
                        UCharacter.JoiningType.LEFT_JOINING,UCharacter.JoiningType.DUAL_JOINING->break
                        else->return false
                    }
                }
                j=i+1
                while(true) {
                    if(j==limit)return false
                    c=cpAt(label,j);j+=charCount(c)
                    when(UBiDiProps.INSTANCE.getJoiningType(c)) {
                        UCharacter.JoiningType.TRANSPARENT->{}
                        UCharacter.JoiningType.RIGHT_JOINING,UCharacter.JoiningType.DUAL_JOINING->break
                        else->return false
                    }
                }
            }else if(label[i]=='\u200d') {
                if(i==start || norm.getCombiningClass(cpBefore(label,i))!=9)return false
            }
        }
        return true
    }
    private fun checkLabelContextO(label: CharSequence,start: Int,length: Int,info: Info) {
        val end=start+length-1;var digits=0
        for(i in start..end) {
            var c=label[i].code
            if(c<0xb7)continue
            else if(c<=0x6f9) {
                when {
                    c==0xb7->if(!(start<i && label[i-1]=='l' && i<end && label[i+1]=='l'))addLabelError(info,Error.CONTEXTO_PUNCTUATION)
                    c==0x375->if(!(i<end && UScript.getScript(cpAt(label,i+1))==UScript.GREEK))addLabelError(info,Error.CONTEXTO_PUNCTUATION)
                    c==0x5f3 || c==0x5f4->if(!(start<i && UScript.getScript(cpBefore(label,i))==UScript.HEBREW))addLabelError(info,Error.CONTEXTO_PUNCTUATION)
                    c>=0x660->if(c<=0x669){if(digits>0)addLabelError(info,Error.CONTEXTO_DIGITS);digits=-1}else if(c>=0x6f0){if(digits<0)addLabelError(info,Error.CONTEXTO_DIGITS);digits=1}
                }
            }else if(c==0x30fb) {
                var j=start
                while(true) {
                    if(j>end){addLabelError(info,Error.CONTEXTO_PUNCTUATION);break}
                    c=cpAt(label,j);val script=UScript.getScript(c)
                    if(script==UScript.HIRAGANA || script==UScript.KATAKANA || script==UScript.HAN)break
                    j+=charCount(c)
                }
            }
        }
    }
    companion object {
        private val norm=Normalizer2.getInstance(null,"uts46",Normalizer2.Mode.COMPOSE)
        private val severeErrors=setOf(Error.LEADING_COMBINING_MARK,Error.DISALLOWED,Error.PUNYCODE,Error.LABEL_HAS_DOT,Error.INVALID_ACE_LABEL)
        private val asciiData=IntArray(128){when(it){in 65..90->1;45,46,in 48..57,in 97..122->0;else->-1}}
        private fun isASCIIString(s: CharSequence): Boolean = s.all {it.code<=0x7f}
        private fun startsWithXNDashDash(s: CharSequence): Boolean = s.length>=4 && s[0]=='x' && s[1]=='n' && s[2]=='-' && s[3]=='-'
        private fun charCount(c: Int): Int = if(c>0xffff)2 else 1
        private fun cpAt(s: CharSequence,i: Int): Int {
            val c=s[i];return if(c.isHighSurrogate() && i+1<s.length && s[i+1].isLowSurrogate())(c.code-0xd800)*1024+s[i+1].code-0xdc00+0x10000 else c.code
        }
        private fun cpBefore(s: CharSequence,i: Int): Int {
            val c=s[i-1];return if(c.isLowSurrogate() && i>1 && s[i-2].isHighSurrogate())(s[i-2].code-0xd800)*1024+c.code-0xdc00+0x10000 else c.code
        }
        private fun isASCIIOkBiDi(s: CharSequence,length: Int): Boolean {
            var start=0
            for(i in 0 until length) {
                val c=s[i]
                if(c=='.') {
                    if(i>start && s[i-1] !in 'a'..'z' && s[i-1] !in '0'..'9')return false
                    start=i+1
                }else if(i==start){if(c !in 'a'..'z')return false}
                else if(c.code<=0x20 && (c.code>=0x1c || c.code in 9..13))return false
            }
            return true
        }
        private fun mask(c: Int): Int = 1 shl c
        private val L=mask(D.LEFT_TO_RIGHT)
        private val R_AL=mask(D.RIGHT_TO_LEFT) or mask(D.RIGHT_TO_LEFT_ARABIC)
        private val L_R_AL=L or R_AL
        private val R_AL_AN=R_AL or mask(D.ARABIC_NUMBER)
        private val EN_AN=mask(D.EUROPEAN_NUMBER) or mask(D.ARABIC_NUMBER)
        private val R_AL_EN_AN=R_AL or EN_AN
        private val L_EN=L or mask(D.EUROPEAN_NUMBER)
        private val OTHER=mask(D.EUROPEAN_NUMBER_SEPARATOR) or mask(D.COMMON_NUMBER_SEPARATOR) or mask(D.EUROPEAN_NUMBER_TERMINATOR) or mask(D.OTHER_NEUTRAL) or mask(D.BOUNDARY_NEUTRAL) or mask(D.DIR_NON_SPACING_MARK)
        private val L_EN_OTHER=L_EN or OTHER
        private val R_AL_EN_AN_OTHER=R_AL or EN_AN or OTHER
        private val MARK_MASK=mask(UCharacterCategory.NON_SPACING_MARK.toInt()) or mask(UCharacterCategory.ENCLOSING_MARK.toInt()) or mask(UCharacterCategory.COMBINING_SPACING_MARK.toInt())
    }
}

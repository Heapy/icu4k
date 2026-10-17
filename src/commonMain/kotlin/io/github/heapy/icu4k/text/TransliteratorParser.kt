// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.lang.UCharacter

/** Complete rule syntax; registry lookup is supplied by the owner. Mutable and reusable like ICU4J. */
internal class TransliteratorParser(private val factory: TransliterationFactory) {
    var dataVector = mutableListOf<TransliterationData>()
    var idBlockVector = mutableListOf<String>()
    var compoundFilter: UnicodeSet? = null
    private var curData: TransliterationData? = null
    private var direction = 0
    private var parseData = ParseData()
    private var variablesVector: MutableList<Any?>? = null
    private var variableNames = mutableMapOf<String,CharArray>()
    private var segmentStandins = StringBuilder()
    private var segmentObjects = mutableListOf<StringMatcher?>()
    private var variableNext = '\u0000'
    private var variableLimit = '\u0000'
    private var undefinedVariableName: String? = null
    private var dotStandIn = -1

    private inner class ParseData : SymbolTable {
        override fun lookup(s: String): CharArray? = variableNames[s]
        override fun lookupMatcher(ch: Int): UnicodeMatcher? {
            val i=ch-curData!!.variablesBase.code
            val values=variablesVector!!
            return if(i>=0 && i<values.size) values[i] as UnicodeMatcher? else null
        }
        override fun parseReference(text: String,pos: ParsePosition,limit: Int): String? {
            val start=pos.index
            var i=start
            while(i<limit) {
                val c=transliterationCharAt(text,i).code
                if((i==start && !UCharacter.isUnicodeIdentifierStart(c)) || !UCharacter.isUnicodeIdentifierPart(c))break
                ++i
            }
            if(i==start)return null
            pos.index=i
            return text.substring(start,i)
        }
        fun isMatcher(ch: Int): Boolean {val i=ch-curData!!.variablesBase.code;val v=variablesVector!!;return i<0 || i>=v.size || v[i] is UnicodeMatcher}
        fun isReplacer(ch: Int): Boolean {val i=ch-curData!!.variablesBase.code;val v=variablesVector!!;return i<0 || i>=v.size || v[i] is UnicodeReplacer}
    }
    private class RuleHalf {
        var text=""
        var cursor=-1;var ante=-1;var post=-1;var cursorOffset=0
        private var cursorOffsetPos=0
        var anchorStart=false;var anchorEnd=false
        private var nextSegmentNumber=1
        fun parse(rule: String,pos: Int,limit: Int,parser: TransliteratorParser): Int {
            val buf=StringBuilder()
            val result=parseSection(rule,pos,limit,parser,buf,ILLEGAL_TOP,false)
            text=buf.toString()
            if(cursorOffset>0 && cursor!=cursorOffsetPos)syntaxError("Misplaced |",rule,pos)
            return result
        }
        private fun parseSection(rule: String,initialPos: Int,limit: Int,parser: TransliteratorParser,
            buf: StringBuilder,illegal: UnicodeSet,isSegment: Boolean): Int {
            var pos=initialPos
            val start=pos
            var pp: ParsePosition?=null
            var quoteStart=-1;var quoteLimit=-1;var varStart=-1;var varLimit=-1
            val ref=IntArray(1)
            val bufStart=buf.length
            main@ while(pos<limit) {
                var c=transliterationCharAt(rule,pos++)
                if(PatternProps.isWhiteSpace(c.code))continue
                if(HALF_ENDERS.indexOf(c)>=0){if(isSegment)syntaxError("Unclosed segment",rule,start);break}
                if(anchorEnd)syntaxError("Malformed variable reference",rule,start)
                if(UnicodeSet.resemblesPattern(rule,pos-1)) {
                    if(pp==null)pp=ParsePosition(0)
                    pp.index=pos-1;buf.append(parser.parseSet(rule,pp));pos=pp.index;continue
                }
                if(c=='\\') {
                    if(pos==limit)syntaxError("Trailing backslash",rule,start)
                    val cpLength=Utility.unescapeAndLengthAt(rule,pos)
                    if(cpLength<0)syntaxError("Malformed escape",rule,start)
                    val escaped=Utility.cpFromCodePointAndLength(cpLength)
                    pos+=Utility.lengthFromCodePointAndLength(cpLength)
                    parser.checkVariableRange(escaped,rule,start);UTF16.append(buf,escaped);continue
                }
                if(c=='\'') {
                    var iq=rule.indexOf('\'',pos)
                    if(iq==pos){buf.append(c);++pos}
                    else {
                        quoteStart=buf.length
                        while(true) {
                            if(iq<0)syntaxError("Unterminated quote",rule,start)
                            buf.append(rule.substring(pos,iq));pos=iq+1
                            if(pos<limit && transliterationCharAt(rule,pos)=='\'')iq=rule.indexOf('\'',pos+1)else break
                        }
                        quoteLimit=buf.length
                        for(i in quoteStart until quoteLimit)parser.checkVariableRange(buf[i].code,rule,start)
                    }
                    continue
                }
                parser.checkVariableRange(c.code,rule,start)
                if(illegal.contains(c.code))syntaxError("Illegal character '$c'",rule,start)
                when(c) {
                    '^' -> if(buf.isEmpty() && !anchorStart)anchorStart=true else syntaxError("Misplaced anchor start",rule,start)
                    '(' -> {
                        val begin=buf.length
                        val segment=nextSegmentNumber++
                        pos=parseSection(rule,pos,limit,parser,buf,ILLEGAL_SEG,true)
                        val matcher=StringMatcher(buf.substring(begin),segment,parser.curData!!)
                        parser.setSegmentObject(segment,matcher)
                        buf.setLength(begin);buf.append(parser.getSegmentStandin(segment))
                    }
                    '&','∆' -> {
                        ref[0]=pos
                        val single=TransliteratorIDParser.parseFilterID(rule,ref)
                        if(single==null || !Utility.parseChar(rule,ref,'('))syntaxError("Invalid function",rule,start)
                        val t=single.getInstance(parser.factory) ?: syntaxError("Invalid function ID",rule,start)
                        val begin=buf.length
                        pos=parseSection(rule,ref[0],limit,parser,buf,ILLEGAL_FUNC,true)
                        val replacer=FunctionReplacer(t,StringReplacer(buf.substring(begin),parser.curData!!))
                        buf.setLength(begin);buf.append(parser.generateStandInFor(replacer))
                    }
                    '$' -> {
                        if(pos==limit){anchorEnd=true;continue@main}
                        c=transliterationCharAt(rule,pos)
                        var r=UCharacter.digit(c.code,10)
                        if(r in 1..9) {
                            ref[0]=pos;r=Utility.parseNumber(rule,ref,10)
                            if(r<0)syntaxError("Undefined segment reference",rule,start)
                            pos=ref[0];buf.append(parser.getSegmentStandin(r))
                        } else {
                            if(pp==null)pp=ParsePosition(0)
                            pp.index=pos
                            val name=parser.parseData.parseReference(rule,pp,limit)
                            if(name==null){anchorEnd=true;continue@main}
                            pos=pp.index;varStart=buf.length;parser.appendVariableDef(name,buf);varLimit=buf.length
                        }
                    }
                    '.' -> buf.append(parser.getDotStandIn())
                    '*','+','?' -> {
                        if(isSegment && buf.length==bufStart)syntaxError("Misplaced quantifier",rule,start)
                        val qstart: Int;val qlimit: Int
                        if(buf.length==quoteLimit){qstart=quoteStart;qlimit=quoteLimit}
                        else if(buf.length==varLimit){qstart=varStart;qlimit=varLimit}
                        else {qstart=buf.length-1;qlimit=qstart+1}
                        var m: UnicodeMatcher=try {StringMatcher(buf.toString(),qstart,qlimit,0,parser.curData!!)}catch(e: RuntimeException) {
                            val pre=if(pos<50)rule.substring(0,pos)else "..."+rule.substring(pos-50,pos)
                            val after=if(limit-pos<=50)rule.substring(pos,limit)else rule.substring(pos,pos+50)+"..."
                            throw IllegalIcuArgumentException("Failure in rule: "+pre+"\$\$\$"+after).initCause(e)
                        }
                        val min=if(c=='+')1 else 0
                        val max=if(c=='?')1 else Quantifier.MAX
                        m=Quantifier(m,min,max);buf.setLength(qstart);buf.append(parser.generateStandInFor(m))
                    }
                    ')' -> break@main
                    '{' -> {if(ante>=0)syntaxError("Multiple ante contexts",rule,start);ante=buf.length}
                    '}' -> {if(post>=0)syntaxError("Multiple post contexts",rule,start);post=buf.length}
                    '|' -> {if(cursor>=0)syntaxError("Multiple cursors",rule,start);cursor=buf.length}
                    '@' -> when {
                        cursorOffset<0 -> {if(buf.isNotEmpty())syntaxError("Misplaced $c",rule,start);--cursorOffset}
                        cursorOffset>0 -> {if(buf.length!=cursorOffsetPos || cursor>=0)syntaxError("Misplaced $c",rule,start);++cursorOffset}
                        cursor==0 && buf.isEmpty() -> cursorOffset=-1
                        cursor<0 -> {cursorOffsetPos=buf.length;cursorOffset=1}
                        else -> syntaxError("Misplaced $c",rule,start)
                    }
                    else -> {
                        if(c.code in 0x21..0x7e && c !in '0'..'9' && c !in 'A'..'Z' && c !in 'a'..'z')syntaxError("Unquoted $c",rule,start)
                        buf.append(c)
                    }
                }
            }
            return pos
        }
        fun removeContext(){text=text.substring(if(ante<0)0 else ante,if(post<0)text.length else post);ante=-1;post=-1;anchorStart=false;anchorEnd=false}
        fun isValidOutput(parser: TransliteratorParser): Boolean = valid(parser,false)
        fun isValidInput(parser: TransliteratorParser): Boolean = valid(parser,true)
        private fun valid(parser: TransliteratorParser,input: Boolean): Boolean {
            var i=0
            while(i<text.length){val cp=UTF16.charAt(text,i);i+=UTF16.getCharCount(cp);if(if(input)!parser.parseData.isMatcher(cp)else !parser.parseData.isReplacer(cp))return false}
            return true
        }
    }
    fun parse(rules: String,dir: Int) = parseLines(arrayOf(rules),dir)
    fun parseLines(lines: Array<String>,dir: Int) {
        var parsingIDs=true;var ruleCount=0
        dataVector=mutableListOf();idBlockVector=mutableListOf();curData=null;direction=dir;compoundFilter=null
        variablesVector=mutableListOf();variableNames=mutableMapOf();parseData=ParseData()
        val errors=ArrayList<RuntimeException>();var errorCount=0
        val idBlock=StringBuilder();var filterOffset=-1;var line=0
        main@ while(line<lines.size) {
            var rule=lines[line++]
            if(rule.isNotEmpty() && rule.last()=='\\') {
                val b=StringBuilder(rule)
                do {b.setLength(b.length-1);if(line>=lines.size)break;rule=lines[line++];b.append(rule)}while(rule.isNotEmpty() && rule.last()=='\\')
                rule=b.toString()
            }
            var pos=0;val limit=rule.length
            while(pos<limit) {
                var c=transliterationCharAt(rule,pos++)
                if(PatternProps.isWhiteSpace(c.code))continue
                if(c=='#'){pos=rule.indexOf('\n',pos)+1;if(pos==0)break;continue}
                if(c==';')continue
                try {
                    ++ruleCount;--pos
                    if(pos+3<=limit && rule.regionMatches(pos,"::",0,2)) {
                        pos+=2;c=transliterationCharAt(rule,pos)
                        while(PatternProps.isWhiteSpace(c.code) && pos<limit){++pos;c=transliterationCharAt(rule,pos)}
                        val p=intArrayOf(pos)
                        if(!parsingIDs) {
                            if(curData!=null){if(direction==0)dataVector.add(curData!!)else dataVector.add(0,curData!!);curData=null}
                            parsingIDs=true
                        }
                        val id=TransliteratorIDParser.parseSingleID(rule,p,direction)
                        if(p[0]!=pos && Utility.parseChar(rule,p,';')) {
                            if(direction==0)idBlock.append(id!!.canonID).append(';')else idBlock.insert(0,id!!.canonID+";")
                        } else {
                            val parens=intArrayOf(-1)
                            val f=TransliteratorIDParser.parseGlobalFilter(rule,p,direction,parens,null)
                            if(f!=null && Utility.parseChar(rule,p,';')) {
                                if((direction==0)==(parens[0]==0)) {
                                    if(compoundFilter!=null)syntaxError("Multiple global filters",rule,pos)
                                    compoundFilter=f;filterOffset=ruleCount
                                }
                            } else syntaxError("Invalid ::ID",rule,pos)
                        }
                        pos=p[0]
                    } else {
                        if(parsingIDs) {
                            if(direction==0)idBlockVector.add(idBlock.toString())else idBlockVector.add(0,idBlock.toString())
                            idBlock.setLength(0);parsingIDs=false;curData=TransliterationData();setVariableRange(0xf000,0xf8ff)
                        }
                        if(resemblesPragma(rule,pos,limit)) {
                            val p=parsePragma(rule,pos,limit)
                            if(p<0)syntaxError("Unrecognized pragma",rule,pos)
                            pos=p
                        } else pos=parseRule(rule,pos,limit)
                    }
                } catch(e: IllegalArgumentException) {
                    if(errorCount==30){errors.add(IllegalIcuArgumentException("\nMore than 30 errors; further messages squelched").initCause(e));break@main}
                    errors.add(e);++errorCount;pos=ruleEnd(rule,pos,limit)+1
                }
            }
        }
        if(parsingIDs && idBlock.isNotEmpty()) {if(direction==0)idBlockVector.add(idBlock.toString())else idBlockVector.add(0,idBlock.toString())}
        else if(!parsingIDs && curData!=null){if(direction==0)dataVector.add(curData!!)else dataVector.add(0,curData!!)}
        for(data in dataVector){data.variables=variablesVector!!.toTypedArray();data.variableNames=variableNames.toMutableMap()}
        variablesVector=null
        try {
            if(compoundFilter!=null && ((direction==0 && filterOffset!=1)||(direction==1 && filterOffset!=ruleCount)))throw IllegalIcuArgumentException("Compound filters misplaced")
            for(data in dataVector)data.ruleSet.freeze()
            if(idBlockVector.size==1 && idBlockVector[0].isEmpty())idBlockVector.removeAt(0)
        }catch(e: IllegalArgumentException){errors.add(e)}
        if(errors.isNotEmpty())throw linkTransliterationErrors(errors)
    }
    private fun parseRule(rule: String,initialPos: Int,limit: Int): Int {
        var pos=initialPos;val start=pos;var operator='\u0000'
        segmentStandins=StringBuilder();segmentObjects=mutableListOf()
        var left=RuleHalf();var right=RuleHalf();undefinedVariableName=null
        pos=left.parse(rule,pos,limit,this)
        if(pos!=limit){operator=transliterationCharAt(rule,--pos)}
        if(pos==limit || OPERATORS.indexOf(operator)<0)syntaxError("No operator pos=$pos",rule,start)
        ++pos
        if(operator=='<' && pos<limit && transliterationCharAt(rule,pos)=='>'){++pos;operator='~'}
        operator=when(operator){'→'->'>'; '←'->'<'; '↔'->'~';else->operator}
        pos=right.parse(rule,pos,limit,this)
        if(pos<limit){if(transliterationCharAt(rule,--pos)==';')++pos else syntaxError("Unquoted operator",rule,start)}
        if(operator=='=') {
            if(undefinedVariableName==null)syntaxError("Missing '$' or duplicate definition",rule,start)
            if(left.text.length!=1 || transliterationCharAt(left.text,0)!=variableLimit)syntaxError("Malformed LHS",rule,start)
            if(left.anchorStart || left.anchorEnd || right.anchorStart || right.anchorEnd)syntaxError("Malformed variable def",rule,start)
            variableNames[undefinedVariableName!!]=right.text.toCharArray();variableLimit=(variableLimit.code+1).toChar();return pos
        }
        if(undefinedVariableName!=null)syntaxError("Undefined variable $"+undefinedVariableName,rule,start)
        if(segmentStandins.length>segmentObjects.size)syntaxError("Undefined segment reference",rule,start)
        for(c in segmentStandins)if(c=='\u0000')syntaxError("Internal error",rule,start)
        for(obj in segmentObjects)if(obj==null)syntaxError("Internal error",rule,start)
        if(operator!='~' && ((direction==0)!=(operator=='>')))return pos
        if(direction==1){val temp=left;left=right;right=temp}
        if(operator=='~'){right.removeContext();left.cursor=-1;left.cursorOffset=0}
        if(left.ante<0)left.ante=0
        if(left.post<0)left.post=left.text.length
        if(right.ante>=0 || right.post>=0 || left.cursor>=0 || (right.cursorOffset!=0 && right.cursor<0) || right.anchorStart || right.anchorEnd ||
            !left.isValidInput(this) || !right.isValidOutput(this) || left.ante>left.post)syntaxError("Malformed rule",rule,start)
        val segments=if(segmentObjects.isEmpty())null else Array<UnicodeMatcher>(segmentObjects.size){segmentObjects[it]!!}
        curData!!.ruleSet.addRule(TransliterationRule(left.text,left.ante,left.post,right.text,right.cursor,right.cursorOffset,segments,left.anchorStart,left.anchorEnd,curData!!))
        return pos
    }
    private fun setVariableRange(start: Int,end: Int) {
        if(start>end || start<0 || end>0xffff)throw IllegalIcuArgumentException("Invalid variable range $start, $end")
        curData!!.variablesBase=start.toChar()
        if(dataVector.isEmpty()){variableNext=start.toChar();variableLimit=(end+1).toChar()}
    }
    private fun checkVariableRange(ch: Int,rule: String,start: Int){if(ch>=curData!!.variablesBase.code && ch<variableLimit.code)syntaxError("Variable range character in rule",rule,start)}
    private fun parsePragma(rule: String,initialPos: Int,limit: Int): Int {
        val pos=initialPos+4;val array=IntArray(2)
        var p=Utility.parsePattern(rule,pos,limit,"~variable range # #~;",array)
        if(p>=0){setVariableRange(array[0],array[1]);return p}
        p=Utility.parsePattern(rule,pos,limit,"~maximum backup #~;",array)
        if(p>=0)throw IllegalIcuArgumentException("use maximum backup pragma not implemented yet")
        p=Utility.parsePattern(rule,pos,limit,"~nfd rules~;",null)
        if(p>=0)throw IllegalIcuArgumentException("use normalize rules pragma not implemented yet")
        p=Utility.parsePattern(rule,pos,limit,"~nfc rules~;",null)
        if(p>=0)throw IllegalIcuArgumentException("use normalize rules pragma not implemented yet")
        return -1
    }
    private fun parseSet(rule: String,pos: ParsePosition): Char {
        val set=UnicodeSet(rule,pos,parseData)
        if(variableNext>=variableLimit)throw RuntimeException("Private use variables exhausted")
        set.compact();return generateStandInFor(set)
    }
    fun generateStandInFor(obj: Any): Char {
        val values=variablesVector!!
        for(i in values.indices)if(values[i]===obj)return (curData!!.variablesBase.code+i).toChar()
        if(variableNext>=variableLimit)throw RuntimeException("Variable range exhausted")
        values.add(obj);val result=variableNext;variableNext=(variableNext.code+1).toChar();return result
    }
    fun getSegmentStandin(seg: Int): Char {
        if(segmentStandins.length<seg)segmentStandins.setLength(seg)
        var c=segmentStandins[seg-1]
        if(c=='\u0000') {
            if(variableNext>=variableLimit)throw RuntimeException("Variable range exhausted")
            c=variableNext;variableNext=(variableNext.code+1).toChar();variablesVector!!.add(null);segmentStandins[seg-1]=c
        }
        return c
    }
    fun setSegmentObject(seg: Int,obj: StringMatcher) {
        while(segmentObjects.size<seg)segmentObjects.add(null)
        val index=getSegmentStandin(seg).code-curData!!.variablesBase.code
        if(segmentObjects[seg-1]!=null || variablesVector!![index]!=null)throw RuntimeException()
        segmentObjects[seg-1]=obj;variablesVector!![index]=obj
    }
    fun getDotStandIn(): Char {
        if(dotStandIn==-1)dotStandIn=generateStandInFor(UnicodeSet(DOT_SET)).code
        return dotStandIn.toChar()
    }
    private fun appendVariableDef(name: String,buf: StringBuilder) {
        val value=variableNames[name]
        if(value==null) {
            if(undefinedVariableName!=null)throw IllegalIcuArgumentException("Undefined variable $"+name)
            undefinedVariableName=name
            if(variableNext>=variableLimit)throw RuntimeException("Private use variables exhausted")
            variableLimit=(variableLimit.code-1).toChar();buf.append(variableLimit)
        }else buf.append(value.concatToString())
    }
    companion object {
        private const val OPERATORS="=><←→↔"
        private const val HALF_ENDERS="=><←→↔;"
        private const val DOT_SET="[^[:Zp:][:Zl:]\\r\\n$]"
        private val ILLEGAL_TOP=UnicodeSet("[\\)]")
        private val ILLEGAL_SEG=UnicodeSet("[\\{\\}\\|\\@]")
        private val ILLEGAL_FUNC=UnicodeSet("[\\^\\(\\.\\*\\+\\?\\{\\}\\|\\@]")
        fun resemblesPragma(rule: String,pos: Int,limit: Int): Boolean = Utility.parsePattern(rule,pos,limit,"use ",null)>=0
        fun syntaxError(msg: String,rule: String,start: Int): Nothing = throw IllegalIcuArgumentException(msg+" in \""+Utility.escape(rule.substring(start,ruleEnd(rule,start,rule.length)))+'"')
        fun ruleEnd(rule: String,start: Int,limit: Int): Int {val end=Utility.quotedIndexOf(rule,start,limit,";");return if(end<0)limit else end}
    }
}
internal expect fun linkTransliterationErrors(errors: List<RuntimeException>): RuntimeException

/** Kotlin Throwable has no public cause mutator; preserve mutable ICU errors and rebuild other links. */
internal fun linkPortableTransliterationErrors(errors: List<RuntimeException>): RuntimeException {
    fun copy(error: RuntimeException,cause: Throwable): RuntimeException = when(error) {
        is IllegalIcuArgumentException -> IllegalIcuArgumentException(error.message,cause)
        is IndexOutOfBoundsException -> TransliterationChainedBounds(error.message,cause)
        is IllegalArgumentException -> IllegalArgumentException(error.message,cause)
        is IllegalStateException -> IllegalStateException(error.message,cause)
        else -> RuntimeException(error.message,cause)
    }
    fun append(error: RuntimeException,next: RuntimeException): RuntimeException {
        val cause=error.cause
        if(cause!=null){val linked=append(cause as RuntimeException,next);if(linked===cause)return error;return copy(error,linked)}
        return if(error is IllegalIcuArgumentException)error.initCause(next)else copy(error,next)
    }
    var error=errors.last()
    for(i in errors.lastIndex-1 downTo 0)error=append(errors[i],error)
    return error
}

private class TransliterationChainedBounds(message: String?,override val cause: Throwable) : IndexOutOfBoundsException(message)

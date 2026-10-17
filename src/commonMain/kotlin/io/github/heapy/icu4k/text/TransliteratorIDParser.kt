// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.impl.createTextTrieMapLock
import io.github.heapy.icu4k.util.CaseInsensitiveString

internal object TransliteratorIDParser {
    private val specialInverses = HashMap<CaseInsensitiveString,String?>()
    private val lock = createTextTrieMapLock(specialInverses)
    private class Specs(val source: String,val target: String,val variant: String?,val sawSource: Boolean,val filter: String?)
    class SingleID(var canonID: String,var basicID: String?,var filter: String? = null) {
        fun getInstance(factory: TransliterationFactory): TransliterationFunction? {
            val t = factory.getBasicInstance(if(basicID.isNullOrEmpty()) "Any-Null" else basicID!!,canonID)
            if(t != null && filter != null) t.setFilter(UnicodeSet(filter!!))
            return t
        }
    }
    fun parseFilterID(id: String,pos: IntArray): SingleID? {
        val start=pos[0]
        val specs=parseFilterID(id,pos,true) ?: run {pos[0]=start;return null}
        return specsToID(specs,0).apply {filter=specs.filter}
    }
    fun parseSingleID(id: String,pos: IntArray,dir: Int): SingleID? {
        val start=pos[0]
        var a: Specs?=null
        var b: Specs?=null
        var sawParen=false
        for(pass in 1..2) {
            if(pass==2) { a=parseFilterID(id,pos,true);if(a==null){pos[0]=start;return null} }
            if(Utility.parseChar(id,pos,'(')) {
                sawParen=true
                if(!Utility.parseChar(id,pos,')')) {
                    b=parseFilterID(id,pos,true)
                    if(b==null || !Utility.parseChar(id,pos,')')) {pos[0]=start;return null}
                }
                break
            }
        }
        val single: SingleID
        if(sawParen) {
            if(dir==0) {
                single=specsToID(a,0)
                single.canonID += "("+specsToID(b,0).canonID+")"
                if(a!=null) single.filter=a.filter
            } else {
                single=specsToID(b,0)
                single.canonID += "("+specsToID(a,0).canonID+")"
                if(b!=null) single.filter=b.filter
            }
        } else {
            single=if(dir==0) specsToID(a,0) else specsToSpecialInverse(a!!) ?: specsToID(a,1)
            single.filter=a!!.filter
        }
        return single
    }
    fun parseGlobalFilter(id: String,pos: IntArray,dir: Int,withParens: IntArray,canonID: StringBuilder?): UnicodeSet? {
        val start=pos[0]
        if(withParens[0]==-1) withParens[0]=if(Utility.parseChar(id,pos,'(')) 1 else 0
        else if(withParens[0]==1 && !Utility.parseChar(id,pos,'(')) {pos[0]=start;return null}
        pos[0]=PatternProps.skipWhiteSpace(id,pos[0])
        if(!UnicodeSet.resemblesPattern(id,pos[0])) return null
        val pp=ParsePosition(pos[0])
        val filter=try {UnicodeSet(id,pp,null)} catch(e: IllegalArgumentException){pos[0]=start;return null}
        var pattern=id.substring(pos[0],pp.index)
        pos[0]=pp.index
        if(withParens[0]==1 && !Utility.parseChar(id,pos,')')) {pos[0]=start;return null}
        if(canonID!=null) {
            if(dir==0) {if(withParens[0]==1) pattern="($pattern)";canonID.append(pattern).append(';')}
            else {if(withParens[0]==0) pattern="($pattern)";canonID.insert(0,"$pattern;")}
        }
        return filter
    }
    fun parseCompoundID(id: String,dir: Int,canonID: StringBuilder,list: MutableList<SingleID>,globalFilter: Array<UnicodeSet?>): Boolean {
        val pos=intArrayOf(0)
        val parens=intArrayOf(0)
        list.clear();globalFilter[0]=null;canonID.setLength(0)
        var filter=parseGlobalFilter(id,pos,dir,parens,canonID)
        if(filter!=null) {
            if(!Utility.parseChar(id,pos,';')) {canonID.setLength(0);pos[0]=0}
            if(dir==0) globalFilter[0]=filter
        }
        var sawDelimiter=true
        while(true) {
            val single=parseSingleID(id,pos,dir) ?: break
            if(dir==0) list.add(single) else list.add(0,single)
            if(!Utility.parseChar(id,pos,';')) {sawDelimiter=false;break}
        }
        if(list.isEmpty()) return false
        for(i in list.indices) {canonID.append(list[i].canonID);if(i!=list.lastIndex)canonID.append(';')}
        if(sawDelimiter) {
            parens[0]=1
            filter=parseGlobalFilter(id,pos,dir,parens,canonID)
            if(filter!=null){Utility.parseChar(id,pos,';');if(dir==1)globalFilter[0]=filter}
        }
        pos[0]=PatternProps.skipWhiteSpace(id,pos[0])
        return pos[0]==id.length
    }
    fun instantiateList(ids: List<SingleID>,factory: TransliterationFactory): List<TransliterationFunction> {
        val result=ArrayList<TransliterationFunction>()
        for(single in ids) {
            if(single.basicID!!.isEmpty())continue
            result.add(single.getInstance(factory) ?: throw IllegalArgumentException("Illegal ID "+single.canonID))
        }
        if(result.isEmpty())result.add(factory.getBasicInstance("Any-Null",null) ?: throw IllegalArgumentException("Internal error; cannot instantiate Any-Null"))
        return result
    }
    fun IDtoSTV(id: String): Array<String?> {
        var source="Any"
        val target: String
        var variant: String
        var sep=id.indexOf('-')
        var v=id.indexOf('/')
        if(v<0)v=id.length
        var sawSource=false
        if(sep<0){target=id.substring(0,v);variant=id.substring(v)}
        else if(sep<v){if(sep>0){source=id.substring(0,sep);sawSource=true};target=id.substring(++sep,v);variant=id.substring(v)}
        else {if(v>0){source=id.substring(0,v);sawSource=true};variant=id.substring(v,sep++);target=id.substring(sep)}
        if(variant.isNotEmpty())variant=variant.substring(1)
        return arrayOf(source,target,variant,if(sawSource)"" else null)
    }
    fun STVtoID(source: String,target: String?,variant: String?): String =
        (if(source.isEmpty())"Any" else source)+"-"+target+(if(variant.isNullOrEmpty())"" else "/$variant")
    fun registerSpecialInverse(target: String?,inverseTarget: String?,bidirectional: Boolean) {
        // Source synchronizes the two map writes separately.
        lock.withLock {specialInverses[CaseInsensitiveString(target)]=inverseTarget}
        if(bidirectional && !target!!.equals(inverseTarget,true))lock.withLock {specialInverses[CaseInsensitiveString(inverseTarget)]=target}
    }
    private fun parseFilterID(id: String,pos: IntArray,allowFilter: Boolean): Specs? {
        var first: String?=null;var source: String?=null;var target: String?=null;var variant: String?=null;var filter: String?=null
        var delimiter='\u0000';var count=0
        val start=pos[0]
        while(true) {
            pos[0]=PatternProps.skipWhiteSpace(id,pos[0]);if(pos[0]==id.length)break
            if(allowFilter && filter==null && UnicodeSet.resemblesPattern(id,pos[0])) {
                val pp=ParsePosition(pos[0]);UnicodeSet(id,pp,null);filter=id.substring(pos[0],pp.index);pos[0]=pp.index;continue
            }
            if(delimiter=='\u0000') {
                val c=transliterationCharAt(id,pos[0])
                if((c=='-' && target==null)||(c=='/' && variant==null)){delimiter=c;++pos[0];continue}
            }
            if(delimiter=='\u0000' && count>0)break
            val spec=Utility.parseUnicodeIdentifier(id,pos) ?: break
            when(delimiter){'\u0000'->first=spec;'-'->target=spec;'/'->variant=spec}
            ++count;delimiter='\u0000'
        }
        if(first!=null){if(target==null)target=first else source=first}
        if(source==null && target==null){pos[0]=start;return null}
        val sawSource=source!=null
        if(source==null)source="Any"
        if(target==null)target="Any"
        return Specs(source,target,variant,sawSource,filter)
    }
    private fun specsToID(specs: Specs?,dir: Int): SingleID {
        if(specs==null)return SingleID("","")
        val buf=StringBuilder();var prefix=""
        if(dir==0){if(specs.sawSource)buf.append(specs.source).append('-')else prefix=specs.source+"-";buf.append(specs.target)}
        else buf.append(specs.target).append('-').append(specs.source)
        if(specs.variant!=null)buf.append('/').append(specs.variant)
        val basic=prefix+buf.toString()
        if(specs.filter!=null)buf.insert(0,specs.filter)
        return SingleID(buf.toString(),basic)
    }
    private fun specsToSpecialInverse(specs: Specs): SingleID? {
        if(!specs.source.equals("Any",true))return null
        val inverse=lock.withLock {specialInverses[CaseInsensitiveString(specs.target)]} ?: return null
        val buf=StringBuilder()
        if(specs.filter!=null)buf.append(specs.filter)
        if(specs.sawSource)buf.append("Any-")
        buf.append(inverse)
        var basic="Any-$inverse"
        if(specs.variant!=null){buf.append('/').append(specs.variant);basic+="/"+specs.variant}
        return SingleID(buf.toString(),basic)
    }
}

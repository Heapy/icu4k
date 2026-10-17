// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import java.lang.reflect.InvocationTargetException
import com.ibm.icu.text.Replaceable as OracleReplaceable
import com.ibm.icu.text.ReplaceableString as OracleText

class TransliterationStringOracleTest {
    private val dataClass = Class.forName("com.ibm.icu.text.RuleBasedTransliterator\$Data")
    private val matcherClass = Class.forName("com.ibm.icu.text.StringMatcher")
    private val replacerClass = Class.forName("com.ibm.icu.text.StringReplacer")
    private fun make(type: Class<*>, types: Array<Class<*>>, vararg args: Any?): Any =
        type.getDeclaredConstructor(*types).apply { isAccessible = true }.newInstance(*args)
    private fun call(obj: Any, method: String, types: Array<Class<*>>, vararg args: Any?): Any? = try {
        obj.javaClass.getDeclaredMethod(method, *types).apply { isAccessible = true }.invoke(obj, *args)
    } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun data(values: Array<Any?> = emptyArray()): Pair<Any, TransliterationData> {
        val source = make(dataClass, emptyArray())
        dataClass.getDeclaredField("variables").apply { isAccessible = true }.set(source, values)
        dataClass.getDeclaredField("variablesBase").apply { isAccessible = true }.setChar(source, '\ue000')
        return source to TransliterationData().apply { variables = emptyArray(); variablesBase = '\ue000' }
    }
    private fun matcher(pattern: String, segment: Int, data: Any): Any =
        make(matcherClass, arrayOf(String::class.java, Int::class.javaPrimitiveType!!, dataClass), pattern, segment, data)
    private fun replacer(output: String, cursor: Int?, data: Any): Any = if (cursor == null)
        make(replacerClass, arrayOf(String::class.java, dataClass), output, data)
    else make(replacerClass, arrayOf(String::class.java, Int::class.javaPrimitiveType!!, dataClass), output, cursor, data)
    private val matchTypes = arrayOf(OracleReplaceable::class.java, IntArray::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
    private val replaceTypes = arrayOf(OracleReplaceable::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, IntArray::class.java)
    private val booleanType = arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!)
    private fun outcome(action: () -> Any?): String = try { "OK:" + action() } catch (e: Throwable) { "ERR:" + if (e is IndexOutOfBoundsException) "bounds" else e::class.java.simpleName }

    @Test fun literalMatchingBothDirectionsAndIncrementalCapture() {
        val patterns = listOf("", "a", "ab", "a b'\\", "😀", "a😀b", charArrayOf('\ud800', 'x').concatToString())
        val texts = listOf("", "abc", "ab ab", "😀a😀b", "a b'\\", charArrayOf('a', '\ud800', 'x').concatToString())
        for (pattern in patterns) for (value in texts) for (segment in listOf(0, 1, -3)) {
            val (sd, pd) = data()
            val source = matcher(pattern, segment, sd)
            val port = StringMatcher(pattern, segment, pd)
            for (escape in listOf(false, true)) assertEquals(call(source, "toPattern", booleanType, escape), port.toPattern(escape))
            assertEquals(call(source, "toReplacerPattern", booleanType, false), port.toReplacerPattern(false))
            for (start in -1..value.length) for (limit in -1..value.length) for (incremental in listOf(false, true)) {
                val so = intArrayOf(start); val po = intArrayOf(start)
                val st = OracleText(value); val pt = ReplaceableString(value)
                assertEquals(outcome { call(source, "matches", matchTypes, st, so, limit, incremental) }, outcome { port.matches(pt, po, limit, incremental) }, "$pattern/$value/$start/$limit/$incremental")
                assertContentEquals(so, po)
                val sc = intArrayOf(77); val pc = intArrayOf(77)
                assertEquals(outcome { call(source, "replace", replaceTypes, st, 0, 0, sc) }, outcome { port.replace(pt, 0, 0, pc) })
                assertEquals(st.toString(), pt.toString()); assertContentEquals(sc, pc)
                call(source, "resetMatch", emptyArray()); port.resetMatch()
            }
        }
    }

    @Test fun literalReplacementsCursorAndWarmFastPath() {
        val outputs = listOf("", "abc", "a b'\\", "😀x", charArrayOf('\ud800', 'x', '\udc00').concatToString())
        val texts = listOf("", "abc", "😀a😀b", "xy")
        for (output in outputs) for (value in texts) for (cursor in listOf(null, -4, -1, 0, 1, 2, 5, 8)) {
            val (sd, pd) = data()
            val source = replacer(output, cursor, sd)
            val port = if (cursor == null) StringReplacer(output, pd) else StringReplacer(output, cursor, pd)
            for (escape in listOf(false, true)) assertEquals(call(source, "toReplacerPattern", booleanType, escape), port.toReplacerPattern(escape), "$output/$cursor")
            for (start in 0..value.length) for (limit in start..value.length) repeat(2) {
                val st = OracleText(value); val pt = ReplaceableString(value)
                val sc = intArrayOf(77); val pc = intArrayOf(77)
                assertEquals(outcome { call(source, "replace", replaceTypes, st, start, limit, sc) }, outcome { port.replace(pt, start, limit, pc) }, "$output/$cursor/$value/$start/$limit")
                assertEquals(st.toString(), pt.toString()); assertContentEquals(sc, pc)
            }
        }
    }

    @Test fun standInMatchersAndSegmentReplacement() {
        val (sd, pd) = data()
        val sourceSet = com.ibm.icu.text.UnicodeSet("[a-z😀]")
        val sourceSegment = matcher("ab", 1, sd)
        val portSet = UnicodeSet("[a-z😀]")
        val portSegment = StringMatcher("ab", 1, pd)
        dataClass.getDeclaredField("variables").apply { isAccessible = true }.set(sd, arrayOf(sourceSet, sourceSegment))
        pd.variables = arrayOf(portSet, portSegment)
        for (pattern in listOf("\ue000", "\ue000\ue001", "x\ue001", "\ue001\ue001")) {
            val source = matcher(pattern, 2, sd); val port = StringMatcher(pattern, 2, pd)
            for (escape in listOf(false, true)) assertEquals(call(source, "toPattern", booleanType, escape), port.toPattern(escape))
            for (v in 0..255) assertEquals(call(source, "matchesIndexValue", arrayOf(Int::class.javaPrimitiveType!!), v), port.matchesIndexValue(v))
            val ss = com.ibm.icu.text.UnicodeSet(); val ps = UnicodeSet()
            call(source, "addMatchSetTo", arrayOf(com.ibm.icu.text.UnicodeSet::class.java), ss); port.addMatchSetTo(ps)
            assertEquals(ss.toPattern(false), ps.toPattern(false))
            for (value in listOf("ab", "aab", "😀ab", "xabab", "")) {
                val sc = intArrayOf(0); val pc = intArrayOf(0)
                call(sourceSegment, "resetMatch", emptyArray()); portSegment.resetMatch()
                assertEquals(call(source, "matches", matchTypes, OracleText(value), sc, value.length, true), port.matches(ReplaceableString(value), pc, value.length, true))
                val sr = replacer("<\ue001>", 2, sd); val pr = StringReplacer("<\ue001>", 2, pd)
                val st = OracleText(value); val pt = ReplaceableString(value)
                assertEquals(call(sr, "replace", replaceTypes, st, 0, value.length, sc), pr.replace(pt, 0, value.length, pc))
                assertEquals(st.toString(), pt.toString()); assertContentEquals(sc, pc)
            }
        }
    }
    private class Traced(private val delegate: Replaceable, val trace: MutableList<String>) : Replaceable {
        override fun length(): Int { trace.add("length");return delegate.length() }
        override fun charAt(offset: Int): Char { trace.add("charAt:$offset");return delegate.charAt(offset) }
        override fun char32At(offset: Int): Int { trace.add("char32At:$offset");return delegate.char32At(offset) }
        override fun getChars(srcStart: Int,srcLimit: Int,dst: CharArray,dstStart: Int) {trace.add("getChars:$srcStart,$srcLimit,$dstStart");delegate.getChars(srcStart,srcLimit,dst,dstStart)}
        override fun replace(start: Int,limit: Int,text: String) {trace.add("replace:$start,$limit,$text");delegate.replace(start,limit,text)}
        override fun replace(start: Int,limit: Int,chars: CharArray,charsStart: Int,charsLen: Int) {trace.add("replaceChars:$start,$limit,$charsStart,$charsLen");delegate.replace(start,limit,chars,charsStart,charsLen)}
        override fun copy(start: Int,limit: Int,dest: Int) {trace.add("copy:$start,$limit,$dest");delegate.copy(start,limit,dest)}
        override fun hasMetaData(): Boolean {trace.add("hasMetaData");return true}
    }
    @Test fun metadataOperationOrderingAndNestedReplacement() {
        for(value in listOf("abc","😀abc😀","")) for(start in 0..value.length) for(limit in start..value.length) {
            val (sd,pd)=data()
            val sourceNested=replacer("😀'",null,sd)
            val portNested=StringReplacer("😀'",pd)
            dataClass.getDeclaredField("variables").apply{isAccessible=true}.set(sd,arrayOf(sourceNested))
            pd.variables=arrayOf(portNested)
            val source=replacer("a\ue000z",2,sd);val port=StringReplacer("a\ue000z",2,pd)
            val sourceText=OracleText(value);val portText=ReplaceableString(value)
            val sourceTrace=mutableListOf<String>();val portTrace=mutableListOf<String>()
            val proxy=java.lang.reflect.Proxy.newProxyInstance(OracleReplaceable::class.java.classLoader,arrayOf(OracleReplaceable::class.java)) { _,method,args ->
                val a=args ?: emptyArray()
                sourceTrace.add(when(method.name) {
                    "length","hasMetaData" -> method.name
                    "charAt","char32At" -> method.name+":"+a[0]
                    "copy" -> "copy:"+a.joinToString(",")
                    "replace" -> "replace:"+a.joinToString(",")
                    else -> method.name+":"+a.joinToString(",")
                })
                if(method.name=="hasMetaData") true else try {method.invoke(sourceText,*a)}catch(e:InvocationTargetException){throw e.cause!!}
            }
            val sc=intArrayOf(77);val pc=intArrayOf(77)
            assertEquals(outcome{call(source,"replace",replaceTypes,proxy,start,limit,sc)},outcome{port.replace(Traced(portText,portTrace),start,limit,pc)})
            assertEquals(sourceTrace,portTrace,"$value/$start/$limit")
            assertEquals(sourceText.toString(),portText.toString());assertContentEquals(sc,pc)
        }
    }

    @Test fun minimumSegmentNumberOverflow() {
        val (sd,pd)=data()
        val source=matcher("a",Int.MIN_VALUE,sd);val port=StringMatcher("a",Int.MIN_VALUE,pd)
        assertEquals(outcome{call(source,"toReplacerPattern",booleanType,false)},outcome{port.toReplacerPattern(false)})
    }

}

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.text.Collator as Source
import com.ibm.icu.text.RuleBasedCollator as SourceRbc
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import kotlin.random.Random
import kotlin.test.*

class CollationLocaleOptionsOracleTest {
    private val apply=Source::class.java.getDeclaredMethod("setAttributesFromKeywords",SourceLocale::class.java,Source::class.java,SourceRbc::class.java).apply { isAccessible=true }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private class SourceRecording : Source() {
        val delegate=SourceRbc("")
        val calls=mutableListOf<Any>()
        override fun setStrength(value: Int) { calls.add("strength" to value); delegate.setStrength(value) }
        override fun setDecomposition(value: Int) { calls.add("decomposition" to value); delegate.setDecomposition(value) }
        override fun setReorderCodes(vararg order: Int) { calls.add("reorder" to order.toList()); delegate.setReorderCodes(*order) }
        override fun setMaxVariable(value: Int): Source { calls.add("maxVariable" to value); delegate.setMaxVariable(value); return this }
        override fun compare(a: String?, b: String?): Int = delegate.compare(a,b)
        override fun getCollationKey(s: String?): com.ibm.icu.text.CollationKey? = delegate.getCollationKey(s)
        override fun getRawCollationKey(s: String?, key: com.ibm.icu.text.RawCollationKey?): com.ibm.icu.text.RawCollationKey? = delegate.getRawCollationKey(s,key)
        override fun setVariableTop(s: String?): Int = delegate.setVariableTop(s)
        override fun setVariableTop(value: Int) = delegate.setVariableTop(value)
        override fun getVariableTop(): Int = delegate.variableTop
        override fun getVersion(): com.ibm.icu.util.VersionInfo = delegate.version
        override fun getUCAVersion(): com.ibm.icu.util.VersionInfo = delegate.ucaVersion
    }
    private class TargetRecording(private val c: RuleBasedCollatorSettings) : CollationLocaleOptions.CollatorSink, CollationLocaleOptions.RuleBasedSink {
        val calls=mutableListOf<Any>()
        override fun setStrength(value: Int) { calls.add("strength" to value); c.setStrength(value) }
        override fun setDecomposition(value: Int) { calls.add("decomposition" to value); c.setDecomposition(value) }
        override fun setReorderCodes(order: IntArray) { calls.add("reorder" to order.toList()); c.setReorderCodes(order) }
        override fun setMaxVariable(value: Int) { calls.add("maxVariable" to value); c.setMaxVariable(value) }
        override fun setFrenchCollation(value: Boolean) { c.setFrenchCollation(value) }
        override fun setCaseLevel(value: Boolean) { c.setCaseLevel(value) }
        override fun setLowerCaseFirst(value: Boolean) { c.setLowerCaseFirst(value) }
        override fun setUpperCaseFirst(value: Boolean) { c.setUpperCaseFirst(value) }
        override fun setAlternateHandlingShifted(value: Boolean) { c.setAlternateHandlingShifted(value) }
        override fun setNumericCollation(value: Boolean) { c.setNumericCollation(value) }
    }
    @Test fun keywordsCallOrderPartialMutationsAndNonRbcRejections() {
        val options=listOf("colStrength" to listOf("primary","secondary","TERTIARY","quaternary","identical","quarternary","4",""),
            "colBackwards" to listOf("yes","NO","true","maybe"),"colCaseLevel" to listOf("yes","no","maybe"),
            "colCaseFirst" to listOf("no","lower","UPPER","maybe"),"colAlternate" to listOf("non-ignorable","shifted","maybe"),
            "colNormalization" to listOf("yes","no","maybe"),"colNumeric" to listOf("yes","no","maybe"),
            "colReorder" to listOf("Hani-Latn","space-punct-Latn-symbol-currency-digit","Zzzz-Latn","others","Latin","Abcd","Latn-","-Latn","Latn--Hani","Latn-Latn",List(219) { "Latn" }.joinToString("-")),
            "kv" to listOf("space","punct","symbol","currency","digit","others"),"colHiraganaQuaternary" to listOf("yes","no"),"variableTop" to listOf("x"),"unknown" to listOf("anything"))
        val cases=options.flatMap { (key,values) -> values.map { "en@$key=$it" } }.toMutableList()
        val random=Random(78191)
        repeat(180) { cases.add("en@"+options.shuffled(random).take(random.nextInt(2,8)).joinToString(";") { (key,values) -> "$key=${values.random(random)}" }) }
        cases.add("en-u-ks-level1-kc-true-kf-upper-kn-true-kk-true-kr-hani-latn-kv-currency")
        for(locale in cases) for(rbc in listOf(false,true)) {
            val a=SourceRecording(); val f=ConfiguredCollatorFixture(CollationRoot.getRoot()); val b=TargetRecording(f.config)
            assertEquals(outcome { apply.invoke(null,SourceLocale(locale),a,if(rbc) a.delegate else null); Unit },outcome { CollationLocaleOptions.apply(ULocale(locale),b,if(rbc) b else null) },locale)
            assertEquals(a.calls,b.calls,"calls/$locale/$rbc")
            for(text in listOf("aAé","123٤٥","一😀 $.")) assertContentEquals(a.getCollationKey(text)!!.toByteArray(),f.core.getCollationKey(text)!!.toByteArray(),"key/$locale/$rbc")
        }
    }
    @Test fun sourceAsciiAndParsingHelpers() {
        val ascii=Source::class.java.declaredClasses.single { it.simpleName=="ASCII" }.getDeclaredMethod("equalIgnoreCase",CharSequence::class.java,CharSequence::class.java).apply { isAccessible=true }
        val yes=Source::class.java.getDeclaredMethod("getYesOrNo",String::class.java,String::class.java).apply { isAccessible=true }
        val integer=Source::class.java.getDeclaredMethod("getIntValue",String::class.java,String::class.java,Array<String>::class.java).apply { isAccessible=true }
        val reorder=Source::class.java.getDeclaredMethod("getReorderCode",String::class.java,String::class.java).apply { isAccessible=true }
        val values=listOf<String?>(null,"","yes","YES","no","No","space","PUNCT","symbol","currency","digit","others","Latn","İ","Σ")
        for(s in values) {
            assertEquals(outcome { yes.invoke(null,"test",s) },outcome { CollationLocaleOptions.getYesOrNo("test",s) })
            assertEquals(outcome { reorder.invoke(null,"test",s) },outcome { CollationLocaleOptions.getReorderCode("test",s) })
            val choices=arrayOf("yes","no","space")
            assertEquals(outcome { integer.invoke(null,"test",s,choices) },outcome { CollationLocaleOptions.getIntValue("test",s,*choices) })
            for(t in values) assertEquals(outcome { ascii.invoke(null,s,t) },outcome { CollationLocaleOptions.equalIgnoreCase(s,t) })
        }
        val random=Random(78481)
        repeat(5000) {
            val a=CharArray(random.nextInt(0,8)) { random.nextInt(65536).toChar() }.concatToString()
            val b=if(random.nextBoolean()) a.uppercase() else CharArray(random.nextInt(0,8)) { random.nextInt(65536).toChar() }.concatToString()
            assertEquals(ascii.invoke(null,a,b),CollationLocaleOptions.equalIgnoreCase(a,b))
        }
    }
}

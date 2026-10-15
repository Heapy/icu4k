// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.Collator as Source
import com.ibm.icu.text.RuleBasedCollator as SourceRbc
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.CloneNotSupportedException
import kotlin.test.*

class CollatorServiceOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun compare(a: Source,b: Collator) {
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE)?.name,b.getLocale(ULocale.VALID_LOCALE)?.getName())
        assertEquals(a.getLocale(SourceLocale.ACTUAL_LOCALE)?.name,b.getLocale(ULocale.ACTUAL_LOCALE)?.getName())
        for(text in listOf("abc","äæé","ch😀一","002٤٥","A-.$")) assertContentEquals(a.getCollationKey(text).toByteArray(),b.getCollationKey(text)!!.toByteArray())
        assertEquals(a.strength,b.getStrength()); assertEquals(a.decomposition,b.getDecomposition())
    }
    @Test fun localeFactoriesKeywordValuesAndFunctionalEquivalence() {
        for(name in listOf("root","en_US","de_DE","de@collation=phonebook","es@collation=traditional","ja","zh@collation=pinyin","fr_CA","zz_ZZ","en@colStrength=primary;colNumeric=yes","en-u-kf-upper-kn-true")) {
            compare(Source.getInstance(SourceLocale(name)),Collator.getInstance(ULocale(name)))
            for(key in listOf<String?>("collation","bad",null)) for(used in listOf(false,true)) {
                assertContentEquals(Source.getKeywordValuesForLocale(key,SourceLocale(name),used),Collator.getKeywordValuesForLocale(key,ULocale(name),used))
            }
            for(key in listOf<String?>("collation","bad",null)) {
                val sa=booleanArrayOf(false); val ta=booleanArrayOf(false)
                assertEquals(outcome { Source.getFunctionalEquivalent(key,SourceLocale(name),sa).name },outcome { Collator.getFunctionalEquivalent(key,ULocale(name),ta).getName() },"$name/$key")
                assertContentEquals(sa,ta)
            }
            assertEquals(Source.getDisplayName(SourceLocale(name),SourceLocale.ENGLISH),Collator.getDisplayName(ULocale(name),ULocale.ENGLISH))
        }
        assertContentEquals(Source.getAvailableULocales().map { it.name }.sorted().toTypedArray(),Collator.getAvailableULocales().map { it.getName() }.sorted().toTypedArray())
        assertContentEquals(Source.getAvailableLocales().map { it.toString() }.sorted().toTypedArray(),Collator.getAvailableLocales().map { it.toString() }.sorted().toTypedArray())
        for(key in listOf<String?>("collation","bad",null)) assertEquals(outcome { Source.getKeywordValues(key).toList() },outcome { Collator.getKeywordValues(key).toList() })
        compare(Source.getInstance(java.util.Locale.GERMANY),Collator.getInstance(java.util.Locale.GERMANY))
        compare(Source.getInstance(null as SourceLocale?),Collator.getInstance(null as ULocale?))
    }
    @Test fun registrationFallbackFactoriesAndFrozenPrototype() {
        val sa=SourceLocale("qaa"); val ta=ULocale("qaa")
        val a=SourceRbc("&a<z"); val b=RuleBasedCollator("&a<z")
        val sk=Source.registerInstance(a,sa); val tk=Collator.registerInstance(b,ta)
        try {
            for(name in listOf("qaa","qaa_BB","qaa@colNumeric=yes")) compare(Source.getInstance(SourceLocale(name)),Collator.getInstance(ULocale(name)))
            assertEquals(Source.getDisplayName(sa,SourceLocale.ENGLISH),Collator.getDisplayName(ta,ULocale.ENGLISH))
            assertEquals(Source.getAvailableULocales().any { it.name=="qaa" },Collator.getAvailableULocales().any { it.getName()=="qaa" })
        } finally { assertEquals(Source.unregister(sk),Collator.unregister(tk)) }
        assertEquals(Source.unregister(sk),Collator.unregister(tk))
        for(visible in listOf(true,false)) {
            val sourceCalls=mutableListOf<String?>(); val targetCalls=mutableListOf<String?>()
            val sf=object : Source.CollatorFactory() {
                override fun visible()=visible
                override fun getSupportedLocaleIDs()=setOf("qaa")
                override fun createCollator(loc: SourceLocale?): Source? { sourceCalls.add(loc?.name); return SourceRbc("&a<z") }
                override fun getDisplayName(obj: SourceLocale?, display: SourceLocale?): String? = "custom:$obj/$display"
            }
            val tf=object : Collator.CollatorFactory() {
                override fun visible()=visible
                override fun getSupportedLocaleIDs()=setOf("qaa")
                override fun createCollator(loc: ULocale?): Collator? { targetCalls.add(loc?.getName()); return RuleBasedCollator("&a<z") }
                override fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String? = "custom:$objectLocale/$displayLocale"
            }
            val x=Source.registerFactory(sf); val y=Collator.registerFactory(tf)
            try {
                compare(Source.getInstance(SourceLocale("qaa_BB@colStrength=primary")),Collator.getInstance(ULocale("qaa_BB@colStrength=primary")))
                assertEquals(sourceCalls,targetCalls)
                assertEquals(Source.getDisplayName(sa,SourceLocale.ENGLISH),Collator.getDisplayName(ta,ULocale.ENGLISH))
                assertEquals(Source.getAvailableULocales().any { it.name=="qaa" },Collator.getAvailableULocales().any { it.getName()=="qaa" })
            } finally { assertEquals(Source.unregister(x),Collator.unregister(y)) }
        }
        a.freeze(); b.freeze()
        val x=Source.registerInstance(a,sa); val y=Collator.registerInstance(b,ta)
        try {
            assertSame(a,Source.getInstance(sa)); assertSame(b,Collator.getInstance(ta))
            assertEquals(outcome { Source.getInstance(SourceLocale("qaa@colStrength=primary")) },outcome { Collator.getInstance(ULocale("qaa@colStrength=primary")) })
        } finally { Source.unregister(x); Collator.unregister(y) }
        assertEquals(outcome { Source.unregister(Any()) },outcome { Collator.unregister(Any()) })
        assertEquals(outcome { Source.unregister(null) },outcome { Collator.unregister(null) })
    }
    private class SourceCustom(val fail: Boolean) : Source() {
        private val delegate=SourceRbc("")
        override fun clone(): Source { if(fail) throw java.lang.CloneNotSupportedException("disabled"); return super.clone() }
        override fun compare(a: String?,b: String?)=delegate.compare(a,b)
        override fun getCollationKey(s: String?)=delegate.getCollationKey(s)
        override fun getRawCollationKey(s: String?,k: com.ibm.icu.text.RawCollationKey?)=delegate.getRawCollationKey(s,k)
        override fun setVariableTop(s: String?)=delegate.setVariableTop(s)
        override fun setVariableTop(v: Int)=delegate.setVariableTop(v)
        override fun getVariableTop()=delegate.variableTop
        override fun getVersion()=delegate.version
        override fun getUCAVersion()=delegate.ucaVersion
    }
    private class TargetCustom(val fail: Boolean) : Collator() {
        private val delegate=RuleBasedCollator("")
        override fun clone(): Collator { if(fail) throw CloneNotSupportedException("disabled"); return super.clone() }
        override fun compare(source: String?,target: String?)=delegate.compare(source,target)
        override fun getCollationKey(source: String?)=delegate.getCollationKey(source)
        override fun getRawCollationKey(source: String?,key: RawCollationKey?)=delegate.getRawCollationKey(source,key)
        override fun setVariableTop(varTop: String?)=delegate.setVariableTop(varTop)
        override fun setVariableTop(varTop: Int)=delegate.setVariableTop(varTop)
        override fun getVariableTop()=delegate.getVariableTop()
        override fun getVersion()=delegate.getVersion()
        override fun getUCAVersion()=delegate.getUCAVersion()
    }
    @Test fun nonRuleBasedRegistrationAndCloneFailureWrapping() {
        for(fail in listOf(false,true)) {
            val x=Source.registerInstance(SourceCustom(fail),SourceLocale("qab"))
            val y=Collator.registerInstance(TargetCustom(fail),ULocale("qab"))
            try {
                for(name in listOf("qab","qab@colStrength=primary","qab@colNumeric=yes","qab@colCaseFirst=invalid")) {
                    assertEquals(outcome { val a=Source.getInstance(SourceLocale(name)); a.getCollationKey("aAé").toByteArray().toList() },
                        outcome { val b=Collator.getInstance(ULocale(name)); b.getCollationKey("aAé")!!.toByteArray().toList() })
                }
                if(fail) {
                    val source=assertFailsWith<com.ibm.icu.util.ICUCloneNotSupportedException> { Source.getInstance(SourceLocale("qab")) }
                    val target=assertFailsWith<io.github.heapy.icu4k.util.ICUCloneNotSupportedException> { Collator.getInstance(ULocale("qab")) }
                    assertEquals(source.cause!!.javaClass,target.cause!!.javaClass)
                    assertEquals(source.cause!!.message,target.cause!!.message)
                }
            } finally { Source.unregister(x); Collator.unregister(y) }
        }
        val previous=java.util.Locale.getDefault()
        val display=java.util.Locale.getDefault(java.util.Locale.Category.DISPLAY)
        val format=java.util.Locale.getDefault(java.util.Locale.Category.FORMAT)
        try {
            java.util.Locale.setDefault(java.util.Locale.JAPAN)
            compare(Source.getInstance(SourceLocale("zz_ZZ")),Collator.getInstance(ULocale("zz_ZZ")))
            assertEquals(Collator.getInstance(ULocale.ROOT),Collator.getInstance(ULocale("zz_ZZ")))
        } finally {
            java.util.Locale.setDefault(previous)
            java.util.Locale.setDefault(java.util.Locale.Category.DISPLAY,display)
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT,format)
        }
    }
    @Test fun legacyLocaleFactoryOverride() {
        val sourceCalls=mutableListOf<java.util.Locale?>(); val targetCalls=mutableListOf<java.util.Locale?>()
        val sf=object : Source.CollatorFactory() {
            override fun getSupportedLocaleIDs()=setOf("qaa")
            override fun createCollator(loc: java.util.Locale?): Source? { sourceCalls.add(loc); return SourceRbc("&a<z") }
        }
        val tf=object : Collator.CollatorFactory() {
            override fun getSupportedLocaleIDs()=setOf("qaa")
            override fun createCollator(loc: java.util.Locale?): Collator? { targetCalls.add(loc); return RuleBasedCollator("&a<z") }
        }
        val x=Source.registerFactory(sf); val y=Collator.registerFactory(tf)
        try {
            compare(Source.getInstance(SourceLocale("qaa_BB")),Collator.getInstance(ULocale("qaa_BB")))
            assertEquals(sourceCalls,targetCalls)
            assertEquals(sf.getDisplayName(SourceLocale("qaa"),SourceLocale.ENGLISH),tf.getDisplayName(ULocale("qaa"),ULocale.ENGLISH))
        } finally { Source.unregister(x); Collator.unregister(y) }
    }
}

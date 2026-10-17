package io.github.heapy.icu4k.impl

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatDefaultPatternCoreOracleTest {
    private val source=Source::class.java.getDeclaredMethod("getDefaultPattern").also { it.isAccessible=true }
    private fun outcome(block:()->Any?):Any?=try { block() } catch(e:Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        if(t is VirtualMachineError) throw t
        when(t) { is NullPointerException->"null"; is IndexOutOfBoundsException->"bounds"; else->t.javaClass.simpleName+":"+t.message }
    }
    @Test fun everyLocaleAndRepeatedFailuresMatchPrivateSourceDefaultFactory() {
        val saved=SourceLocale.getDefault(SourceLocale.Category.FORMAT)
        val localeField=Source::class.java.getDeclaredField("cachedDefaultLocale").also { it.isAccessible=true }
        val patternField=Source::class.java.getDeclaredField("cachedDefaultPattern").also { it.isAccessible=true }
        val savedLocale=localeField.get(null); val savedPattern=patternField.get(null)
        val core=SimpleDateFormatDefaultPatternCore()
        try {
            localeField.set(null,null); patternField.set(null,null)
            val ids=(SourceLocale.getAvailableLocales().map { it.toString() }+listOf("en_US@hours=h23","en_US@rg=gbzzzz","he_IL@calendar=hebrew","zh@calendar=chinese","ko@calendar=dangi","en@calendar=not-a-calendar","zz_ZZ")).distinct()
            for(id in ids) {
                SourceLocale.setDefault(SourceLocale.Category.FORMAT,SourceLocale(id))
                repeat(2) { assertEquals(outcome { source.invoke(null) },outcome { core.getPattern(ULocale(id)) },id) }
            }
        } finally {
            SourceLocale.setDefault(SourceLocale.Category.FORMAT,saved)
            localeField.set(null,savedLocale); patternField.set(null,savedPattern)
        }
    }
}

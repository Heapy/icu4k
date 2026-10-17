package io.github.heapy.icu4k.util

import com.ibm.icu.util.GenderInfo as Source
import kotlin.test.*

class GenderInfoOracleTest {
    private fun result(block:()->Any?): String = try {block()?.toString()?:"null"}catch(e:Throwable){if(e is NullPointerException)"NullPointerException" else "${e.javaClass.simpleName}:${e.message}"}
    private fun cases(): List<List<Int?>> = buildList {
        add(emptyList())
        fun visit(prefix: List<Int?>,left: Int) {if(left==0){add(prefix);return};for(v in listOf(null,0,1,2))visit(prefix+v,left-1)}
        for(size in 1..5)visit(emptyList(),size)
    }
    private fun compare(source: Source,port: GenderInfo,cases: List<List<Int?>>) {
        for(input in cases) {
            val s=input.map{it?.let{Source.Gender.values()[it]}};val p=input.map{it?.let{GenderInfo.Gender.entries[it]}}
            assertEquals(result{source.getListGender(s)},result{port.getListGender(p)},input.toString())
            assertEquals(result{source.getListGender(*s.toTypedArray())},result{port.getListGender(*p.toTypedArray())},input.toString())
        }
    }
    @Test fun exhaustiveStylesAndNullElements() {
        val cases=cases()
        for(style in listOf(null,0,1,2))compare(Source(style?.let{Source.ListGenderStyle.values()[it]}),GenderInfo(style?.let{GenderInfo.ListGenderStyle.entries[it]}),cases)
        for(name in listOf(null,"neutral","maleTaints","mixedNeutral","","NEUTRAL","unknown"))assertEquals(result{Source.ListGenderStyle.fromName(name)},result{GenderInfo.ListGenderStyle.fromName(name)})
    }
    @Test fun everyLocaleAndFallback() {
        val cases=cases().filter{it.size<=3}
        for(id in (com.ibm.icu.util.ULocale.getAvailableLocales().map{it.toString()}+listOf("", "root","zz_ZZ","fr_CA_POSIX","el_GR@currency=EUR","iw_IL","en@foo=bar")).distinct()) {
            compare(Source.getInstance(com.ibm.icu.util.ULocale(id)),GenderInfo.getInstance(ULocale(id)),cases)
            assertEquals(result{Source.getInstance(java.util.Locale.forLanguageTag(id.replace('_','-'))).getListGender(Source.Gender.FEMALE,Source.Gender.OTHER)},result{GenderInfo.getInstance(java.util.Locale.forLanguageTag(id.replace('_','-'))).getListGender(GenderInfo.Gender.FEMALE,GenderInfo.Gender.OTHER)},id)
        }
        assertSame(GenderInfo.getInstance(ULocale("fr")),GenderInfo.getInstance(ULocale("fr_CA_POSIX")))
        assertSame(GenderInfo.getInstance(ULocale("zz")),GenderInfo.getInstance(ULocale("zz_ZZ")))
    }
}

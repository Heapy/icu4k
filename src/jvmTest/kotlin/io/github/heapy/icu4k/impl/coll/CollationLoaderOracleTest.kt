// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.CollationLoader as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CollationLoaderOracleTest {
    @Test fun everyAvailableLocaleTypesFallbackAndInformationalLocales() {
        val index=com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_COLLATION_BASE_NAME,"res_index")
        val available=index.get("InstalledLocales").keySet()
        val locales=available.sorted()+listOf("root","","zz_ZZ","zh_Hant_TW","sr_Latn_BA","de_DE_EURO")
        for(locale in locales) for(type in listOf("","standard","default","search","searchjl","searchzz","PHONEBOOK","traditional","stroke","bogus")) {
            val name=locale+if(type.isEmpty()) "" else "@collation=$type"
            val outA=com.ibm.icu.util.Output<SourceLocale>();val outB=Output<ULocale>()
            val a=Source.loadTailoring(SourceLocale(name),outA);val b=CollationLoader.loadTailoring(ULocale(name),outB)
            assertEquals(outA.value.name,outB.value!!.getName(),"valid/$name")
            assertEquals(a.actualLocale.name,b.actualLocale!!.getName(),"actual/$name")
            assertEquals(a.version,b.version,"version/$name");assertEquals(a.rules,b.getRules(),"rules/$name")
            assertEquals(a===com.ibm.icu.impl.coll.CollationRoot.getRoot(),b===CollationRoot.getRoot(),"root/$name")
            val settings=a.settings.readOnly();val target=b.settings!!.readOnly()!!
            assertEquals(settings.options,target.options);assertEquals(settings.variableTop,target.variableTop)
            assertEquals(settings.fastLatinOptions,target.fastLatinOptions);assertContentEquals(settings.fastLatinPrimaries,target.fastLatinPrimaries)
            val d=CollationDataOracleTest().importSource(a.data);val td=b.data!!
            assertContentEquals(d.ces,td.ces);assertContentEquals(d.ce32s,td.ce32s);assertContentEquals(d.jamoCE32s,td.jamoCE32s)
            assertEquals(d.contexts,td.contexts);assertEquals(d.unsafeBackwardSet,td.unsafeBackwardSet)
            for(c in listOf(0,48,65,0xe4,0x301,0x660,0x4e00,0x1f600,0x10ffff)) assertEquals(d.getCE32(c),td.getCE32(c),"CE32/$name/$c")
            // The source relies on finalization; clear these test-local holders explicitly.
            if(b!==CollationRoot.getRoot()) b.settings!!.clear()
        }
    }
    @Test fun rootRulesAndExplicitRulesLookup() {
        assertEquals(Source.getRootRules(),CollationLoader.getRootRules())
        val method=Source::class.java.getDeclaredMethod("loadRules",SourceLocale::class.java,String::class.java).apply { isAccessible=true }
        for((locale,type) in listOf("de" to "phonebook","es" to "traditional","zh" to "stroke","ko" to "searchjl")) {
            assertEquals(method.invoke(null,SourceLocale(locale),type),CollationLoader.loadRules(ULocale(locale),type))
            assertEquals(method.invoke(null,SourceLocale(locale),type.uppercase()),CollationLoader.loadRules(ULocale(locale),type.uppercase()))
        }
    }
}

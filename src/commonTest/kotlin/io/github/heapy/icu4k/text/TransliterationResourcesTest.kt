// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.UResourceBundle

class TransliterationResourcesTest {
    private fun bundle(name:String)=UResourceBundle.getBundleInstance(ICUData.ICU_TRANSLIT_BASE_NAME,name,disableFallback=true)
    private fun fingerprint(bundle:UResourceBundle):Int {
        var hash=0x811c9dc5.toInt()
        fun add(v:Int){hash=(hash xor v)*16777619}
        fun string(s:String?){if(s==null){add(-1);return};add(s.length);for(c in s)add(c.code)}
        fun visit(b:UResourceBundle) {
            val type=b.getType();add(type);string(b.getKey());add(b.getSize())
            when(type) {
                0->string(b.getString())
                1->for(v in b.getBinary(null))add(v.toInt() and 255)
                7->add(b.getInt())
                14->for(v in b.getIntVector())add(v)
                2,8->for(i in 0 until b.getSize())visit(b[i])
                else->error("Unexpected resource type $type")
            }
        }
        visit(bundle)
        return hash
    }
    // ICU4J 78.3: complete resource trees, including every rule string and ID row.
    @Test fun completeRootBundle(){val b=bundle("root");assertEquals(6,b.getSize());assertEquals(1007,b["RuleBasedTransliteratorIDs"].getSize());assertEquals(193550730,fingerprint(b))}
    @Test fun completeEnglishBundle(){val b=bundle("en");assertEquals(4,b.getSize());assertEquals(944385659,fingerprint(b))}
    @Test fun completeGreekBundle(){val b=bundle("el");assertEquals(1,b.getSize());assertEquals(-1230662304,fingerprint(b))}
    @Test fun catalogAndCopyIsolation() {
        val names=mutableSetOf<String>()
        ICUData.visitResources(ICUData.ICU_TRANSLIT_BASE_NAME+"/"){names.add(it)}
        assertEquals(setOf("root.res","en.res","el.res"),names)
        for(name in names) {
            val path=ICUData.ICU_TRANSLIT_BASE_NAME+"/"+name
            assertTrue(ICUData.exists(path))
            val a=ICUData.getBytes(path)!!;val b=ICUData.getBytes("translit/$name")!!
            assertContentEquals(a,b);a[0]=(a[0].toInt() xor 255).toByte()
            assertNotEquals(a[0],b[0]);assertContentEquals(b,ICUData.getBytes(path))
        }
    }
    @Test fun sourceBootstrapRowsAndLazyRuleSuppliers() {
        val root=bundle("root");var rootCalls=0
        data class Row(val id:String,val type:String,val rules:(()->String)?,val alias:String?,val direction:Int,val visible:Boolean)
        val rows=mutableListOf<Row>()
        TransliterationResourceBootstrap{++rootCalls;root}.register(object:TransliterationResourceBootstrap.Registry {
            override fun putResource(id:String,rules:()->String,direction:Int,visible:Boolean){rows.add(Row(id,if(visible)"file"else"internal",rules,null,direction,visible))}
            override fun putAlias(id:String,alias:String,visible:Boolean){rows.add(Row(id,"alias",null,alias,0,visible))}
        })
        assertEquals(1,rootCalls)
        assertEquals(620,rows.size)
        assertEquals(387,rows.count{it.type=="file"});assertEquals(32,rows.count{it.type=="internal"});assertEquals(201,rows.count{it.type=="alias"})
        assertTrue(rows.none{it.id.contains("-t-")})
        var hash=0x811c9dc5.toInt()
        fun add(v:Int){hash=(hash xor v)*16777619}
        fun string(s:String){add(s.length);for(c in s)add(c.code)}
        for(row in rows) {
            string(row.id);string(row.type)
            if(row.type=="alias"){string(row.alias!!);add(if(row.visible)1 else 0)}
            else {add(row.direction);add(if(row.visible)1 else 0);string(row.rules!!())}
        }
        assertEquals(-1360892326,hash)
        assertEquals(420,rootCalls)
    }
}

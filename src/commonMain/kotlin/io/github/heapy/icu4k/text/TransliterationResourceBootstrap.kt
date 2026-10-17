// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.UResourceBundle

/** Source static registry rows; built-in registrations are added by the future facade owner. */
internal class TransliterationResourceBootstrap(private val root: () -> UResourceBundle = {
    UResourceBundle.getBundleInstance(ICUData.ICU_TRANSLIT_BASE_NAME,"root")
}) {
    interface Registry {
        fun putResource(id:String,rules:()->String,direction:Int,visible:Boolean)
        fun putAlias(id:String,alias:String,visible:Boolean)
    }
    fun register(registry: Registry) {
        val ids=root()["RuleBasedTransliteratorIDs"]
        for(row in 0 until ids.getSize()) {
            val entry=ids[row]
            val id=entry.getKey()!!
            if(id.indexOf("-t-")>=0)continue
            val resource=entry[0]
            val type=resource.getKey()
            when(type) {
                "file","internal" -> {
                    val direction=resource.getString("direction")
                    val dir=when(transliterationCharAt(direction,0)){'F'->0;'R'->1;else->throw RuntimeException("Can't parse direction: $direction")}
                    registry.putResource(id,{root()["RuleBasedTransliteratorIDs"][row][0].getString("resource")},dir,type!="internal")
                }
                "alias" -> registry.putAlias(id,resource.getString(),true)
                else -> throw RuntimeException("Unknown type: $type")
            }
        }
    }
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.Region
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** DateTimePatternGenerator's preferred and allowed hours, read from ICU timeData. */
internal class DateTimePatternHourDataCore(loader: ICUResourceLoader=defaultICUResourceLoader()) {
    class Hours(val preferred: Char,val allowed: Array<String?>)
    private val data=HashMap<String,Array<String?>>()
    init {
        val bundle=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"supplementalData",loader) as ICUResourceBundle
        bundle.getAllItemsWithFallback("timeData",object: UResource.Sink() {
            override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
                val table=value.getTable(); var i=0
                while(table.getKeyAndValue(i++,key,value)) {
                    val id=key.toString(); val formats=value.getTable(); var j=0
                    var allowed: Array<String>?=null; var preferred: String?=null
                    while(formats.getKeyAndValue(j++,key,value)) {
                        if(key.contentEquals("allowed")) allowed=value.getStringArrayOrStringAsArray()
                        else if(key.contentEquals("preferred")) preferred=value.getString()
                    }
                    val items=allowed
                    data[id]=if(items!=null && items.isNotEmpty()) arrayOfNulls<String>(items.size+1).also {
                        it[0]=preferred?:items[0]; items.copyInto(it,1)
                    } else arrayOf(preferred?:"H",preferred?:"H")
                }
            }
        })
    }
    private fun lookup(language: String,country: String): Array<String?>?=data[language+"_"+country]?:data[country]
    fun get(locale: ULocale?): Hours {
        val loc=locale!!
        var language=loc.getLanguage(); var country=ULocale.getRegionForSupplementalData(loc,false)
        if(language.isEmpty() || country.isEmpty()) {
            val max=ULocale.addLikelySubtags(loc); language=max.getLanguage(); country=max.getCountry()
        }
        if(language.isEmpty()) language="und"
        if(country.isEmpty()) country="001"
        var list=lookup(language,country)
        val override=when(loc.getKeywordValue("hours")) { "h24" -> 'k'; "h23" -> 'H'; "h12" -> 'h'; "h11" -> 'K'; else -> null }
        if(list==null) {
            try { country=Region.getInstance(country).toString(); list=lookup(language,country) }
            catch(_: IllegalArgumentException) { }
        }
        val formats=list
        if(formats!=null) return Hours(override?:formats[0]!![0],if(formats.size>1) formats.copyOfRange(1,formats.size) else LAST_RESORT)
        return Hours(override?:LAST_RESORT[0]!![0],LAST_RESORT)
    }
    companion object { private val LAST_RESORT=arrayOf<String?>("H") }
}

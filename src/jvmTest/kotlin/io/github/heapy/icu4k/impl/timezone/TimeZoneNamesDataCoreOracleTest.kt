@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.TimeZoneNamesImpl as Source
import com.ibm.icu.text.TimeZoneNames.NameType as SType
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.*
import kotlin.random.Random
import kotlin.test.*

class TimeZoneNamesDataCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t = if(e is java.lang.reflect.InvocationTargetException) e.cause!! else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private fun defaultZone(): String? = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(com.ibm.icu.util.TimeZone.getDefault())
    private fun pair(id: String): Pair<Source,TimeZoneNamesDataCore> = Source(com.ibm.icu.util.ULocale(id)) to TimeZoneNamesDataCore(ULocale(id),::defaultZone)
    private fun type(t: SType?): TimeZoneNamesDataCore.NameType? = t?.let { TimeZoneNamesDataCore.NameType.valueOf(it.name) }
    private val zoneIDs = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64").get("Names").getStringArray()
    @Test fun everyLocaleAndRepresentativeNamesMatchIncludingBulkFallback() {
        val index = UResourceBundle.getBundleInstance(ICUData.ICU_ZONE_BASE_NAME,"res_index").get("InstalledLocales").keySet()
        val sourceIndex = com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_ZONE_BASE_NAME,"res_index").get("InstalledLocales").keySet()
        assertEquals(sourceIndex,index)
        val files = mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_ZONE_BASE_NAME+"/") { files.add(it) }
        val locales = files.filter { it != "pool.res" && it != "res_index.res" }.map { it.removeSuffix(".res") }
        assertEquals(543,locales.size)
        val ids = listOf("America/Los_Angeles","America/New_York","Europe/Minsk","Europe/London","Asia/Kolkata","Asia/Tokyo","Etc/UTC","Unknown/City",null,"")
        val metas = listOf("America_Pacific","America_Eastern","Europe_Central","GMT","Japan","India","Unknown",null,"")
        for(locale in locales) {
            val (a,b) = pair(locale)
            for(id in ids) for(t in SType.entries+listOf(null)) {
                assertEquals(outcome { a.getTimeZoneDisplayName(id,t) },outcome { b.getTimeZoneDisplayName(id,type(t)) },"$locale/$id/$t")
                assertEquals(outcome { a.getDisplayName(id,t,0) },outcome { b.getDisplayName(id,type(t),0) },"display/$locale/$id/$t")
            }
            for(id in metas) for(t in SType.entries+listOf(null)) assertEquals(outcome { a.getMetaZoneDisplayName(id,t) },outcome { b.getMetaZoneDisplayName(id,type(t)) },"meta/$locale/$id/$t")
            assertEquals(outcome { a.loadAllDisplayNames(); Unit },outcome { b.loadAllDisplayNames(); Unit },"bulk/$locale")
            for(id in ids) for(t in SType.entries) assertEquals(a.getDisplayName(id,t,0),b.getDisplayName(id,type(t),0),"bulk/$locale/$id/$t")
        }
    }
    @Test fun everyZoneAndMetazoneNameMatchesInRepresentativeLocales() {
        val source = Source(com.ibm.icu.util.ULocale.ENGLISH)
        for(locale in listOf("root","en_US","en_GB","fr_CA","ru","ar","zh_Hant","sr_Cyrl","pt_PT","es_MX","en_US@calendar=hebrew","unknown")) {
            val (a,b) = pair(locale)
            for(id in zoneIDs) {
                assertEquals(a.getExemplarLocationName(id),b.getExemplarLocationName(id),"location/$locale/$id")
                for(t in SType.entries) {
                    assertEquals(a.getTimeZoneDisplayName(id,t),b.getTimeZoneDisplayName(id,type(t)),"zone/$locale/$id/$t")
                    for(date in listOf(-1L,0L,1_000_000_000_000L,Long.MAX_VALUE)) assertEquals(a.getDisplayName(id,t,date),b.getDisplayName(id,type(t),date),"display/$locale/$id/$t/$date")
                }
            }
            for(id in source.availableMetaZoneIDs) for(t in SType.entries) assertEquals(a.getMetaZoneDisplayName(id,t),b.getMetaZoneDisplayName(id,type(t)),"meta/$locale/$id/$t")
        }
    }
    @Test fun mappingIntervalsReferenceZonesAndDateParserMatchOriginal() {
        val a = Source(com.ibm.icu.util.ULocale.ENGLISH)
        assertEquals(a.availableMetaZoneIDs,TimeZoneNamesMappings.getAvailableMetaZoneIDs())
        val info = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"metaZones").get("metazoneInfo")
        val dates = mutableSetOf(Long.MIN_VALUE,Long.MAX_VALUE,-1L,0L,1L)
        for(key in info.keySet()) {
            val resource = info.get(key)
            for(i in 0 until resource.getSize()) {
                val entry = resource.get(i)
                if(entry.getSize() == 3) for(j in 1..2) {
                    val t = TimeZoneNamesMappings.parseDate(entry.getString(j)); dates.add(t-1); dates.add(t); dates.add(t+1)
                }
            }
        }
        for(id in zoneIDs.toList()+listOf(null,"","Unknown/City","America:Los_Angeles")) {
            assertEquals(a.getAvailableMetaZoneIDs(id),TimeZoneNamesMappings.getAvailableMetaZoneIDs(id),id)
            for(date in dates) assertEquals(a.getMetaZoneID(id,date),TimeZoneNamesMappings.getMetaZoneID(id,date),"$id/$date")
        }
        for(id in a.availableMetaZoneIDs+listOf(null,"","Unknown")) for(region in listOf(null,"001","US","CA","RU","BY","CN","ZZ","us","")) {
            val expected = a.getReferenceZoneID(id,region); val actual = TimeZoneNamesMappings.getReferenceZoneID(id,region)
            assertEquals(expected,actual,"$id/$region")
            if(expected != null) assertSame(expected.intern(),actual)
        }
        val cls = Class.forName("com.ibm.icu.impl.TimeZoneNamesImpl\$TZ2MZsCache")
        val parser = cls.getDeclaredMethod("parseDate",String::class.java).also { it.isAccessible = true }
        val random = Random(7881)
        val texts = mutableListOf<String?>(null,"","1970","1970x01x01x00x00ignored","1970-00-00 99:99","2000-00-00 99:99","٢٠٢٥-01-01 00:00")
        repeat(600) { texts.add("%04d-%02d-%02d %02d:%02d".format(random.nextInt(10_000),random.nextInt(100),random.nextInt(100),random.nextInt(100),random.nextInt(100))) }
        repeat(100) { val chars = "2025-01-02 03:04".toCharArray(); chars[random.nextInt(chars.size)] = 'x'; texts.add(chars.concatToString()) }
        for(text in texts) assertEquals(outcome { parser.invoke(null,text) },outcome { TimeZoneNamesMappings.parseDate(text) },text)
    }
    @Test fun batchesMatchPartialWritesAndArgumentValidation() {
        val (a,b) = pair("en")
        val variants = listOf<Array<SType?>?>(null,emptyArray(),arrayOf(SType.LONG_STANDARD,SType.EXEMPLAR_LOCATION,SType.SHORT_STANDARD),arrayOf(SType.LONG_GENERIC,null,SType.LONG_STANDARD))
        for(id in listOf(null,"","America/Los_Angeles","Unknown/City")) for(types in variants) for(size in -1..4) for(offset in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) {
            val x = if(size < 0) null else Array<String?>(size) { "unchanged" }; val y = x?.copyOf()
            assertEquals(outcome { a.getDisplayNames(id,types,0,x,offset); Unit },outcome { b.getDisplayNames(id,types?.map(::type)?.toTypedArray(),0,y,offset); Unit },"$id/$size/$offset")
            assertContentEquals(x,y)
        }
    }
    @Test fun defaultLocationAlgorithmMatchesJavaRegexAndUnicodeBoundaries() {
        val random = Random(7882)
        val ids = mutableListOf<String?>(null,"","/City","Area/","Etc/UTC","SystemV/EST5","Mideast/Riyadh87","Mideast/Riyadh88","Mideast/Riyadh89")
        for(c in listOf('\n','\r','\u0085','\u2028','\u2029','\u000b','\u000c')) for(prefix in listOf("Etc/","SystemV/","Area/")) ids.add(prefix+"UTC"+c)
        repeat(1000) { ids.add(buildString { repeat(random.nextInt(35)) { append("abc/_EtcSystemV_Riyadh879\n\r\u0085\u2028".random(random)) } }) }
        for(id in ids) assertEquals(Source.getDefaultExemplarLocationName(id),TimeZoneNamesDataCore.getDefaultExemplarLocationName(id),id)
    }
    @Test fun allZoneResourceBytesMatchSourceAndCopiesArePrivate() {
        val names = mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_ZONE_BASE_NAME+"/") { names.add(it) }
        assertEquals(545,names.size)
        for(name in names) {
            val path = "zone/$name"
            val expected = com.ibm.icu.impl.ICUData.getRequiredStream("/"+com.ibm.icu.impl.ICUData.ICU_BASE_NAME+"/"+path).use { it.readBytes() }
            val actual = assertNotNull(ICUData.getBytes(path))
            assertContentEquals(expected,actual,path)
            actual.fill(0)
            assertContentEquals(expected,ICUData.getBytes(path),path)
        }
    }

    @Test fun batchMetaZoneCallbackCanMutateFollowingTypesAndIsCachedOnMissingMapping() {
        for(missing in listOf(false,true)) {
            val sourceTypes: Array<SType?> = arrayOf(SType.LONG_GENERIC,SType.LONG_STANDARD,SType.EXEMPLAR_LOCATION)
            val targetTypes = sourceTypes.map(::type).toTypedArray()
            var expectedCalls = 0; var actualCalls = 0
            val source = object : Source(com.ibm.icu.util.ULocale.ENGLISH) {
                override fun getMetaZoneID(id: String?,date: Long): String? {
                    ++expectedCalls
                    sourceTypes[1] = if(missing) SType.LONG_DAYLIGHT else null
                    return if(missing) null else "America_Pacific"
                }
            }
            val target = TimeZoneNamesDataCore(ULocale.ENGLISH,::defaultZone)
            val x = arrayOf<String?>("unchanged","unchanged","unchanged"); val y = x.copyOf()
            assertEquals(outcome { source.getDisplayNames("America/Los_Angeles",sourceTypes,0,x,0); Unit },
                outcome { target.getDisplayNames("America/Los_Angeles",targetTypes,0,y,0) { _,_ ->
                    ++actualCalls
                    targetTypes[1] = if(missing) TimeZoneNamesDataCore.NameType.LONG_DAYLIGHT else null
                    if(missing) null else "America_Pacific"
                }; Unit })
            assertEquals(expectedCalls,actualCalls)
            assertContentEquals(x,y)
        }
    }

    @Test fun returnedIdSetsRetainNullableMembershipAndMutationRules() {
        val source = Source(com.ibm.icu.util.ULocale.ENGLISH)
        @Suppress("UNCHECKED_CAST")
        fun target(id: String?): MutableSet<String?> = (if(id == "all") TimeZoneNamesMappings.getAvailableMetaZoneIDs() else TimeZoneNamesMappings.getAvailableMetaZoneIDs(id)) as MutableSet<String?>
        for(id in listOf("all","America/Los_Angeles",null,"missing")) {
            val a = (if(id == "all") source.availableMetaZoneIDs else source.getAvailableMetaZoneIDs(id)) as MutableSet<String?>
            val b = target(id)
            assertEquals(outcome { a.contains(null) },outcome { b.contains(null) },id)
            assertEquals(outcome { a.remove(null) },outcome { b.remove(null) },id)
            assertEquals(outcome { a.addAll(emptyList()) },outcome { b.addAll(emptyList()) },id)
            assertEquals(outcome { a.retainAll(a.toList()) },outcome { b.retainAll(b.toList()) },id)
            assertEquals(outcome { a.clear(); Unit },outcome { b.clear(); Unit },id)
        }
    }
}

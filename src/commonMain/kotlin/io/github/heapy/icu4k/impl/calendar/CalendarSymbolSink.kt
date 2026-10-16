// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.UResourceBundle

/** DateFormatSymbols' calendar resource sink, including cross-calendar alias traversal. */
internal class CalendarSymbolSink(private val assertionsEnabled: Boolean=false): UResource.Sink() {
    val arrays=PatternOrderedMap<String,Array<String>>()
    val maps=PatternOrderedMap<String,MutableMap<String,String>>()
    val aliasPathPairs=ArrayList<String>()
    var currentCalendarType: String?=null
    var nextCalendarType: String?=null
    private var resourcesToVisit: MutableSet<String>?=null
    private var aliasRelativePath: String?=null
    private fun check(condition: Boolean) { if(assertionsEnabled && !condition) throw AssertionError() }
    fun visitAllResources() { resourcesToVisit=null }
    fun preEnumerate(type: String) { currentCalendarType=type; nextCalendarType=null; aliasPathPairs.clear() }
    override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
        check(!currentCalendarType.isNullOrEmpty())
        var next: MutableSet<String>?=null
        val table=value.getTable(); var i=0
        while(table.getKeyAndValue(i++,key,value)) {
            val path=key.toString()
            when(alias(path,value)) {
                Alias.GREGORIAN -> continue
                Alias.DIFFERENT -> { if(next==null) next=HashSet(); next.add(aliasRelativePath!!); continue }
                Alias.SAME -> { if(arrays[path]==null && maps[path]==null) { aliasPathPairs.add(aliasRelativePath!!); aliasPathPairs.add(path) }; continue }
                Alias.NONE -> {}
            }
            val visit=resourcesToVisit
            if(visit!=null && visit.isNotEmpty() && path !in visit && path!="AmPmMarkersAbbr") continue
            if(path.startsWith("AmPmMarkers")) {
                if(!path.endsWith("%variant") && arrays[path]==null) arrays[path]=value.getStringArray()
            } else if(path in TABLES) processResource(path,key,value)
        }
        do {
            var modified=false; var index=0
            while(index<aliasPathPairs.size) {
                val source=aliasPathPairs[index]; val destination=aliasPathPairs[index+1]
                val a=arrays[source]; val m=maps[source]
                if(a!=null || m!=null) {
                    if(a!=null) arrays[destination]=a else maps[destination]=m!!
                    aliasPathPairs.removeAt(index+1); aliasPathPairs.removeAt(index); modified=true
                } else index+=2
            }
        } while(modified && aliasPathPairs.isNotEmpty())
        if(next!=null) resourcesToVisit=next
    }
    private fun processResource(path: String,key: UResource.Key,value: UResource.Value) {
        val table=value.getTable(); var strings: MutableMap<String,String>?=null; var i=0
        while(table.getKeyAndValue(i,key,value)) {
            val index=i++
            if(key.endsWith("%variant")) continue
            val name=key.toString()
            if(value.getType()==UResourceBundle.STRING) {
                if(index==0) { strings=HashMap(); maps[path]=strings }
                check(strings!=null); strings!![name]=value.getString(); continue
            }
            check(strings==null)
            val current="$path/$name"
            if(current.startsWith("cyclicNameSets") && CYCLIC.none { it.startsWith(current) }) continue
            if(arrays[current]!=null || maps[current]!=null) continue
            val type=alias(current,value)
            if(type==Alias.SAME) { aliasPathPairs.add(aliasRelativePath!!); aliasPathPairs.add(current); continue }
            check(type==Alias.NONE)
            when(value.getType()) {
                UResourceBundle.ARRAY -> arrays[current]=value.getStringArray()
                UResourceBundle.TABLE -> processResource(current,key,value)
            }
        }
    }
    private enum class Alias { SAME, DIFFERENT, GREGORIAN, NONE }
    private fun alias(current: String,value: UResource.Value): Alias {
        if(value.getType()!=ICUResourceBundle.ALIAS) return Alias.NONE
        val path=value.getAliasString()
        if(path.startsWith(PREFIX) && path.length>PREFIX.length) {
            val limit=path.indexOf('/',PREFIX.length)
            if(limit>PREFIX.length) {
                val type=path.substring(PREFIX.length,limit); aliasRelativePath=path.substring(limit+1)
                if(currentCalendarType!! == type && current!=aliasRelativePath) return Alias.SAME
                if(currentCalendarType!=type && current==aliasRelativePath) {
                    if(type=="gregorian") return Alias.GREGORIAN
                    if(nextCalendarType==null || nextCalendarType==type) { nextCalendarType=type; return Alias.DIFFERENT }
                }
            }
        }
        throw ICUException("Malformed 'calendar' alias. Path: $path")
    }
    companion object {
        private const val PREFIX="/LOCALE/calendar/"
        private val TABLES=setOf("eras","dayNames","monthNames","quarters","dayPeriod","monthPatterns","cyclicNameSets")
        private val CYCLIC=arrayOf("cyclicNameSets/years/format/abbreviated","cyclicNameSets/zodiacs/format/abbreviated","cyclicNameSets/dayParts/format/abbreviated")
    }
}

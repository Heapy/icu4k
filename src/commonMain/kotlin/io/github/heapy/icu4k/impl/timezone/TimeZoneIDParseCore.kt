// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.TextTrieMap
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.text.ParsePosition

/** TimeZoneFormat's lazily published full-ID and BCP 47 short-ID tries. */
internal object TimeZoneIDParseCore {
    private val lock = createAvailabilityLock()
    private var fullTrie: TextTrieMap<String>? = null
    private var shortTrie: TextTrieMap<String>? = null
    fun parseZoneID(text: String?, pos: ParsePosition?): String? {
        val trie = lock.withLock {
            fullTrie ?: TextTrieMap<String>(true).also { trie ->
                for (id in TimeZoneMetadataCore.getAvailableIDs()) trie.put(id,id)
                fullTrie = trie
            }
        }
        return parse(trie,text,pos)
    }
    fun parseShortZoneID(text: String?, pos: ParsePosition?): String? {
        val trie = lock.withLock {
            shortTrie ?: TextTrieMap<String>(true).also { trie ->
                for (id in ZoneMetaCore.getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.CANONICAL,null,null)) {
                    ZoneMetaCore.getShortID(id)?.let { trie.put(it,id) }
                }
                trie.put("unk","Etc/Unknown")
                shortTrie = trie
            }
        }
        return parse(trie,text,pos)
    }
    private fun parse(trie: TextTrieMap<String>, text: String?, pos: ParsePosition?): String? {
        val output = TextTrieMap.Output()
        val start = pos!!.index
        if (text != null && (start < 0 || start > text.length)) throw IndexOutOfBoundsException()
        val values = trie.get(text,start,output)
        if (values == null) { pos.errorIndex = pos.index; return null }
        val id = values.next()
        pos.index += output.matchLength
        return id
    }
}

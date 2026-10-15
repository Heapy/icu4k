// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.MissingResourceException

internal object CollationRoot {
    private val rootSingleton: CollationTailoring?
    private val exception: RuntimeException?
    init {
        var root: CollationTailoring? = null
        var failure: RuntimeException? = null
        try {
            val bytes = ICUBinary.getRequiredData("coll/ucadata.icu")
            val tailoring = CollationTailoring(null)
            CollationDataReader.read(null, bytes, tailoring)
            root = tailoring
        } catch (e: ICUUncheckedIOException) {
            failure = MissingResourceException("IOException while reading CLDR root data", "CollationRoot", ICUData.ICU_BUNDLE + "/coll/ucadata.icu")
        } catch (e: RuntimeException) {
            failure = e
        }
        rootSingleton = root
        exception = failure
    }
    fun getRoot(): CollationTailoring { exception?.let { throw it }; return rootSingleton!! }
    fun getData(): CollationData = getRoot().data!!
    fun getSettings(): CollationSettings = getRoot().settings!!.readOnly()!!
}

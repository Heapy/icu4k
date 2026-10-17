// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun Transliterator.Companion.getDisplayName(id: String, locale: Locale?): String = getDisplayName(id,ULocale.forLocale(locale))
@Suppress("DEPRECATION")
fun Transliterator.Companion.registerClass(id: String, type: Class<out Transliterator>?, displayName: String?) =
    registerConstructor(id,{
        try {type!!.newInstance()}catch(_:InstantiationException){null}catch(_:IllegalAccessException){null}
    },displayName)

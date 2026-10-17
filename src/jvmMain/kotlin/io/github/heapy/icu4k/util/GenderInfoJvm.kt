package io.github.heapy.icu4k.util

/** Adapter for Java callers' locale values; list processing remains common. */
fun GenderInfo.Companion.getInstance(locale: java.util.Locale): GenderInfo = getInstance(ULocale.forLocale(locale)!!)

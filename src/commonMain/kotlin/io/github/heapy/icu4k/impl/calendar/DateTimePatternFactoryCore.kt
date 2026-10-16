// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUCache
import io.github.heapy.icu4k.impl.SimpleCache
import io.github.heapy.icu4k.util.ULocale

/** Generator factory/cache policy; normal construction requires the actual Calendar owner. */
internal class DateTimePatternFactoryCore(
    private val initialization: DateTimePatternInitializationCore,
    private val createCalendar: (ULocale)->DateTimePatternInitializationCore.CalendarPatterns,
    private val cache: ICUCache<String,DateTimePatternGeneratorCore> = SimpleCache(),
    private val defaultFormatLocale: ()->ULocale = { ULocale.getDefault(ULocale.Category.FORMAT) },
    private val createGenerator: ()->DateTimePatternGeneratorCore = { DateTimePatternGeneratorCore() },
) {
    fun getEmptyInstance(): DateTimePatternGeneratorCore = createGenerator().apply { addCanonicalItems(); fillInMissing() }
    fun getInstance(): DateTimePatternGeneratorCore = getInstance(defaultFormatLocale())
    fun getInstance(locale: ULocale?): DateTimePatternGeneratorCore = getFrozenInstance(locale).cloneAsThawed()
    fun getFrozenInstance(locale: ULocale?): DateTimePatternGeneratorCore {
        val key=locale!!.toString()
        cache.get(key)?.let { return it }
        val result=createGenerator()
        initialization.initialize(result,locale,createCalendar)
        result.freeze()
        cache.put(key,result)
        return result
    }
    fun getInstanceNoStdPat(locale: ULocale?): DateTimePatternGeneratorCore = createGenerator().also {
        initialization.initializeNoStandardPatterns(it,locale)
    }
}

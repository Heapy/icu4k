// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.ULocale

open class UnlocalizedNumberFormatter : NumberFormatterSettings<UnlocalizedNumberFormatter> {
    internal constructor() : super(null, KEY_THRESHOLD, 3L)
    internal constructor(parent: NumberFormatterSettings<*>?, key: Int, value: Any?) : super(parent, key, value)
    open fun locale(locale: ULocale?): LocalizedNumberFormatter = LocalizedNumberFormatter(this, KEY_LOCALE, locale)
    internal override fun create(key: Int, value: Any?): UnlocalizedNumberFormatter = UnlocalizedNumberFormatter(this, key, value)
}

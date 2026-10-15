// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.ULocale

open class UnlocalizedNumberRangeFormatter : NumberRangeFormatterSettings<UnlocalizedNumberRangeFormatter> {
    internal constructor() : super(null, KEY_MACROS, null)
    internal constructor(parent: NumberRangeFormatterSettings<*>?, key: Int, value: Any?) : super(parent, key, value)
    open fun locale(locale: ULocale?): LocalizedNumberRangeFormatter = LocalizedNumberRangeFormatter(this, KEY_LOCALE, locale)
    internal override fun create(key: Int, value: Any?): UnlocalizedNumberRangeFormatter = UnlocalizedNumberRangeFormatter(this, key, value)
}

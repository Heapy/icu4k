// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale

/** Host bridge retaining the legacy overridable Java Locale factory entry point. */
expect abstract class CollatorFactoryLocaleBridge() {
    abstract fun createCollator(loc: ULocale?): Collator?
    abstract fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String?
    protected fun createCollatorHost(loc: ULocale?): Collator?
}

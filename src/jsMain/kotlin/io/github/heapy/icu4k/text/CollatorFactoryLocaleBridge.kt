// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale

actual abstract class CollatorFactoryLocaleBridge actual constructor() {
    actual abstract fun createCollator(loc: ULocale?): Collator?
    actual abstract fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String?
    protected actual fun createCollatorHost(loc: ULocale?): Collator? = createCollator(loc)
}

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import io.github.heapy.icu4k.util.toLocale
import java.util.Locale

actual abstract class CollatorFactoryLocaleBridge actual constructor() {
    actual abstract fun createCollator(loc: ULocale?): Collator?
    actual abstract fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String?
    open fun createCollator(loc: Locale?): Collator? = createCollator(ULocale.forLocale(loc))
    open fun getDisplayName(objectLocale: Locale?, displayLocale: Locale?): String? = getDisplayName(ULocale.forLocale(objectLocale),ULocale.forLocale(displayLocale))
    protected actual fun createCollatorHost(loc: ULocale?): Collator? = createCollator(loc!!.toLocale())
}

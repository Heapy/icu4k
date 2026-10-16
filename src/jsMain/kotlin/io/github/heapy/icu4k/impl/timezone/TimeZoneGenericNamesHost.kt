// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone
import io.github.heapy.icu4k.impl.CacheReference
import io.github.heapy.icu4k.impl.createCacheReference
import io.github.heapy.icu4k.text.LocaleDisplayNames
internal actual fun timeZoneGenericNamesAssertionsEnabled(): Boolean = false
internal actual fun createGenericNameMap(): GenericNameMap = PortableGenericNameMap()
internal actual fun createGenericDisplayNamesReference(value: LocaleDisplayNames): CacheReference<LocaleDisplayNames> = createCacheReference(value)

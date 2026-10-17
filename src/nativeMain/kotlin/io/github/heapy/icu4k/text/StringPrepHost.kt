package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.impl.CacheReference
import io.github.heapy.icu4k.impl.createCacheReference
internal actual fun createStringPrepReference(value: StringPrep): CacheReference<StringPrep> = createCacheReference(value)

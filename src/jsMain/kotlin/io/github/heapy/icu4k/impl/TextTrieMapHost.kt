package io.github.heapy.icu4k.impl

internal actual fun createTextTrieMapLock(owner: Any): ResourceCacheLock = createAvailabilityLock()

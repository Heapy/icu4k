package io.github.heapy.icu4k.impl

internal actual fun createCalendarCacheLock(owner: Any): ResourceCacheLock = createAvailabilityLock()

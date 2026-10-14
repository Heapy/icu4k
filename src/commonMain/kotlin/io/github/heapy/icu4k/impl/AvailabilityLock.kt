package io.github.heapy.icu4k.impl

/** Availability initialization calls loaders while locked, matching Java's reentrant monitor. */
internal expect fun createAvailabilityLock(): ResourceCacheLock

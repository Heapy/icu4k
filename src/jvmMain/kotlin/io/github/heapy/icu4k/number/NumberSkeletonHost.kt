package io.github.heapy.icu4k.number

internal actual fun assertNumberSkeleton(condition: () -> Boolean) {
    if (NumberSkeletonCore::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}

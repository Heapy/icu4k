package io.github.heapy.icu4k.impl.number

internal actual fun assertAffixUtils(condition: Boolean) {
    if (AffixUtils::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}

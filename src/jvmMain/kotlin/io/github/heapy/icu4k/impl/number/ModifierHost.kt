package io.github.heapy.icu4k.impl.number

internal actual fun assertConstantAffixModifier(condition: Boolean) {
    if (ConstantAffixModifier::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
internal actual fun assertAdoptingModifierStore(condition: Boolean) {
    if (AdoptingModifierStore::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
internal actual fun assertSimpleModifier(condition: Boolean) {
    if (SimpleModifier::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}

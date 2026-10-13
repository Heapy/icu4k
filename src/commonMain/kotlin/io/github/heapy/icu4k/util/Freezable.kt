// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/**
 * An object that can be made permanently immutable. Mutators of frozen objects
 * must throw UnsupportedOperationException. Mutable copies are independent of
 * the original; immutable implementations may return themselves from cloneAsThawed.
 * Freeze before sharing with other threads, using the platform's publication rules.
 */
interface Freezable<T> {
    fun isFrozen(): Boolean
    fun freeze(): T
    fun cloneAsThawed(): T
}

@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import kotlin.concurrent.atomics.AtomicReference

actual open class IllegalIcuArgumentException : IllegalArgumentException {
    private class CauseState(val initialized: Boolean, val value: Throwable?)
    private val causeState: AtomicReference<CauseState>
    actual constructor(message: String?) : super(message) { causeState = AtomicReference(CauseState(false, null)) }
    actual constructor(cause: Throwable?) : super(cause) { causeState = AtomicReference(CauseState(true, cause)) }
    actual constructor(message: String?, cause: Throwable?) : super(message, cause) { causeState = AtomicReference(CauseState(true, cause)) }
    override val cause: Throwable? get() = causeState.load().value
    actual open fun initCause(cause: Throwable?): IllegalIcuArgumentException {
        while (true) {
            val old = causeState.load()
            if (old.initialized) throw IllegalStateException("Can't overwrite cause with ${cause ?: "a null"}", this)
            if (cause === this) throw IllegalArgumentException("Self-causation not permitted", this)
            if (causeState.compareAndSet(old, CauseState(true, cause))) return this
        }
    }
}

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Portable counterpart of the Java cursor-cloning failure. */
expect open class CloneNotSupportedException : Exception {
    constructor()
    constructor(message: String?)
}

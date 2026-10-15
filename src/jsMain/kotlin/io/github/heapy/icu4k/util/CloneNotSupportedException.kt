// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

actual open class CloneNotSupportedException : Exception {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
}

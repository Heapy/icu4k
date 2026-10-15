// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** JVM ByteBuffer convenience; consumes limit() bytes, matching ICU4J. */
fun ByteArrayWrapper(source: java.nio.ByteBuffer): ByteArrayWrapper {
    val size=source.limit();val bytes=ByteArray(size)
    source.get(bytes,0,size)
    return ByteArrayWrapper(bytes,size)
}
internal actual fun byteArrayWrapperNegativeSizeException(): RuntimeException = NegativeArraySizeException()

package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CacheReference
import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.InputStream
import java.lang.ref.WeakReference

internal actual fun createStringPrepReference(value: StringPrep): CacheReference<StringPrep> = object : CacheReference<StringPrep> {
    private val reference=WeakReference(value)
    override fun get(): StringPrep? = reference.get()
    override fun clear() { reference.clear() }
}
/** JVM stream adapter: closes the stream even if reading fails, as ICU4J does. */
fun StringPrep(input: InputStream): StringPrep {
    val bytes=try {input.readBytes()}finally{input.close()}
    return StringPrep(BinaryBuffer(bytes))
}

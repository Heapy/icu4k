package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Common resource lookup boundary replacing ClassLoader in binary bundle APIs. */
fun interface ICUResourceLoader {
    fun getData(fullName: String): BinaryBuffer?
}

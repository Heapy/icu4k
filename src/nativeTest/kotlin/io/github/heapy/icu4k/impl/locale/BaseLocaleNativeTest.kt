@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlin.native.runtime.NativeRuntimeApi::class)
package io.github.heapy.icu4k.impl.locale

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.random.Random
import kotlin.test.*

class BaseLocaleNativeTest {
    private fun transientString(): WeakReference<String> {
        val value = buildString { append("TransientLocaleString-"); append(Random(5147).nextInt()) }
        return WeakReference(internLocaleString(value))
    }
    @Test fun stringPoolDoesNotStronglyRetainUnreferencedStrings() {
        val weak = transientString()
        GC.collect()
        assertNull(weak.get())
    }
    @Test fun concurrentLocalesShareInternedFieldStrings() {
        val language = buildString { append("native-concurrent-"); append(Random(21781).nextInt()) }
        val workers = List(4) { Worker.start() }
        try {
            val tasks = workers.mapIndexed { index, worker ->
                worker.execute(TransferMode.SAFE, { language.toCharArray().concatToString() to index }) { (language, index) ->
                    BaseLocale.getInstance(language, "Latn", "X$index", "POSIX")
                }
            }
            val results = tasks.map { it.result }
            val first = results.first()
            for (locale in results) {
                assertSame(first.getLanguage(), locale.getLanguage())
                assertSame(first.getScript(), locale.getScript())
                assertSame(first.getVariant(), locale.getVariant())
            }
        } finally { workers.forEach { it.requestTermination().result } }
    }
}

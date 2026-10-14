@file:OptIn(kotlin.native.concurrent.ObsoleteWorkersApi::class)
package io.github.heapy.icu4k.impl

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

actual interface EventListener
internal actual fun createNotifierDispatcher(notify: (EventListener) -> Unit): NotifierDispatcher {
    val worker = Worker.start(errorReporting = true)
    return NotifierEventPump(notify) { task ->
        worker.execute(TransferMode.SAFE, { task }) { it() }
    }
}

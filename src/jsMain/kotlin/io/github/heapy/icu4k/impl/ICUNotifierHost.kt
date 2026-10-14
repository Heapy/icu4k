package io.github.heapy.icu4k.impl

import kotlin.js.Promise

actual interface EventListener
internal actual fun createNotifierDispatcher(notify: (EventListener) -> Unit): NotifierDispatcher = NotifierEventPump(notify) { task ->
    Promise.resolve(Unit).then { task() }.catch { failure ->
        // Match a background thread's uncaught-error report without resuming its queue.
        console.error(failure)
    }
}

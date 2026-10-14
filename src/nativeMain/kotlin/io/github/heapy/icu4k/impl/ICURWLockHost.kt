package io.github.heapy.icu4k.impl

actual class IllegalMonitorStateException actual constructor(message: String?) : RuntimeException(message)
internal actual fun createICURWLockBackend(): ICURWLockBackend = PortableRWLock(createNativeRWMonitor())
internal expect fun createNativeRWMonitor(): RWMonitor

package io.github.heapy.icu4k.impl

internal actual fun localeDataArrayBounds(failure: Exception): Boolean = failure is ArrayIndexOutOfBoundsException

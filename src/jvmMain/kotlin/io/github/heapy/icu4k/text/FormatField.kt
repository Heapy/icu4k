package io.github.heapy.icu4k.text

actual typealias FormatField = java.text.Format.Field
internal actual fun invalidFormatField(): Nothing = throw java.io.InvalidObjectException("An invalid object.")

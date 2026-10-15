package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream

internal actual fun writeDecimalProperties(stream: PluralObjectOutputStream, properties: DecimalFormatProperties): Unit = throw UnsupportedOperationException("Java object serialization is unavailable")
internal actual fun readDecimalProperties(stream: PluralObjectInputStream, properties: DecimalFormatProperties): Unit = throw UnsupportedOperationException("Java object serialization is unavailable")

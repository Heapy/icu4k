// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
internal actual fun simpleDateFormatAssertionsEnabled(): Boolean = false
internal actual fun simpleDateFormatJavaCalendar(value: Any?): Boolean = false
internal actual fun simpleDateFormatSerialFields(): Array<RbnfObjectStreamField> = emptyArray()
internal actual fun simpleDateFormatWrite(output: PluralObjectOutputStream, owner: SimpleDateFormat): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
internal actual fun simpleDateFormatRead(input: PluralObjectInputStream, owner: SimpleDateFormat): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")

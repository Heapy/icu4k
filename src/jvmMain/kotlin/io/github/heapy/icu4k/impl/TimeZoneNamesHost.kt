// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.util.ULocale
internal actual fun writeTimeZoneNamesLocale(output: PluralObjectOutputStream, locale: ULocale) { output.writeObject(locale) }
internal actual fun readTimeZoneNamesLocale(input: PluralObjectInputStream): ULocale? = input.readObject() as ULocale?

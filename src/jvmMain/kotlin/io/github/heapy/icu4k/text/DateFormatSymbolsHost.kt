// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import kotlin.reflect.KClass
internal actual fun dateSymbolsClassName(type: KClass<*>): String = type.java.name

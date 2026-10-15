// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.util.ICUSerializable
import kotlin.jvm.Transient

/** ICU 59 stream compatibility wrapper for DecimalFormatProperties. */
open class Properties : ICUSerializable {
    @Transient private var instance: DecimalFormatProperties? = null
    open fun getInstance(): DecimalFormatProperties? = instance
    private fun readObject(stream: PluralObjectInputStream) {
        if (instance == null) instance = DecimalFormatProperties()
        instance!!.readObjectImpl(stream)
    }
    private fun writeObject(stream: PluralObjectOutputStream) {
        if (instance == null) instance = DecimalFormatProperties()
        instance!!.writeObjectImpl(stream)
    }
    private companion object { private const val serialVersionUID: Long = 4095518955889349243L }
}

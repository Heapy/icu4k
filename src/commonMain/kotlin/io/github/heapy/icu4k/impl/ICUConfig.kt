// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** ICU runtime configuration: nonempty host properties override file defaults. */
object ICUConfig {
    const val CONFIG_PROPS_FILE = "/com/ibm/icu/ICUConfig.properties"
    private val properties = HashMap<String, String>()
    init { loadICUConfigProperties(properties) }
    @kotlin.jvm.JvmStatic
    fun get(name: String): String? = get(name, null)
    @kotlin.jvm.JvmStatic
    fun get(name: String, defaultValue: String?): String? {
        if (name.isEmpty()) throw IllegalArgumentException("key can't be empty")
        val value = getICUConfigHostProperty(name)
        return if (value.isNullOrEmpty()) properties[name] ?: defaultValue else value
    }
}
internal expect fun loadICUConfigProperties(target: MutableMap<String, String>)
internal expect fun getICUConfigHostProperty(name: String): String?

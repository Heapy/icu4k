@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import java.io.IOException
import java.security.AccessControlException
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.MissingResourceException
import java.util.Properties

internal actual fun loadICUConfigProperties(target: MutableMap<String, String>) {
    val properties = object : Properties() {
        override fun put(key: Any, value: Any): Any? {
            val previous = super.put(key, value)
            if (key is String && value is String) target[key] = value
            return previous
        }
    }
    try {
        val input = if (System.getSecurityManager() == null) ICUConfig::class.java.getResourceAsStream(ICUConfig.CONFIG_PROPS_FILE)
            else AccessController.doPrivileged(PrivilegedAction { ICUConfig::class.java.getResourceAsStream(ICUConfig.CONFIG_PROPS_FILE) })
        if (input != null) try { properties.load(input) } finally { input.close() }
    } catch (_: MissingResourceException) { } catch (_: IOException) { }
}
internal actual fun getICUConfigHostProperty(name: String): String? {
    if (System.getSecurityManager() == null) return System.getProperty(name)
    return try { AccessController.doPrivileged(PrivilegedAction { System.getProperty(name) }) }
        catch (_: AccessControlException) { null }
}

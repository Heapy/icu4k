// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.Enumeration
import io.github.heapy.icu4k.util.MissingResourceException

/** Object/key boundary for Java class/property bundles on every target. */
interface JavaResourceBundle {
    val className: String
    fun getObject(key: String): Any?
    fun getKeys(): Enumeration<String>
}
sealed interface JavaBundleClassResult {
    data class Found(val bundle: JavaResourceBundle) : JavaBundleClassResult
    data object LoadProperties : JavaBundleClassResult
    data class Rejected(val failure: Exception) : JavaBundleClassResult
}

/** Common object bundle; enumeration retains the supplied map's iteration order. */
class MapJavaResourceBundle(private val values: Map<String, Any>, override val className: String = "java.util.PropertyResourceBundle",
                            private val parent: JavaResourceBundle? = null) : JavaResourceBundle {
    override fun getObject(key: String): Any = values[key] ?: parent?.getObject(key)
        ?: throw MissingResourceException("Can't find resource for bundle $className, key $key", className, key)
    override fun getKeys(): Enumeration<String> {
        val own = values.keys.iterator()
        val inherited = parent?.getKeys()
        return object : Enumeration<String> {
            private var next: String? = null
            override fun hasMoreElements(): Boolean {
                if (next == null) {
                    if (own.hasNext()) next = own.next()
                    else if (inherited != null) while (inherited.hasMoreElements()) {
                        val candidate = inherited.nextElement()
                        if (candidate !in values) { next = candidate; break }
                    }
                }
                return next != null
            }
            override fun nextElement(): String {
                if (!hasMoreElements()) throw NoSuchElementException()
                return next!!.also { next = null }
            }
        }
    }
}
internal expect fun defaultJavaResourceLoader(): ICUResourceLoader
internal expect fun javaPropertyResourceEncoding(): String?

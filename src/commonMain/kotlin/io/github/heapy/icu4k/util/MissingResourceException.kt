package io.github.heapy.icu4k.util

/** Common equivalent of java.util.MissingResourceException for ICU resource lookups. */
class MissingResourceException(message: String?, private val className: String?, private val key: String?) : RuntimeException(message) {
    fun getClassName(): String? = className
    fun getKey(): String? = key
}

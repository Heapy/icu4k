package io.github.heapy.icu4k.util

internal actual fun resourceBundleClassName(bundle: UResourceBundle): String = bundle::class.simpleName ?: "UResourceBundle"

package io.github.heapy.icu4k.impl

private val defaultsHost = PortableLocaleDefaultHost()
internal actual fun localeDefaultHost(): LocaleDefaultHost = defaultsHost

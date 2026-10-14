package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import io.github.heapy.icu4k.util.toLocale
import java.util.Locale

private object JvmLocaleDefaultHost : LocaleDefaultHost {
    override fun get(category: ULocale.Category?): Any =
        if (category == null) Locale.getDefault() else Locale.getDefault(category.javaCategory())
    override fun set(category: ULocale.Category?, token: Any) {
        if (category == null) Locale.setDefault(token as Locale)
        else {
            // ICU4J invokes category setters reflectively and ignores target failures.
            try { Locale.setDefault(category.javaCategory(), token as Locale) }
            catch (_: Throwable) { }
        }
    }
    override fun fromLocale(token: Any): ULocale = ULocale.forLocale(token as Locale)!!
    override fun toLocale(locale: ULocale): Any = locale.toLocale()
    private fun ULocale.Category.javaCategory(): Locale.Category = when (this) {
        ULocale.Category.DISPLAY -> Locale.Category.DISPLAY
        ULocale.Category.FORMAT -> Locale.Category.FORMAT
    }
}
internal actual fun localeDefaultHost(): LocaleDefaultHost = JvmLocaleDefaultHost

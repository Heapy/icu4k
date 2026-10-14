package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.lang.reflect.InvocationTargetException
import java.util.Locale

fun LocaleDisplayNames.Companion.getInstance(locale: Locale?): LocaleDisplayNames = getInstance(ULocale.forLocale(locale))
fun LocaleDisplayNames.Companion.getInstance(locale: Locale?, vararg contexts: DisplayContext?): LocaleDisplayNames = getInstance(ULocale.forLocale(locale), *contexts)
fun LocaleDisplayNames.localeDisplayName(locale: Locale?): String? = localeDisplayName(ULocale.forLocale(locale))
internal actual fun createLocaleDisplayNamesFactory(className: String): LocaleDisplayNamesFactory {
    val target = if (className == "com.ibm.icu.impl.LocaleDisplayNamesImpl") "io.github.heapy.icu4k.impl.LocaleDisplayNamesImpl" else className
    val clazz = try { Class.forName(target) } catch (_: ClassNotFoundException) { return MissingLocaleDisplayNamesFactory() }
    val dialect = try { clazz.getMethod("getInstance", ULocale::class.java, LocaleDisplayNames.DialectHandling::class.java) } catch (_: NoSuchMethodException) { null }
    val contexts = try { clazz.getMethod("getInstance", ULocale::class.java, arrayOfNulls<DisplayContext>(0).javaClass) } catch (_: NoSuchMethodException) { null }
    return object : LocaleDisplayNamesFactory {
        override fun get(locale: ULocale?, handling: LocaleDisplayNames.DialectHandling?): LocaleDisplayNames? = try { dialect?.invoke(null, locale, handling) as LocaleDisplayNames? } catch (_: InvocationTargetException) { null } catch (_: IllegalAccessException) { null }
        override fun get(locale: ULocale?, options: Array<out DisplayContext?>): LocaleDisplayNames? = try { contexts?.invoke(null, locale, options) as LocaleDisplayNames? } catch (_: InvocationTargetException) { null } catch (_: IllegalAccessException) { null }
    }
}
internal actual fun emptyLocaleDisplayUiList(): List<LocaleDisplayNames.UiListItem> = java.util.Collections.emptyList()

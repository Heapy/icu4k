@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.TimeZoneNames as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale

/** Fresh JVMs are required: both implementations choose the factory at class initialization. */
object TimeZoneNamesFactoryProbe {
    class SourceNull : Source.Factory() {
        override fun getTimeZoneNames(locale: SourceLocale?): Source? = null
    }
    class PortNull : TimeZoneNames.Factory() {
        override fun getTimeZoneNames(locale: ULocale?): TimeZoneNames? = null
    }
    class SourceFrench : Source.Factory() {
        override fun getTimeZoneNames(locale: SourceLocale?): Source = com.ibm.icu.impl.TimeZoneNamesImpl(SourceLocale.FRANCE)
    }
    class PortFrench : TimeZoneNames.Factory() {
        override fun getTimeZoneNames(locale: ULocale?): TimeZoneNames = io.github.heapy.icu4k.impl.TimeZoneNamesImpl(ULocale.FRANCE)
    }
    @JvmStatic fun main(args: Array<String>) {
        val source = args[0] == "source"
        val implementation = when (args[1]) {
            "null" -> if (source) SourceNull::class.java.name else PortNull::class.java.name
            "custom" -> if (source) SourceFrench::class.java.name else PortFrench::class.java.name
            "wrong" -> "java.lang.String"
            else -> "no.such.Factory"
        }
        System.setProperty("com.ibm.icu.text.TimeZoneNames.Factory.impl", implementation)
        try {
            if (source) {
                val a = Source.getInstance(SourceLocale.US)
                val b = Source.getInstance(SourceLocale("en_US@calendar=hebrew"))
                println("${a === b}:${a?.getDisplayName("America/New_York", Source.NameType.LONG_STANDARD, 0)}")
            } else {
                val a = TimeZoneNames.getInstance(ULocale.US)
                val b = TimeZoneNames.getInstance(ULocale("en_US@calendar=hebrew"))
                println("${a === b}:${a?.getDisplayName("America/New_York", TimeZoneNames.NameType.LONG_STANDARD, 0)}")
            }
        } catch (e: ExceptionInInitializerError) { println(e.cause!!.javaClass.simpleName) }
    }
}

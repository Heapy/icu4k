@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text
object DateFormatRecursionProbe {
    @JvmStatic fun main(args:Array<String>) {
        val value=java.time.chrono.JapaneseDate.of(2024,2,29).atTime(java.time.LocalTime.NOON)
        try {
            if(args[0]=="source") com.ibm.icu.impl.JavaTimeConverters.temporalToCalendar(value)
            else io.github.heapy.icu4k.impl.JavaTimeConverters.temporalToCalendar(value as java.time.temporal.Temporal)
            println("unexpected success")
        } catch(_:StackOverflowError) { println("StackOverflowError") }
    }
}

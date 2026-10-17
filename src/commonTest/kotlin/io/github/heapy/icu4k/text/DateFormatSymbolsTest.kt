@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateFormatSymbolsTest {
    @Test fun symbolsAndDefensiveArrayCopies() {
        val symbols=DateFormatSymbols(ULocale.US)
        assertEquals("January",symbols.getMonths()[0])
        val months=symbols.getMonths();months[0]="changed";assertEquals("January",symbols.getMonths()[0])
        val input=arrayOf("custom");symbols.setMonths(input);input[0]="mutated";assertEquals("custom",symbols.getMonths()[0])
        val copy=symbols.clone();assertEquals(symbols,copy);copy.setMonths(arrayOf("clone"));assertNotEquals(symbols,copy)
        assertEquals("custom",symbols.getMonths()[0])
    }
    @Test fun calendarConstructorsAndZoneCopies() {
        val symbols=DateFormatSymbols(HebrewCalendar::class,ULocale.US)
        assertEquals(14,symbols.getMonths().size)
        symbols.setZoneStrings(arrayOf(arrayOf("id","standard",null)))
        val rows=symbols.getZoneStrings();rows[0]!![1]="changed"
        assertEquals("standard",symbols.getZoneStrings()[0]!![1])
        assertEquals(symbols.getMonths().toList(),DateFormatSymbols(HebrewCalendar(),ULocale.US).getMonths().toList())
    }
    @Test fun protectedInitializationHooksDispatchDuringConstruction() {
        val events=mutableListOf<String>()
        val symbols=object:DateFormatSymbols(ULocale.US) {
            override fun initializeData(locale: ULocale?,type: String?) {
                events.add(type!!);super.initializeData(locale,type)
            }
        }
        assertEquals(listOf("gregorian"),events);assertEquals("January",symbols.getMonths()[0])
    }
    @Test fun amPmConvenienceMethodsDispatchToWidthOverrides() {
        val events=mutableListOf<String>()
        val symbols=object:DateFormatSymbols(ULocale.US) {
            override fun getAmPmStrings(context: Int,width: Int): Array<String?> { events.add("get:$context:$width");return arrayOf("custom") }
            override fun setAmPmStrings(value: Array<out String?>?,context: Int,width: Int) { events.add("set:$context:$width") }
        }
        assertContentEquals(arrayOf("custom"),symbols.getAmPmStrings());symbols.setAmPmStrings(arrayOf("x"))
        assertEquals(listOf("get:0:0","set:0:0"),events)
    }

}

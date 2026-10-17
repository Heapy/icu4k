@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

/** Loaded with an isolated classloader so no earlier test can initialize either calendar. */
object JapaneseCalendarBootstrapProbe {
    private val events = arrayListOf<String>()
    private class KZone : SimpleTimeZone(0, "trace") {
        override fun cloneAsThawed(): SimpleTimeZone { events.add("clone"); return super.cloneAsThawed() }
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) { events.add("offset"); super.getOffset(date, local, offsets) }
    }
    private class JZone : com.ibm.icu.util.SimpleTimeZone(0, "trace") {
        override fun cloneAsThawed(): com.ibm.icu.util.SimpleTimeZone { events.add("clone"); return super.cloneAsThawed() }
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) { events.add("offset"); super.getOffset(date, local, offsets) }
    }
    private class KCalendar : JapaneseCalendar {
        constructor() : super()
        constructor(year: Int, month: Int, date: Int) : super(year, month, date)
        constructor(date: Date) : super(date)
        override fun setWeekData(data: Calendar.WeekData?): Calendar { events.add("week"); return super.setWeekData(data) }
        override fun setTimeInMillis(millis: Long) { events.add("time"); super.setTimeInMillis(millis) }
    }
    private class JCalendar : com.ibm.icu.util.JapaneseCalendar {
        constructor() : super()
        constructor(year: Int, month: Int, date: Int) : super(year, month, date)
        constructor(date: java.util.Date) : super(date)
        override fun setWeekData(data: com.ibm.icu.util.Calendar.WeekData?): com.ibm.icu.util.Calendar { events.add("week"); return super.setWeekData(data) }
        override fun setTimeInMillis(millis: Long) { events.add("time"); super.setTimeInMillis(millis) }
    }
    @JvmStatic fun run(mode: String, form: String): String {
        if (mode == "port") {
            TimeZone.setICUDefault(KZone().apply { freeze() }); events.clear()
            when (form) { "fields" -> KCalendar(1, 4, 1); "date" -> KCalendar(Date(0)); else -> KCalendar() }
        } else {
            com.ibm.icu.util.TimeZone.setICUDefault(JZone().apply { freeze() }); events.clear()
            when (form) { "fields" -> JCalendar(1, 4, 1); "date" -> JCalendar(java.util.Date(0)); else -> JCalendar() }
        }
        return events.joinToString(",")
    }
}

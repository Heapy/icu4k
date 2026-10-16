package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class JapaneseCalendarCoreTest {
    private class Fixture : JapaneseCalendarCore.Owner {
        val fields=IntArray(24)
        val set=BooleanArray(24)
        val trace=ArrayList<String>()
        var preferYear=false
        var failField: Int?=null
        val rules=EraRulesCore.getInstance("japanese",false,object : EraRulesCore.Owner {
            override fun currentTimeMillis()=0L
            override fun defaultZoneOffset(time: Long)=0
        })
        val current=rules.getEraCode(2019,5,1)
        val core=JapaneseCalendarCore(rules,current,this)
        override fun newerField(defaultField: Int,alternateField: Int): Int { trace.add("newer:$alternateField"); return if(preferYear) alternateField else defaultField }
        override fun internalGet(field: Int): Int { trace.add("internal:$field"); return fields[field] }
        override fun internalGet(field: Int,defaultValue: Int): Int { trace.add("internal:$field:$defaultValue"); return if(set[field]) fields[field] else defaultValue }
        override fun internalSet(field: Int,value: Int) { trace.add("set:$field:$value"); if(failField==field) error("set"); fields[field]=value; set[field]=true }
        override fun gregorianComputeFields(julianDay: Int) { trace.add("base:$julianDay"); fields[19]=2019; fields[2]=4; fields[5]=1 }
        override fun baseDefaultMonth(year: Int): Int { trace.add("month:$year"); return 0 }
        override fun baseDefaultDay(year: Int,month: Int): Int { trace.add("day:$year:$month"); return 1 }
        override fun baseGetLimit(field: Int,type: Int): Int { trace.add("limit:$field:$type"); return 5000000 }
        override fun baseGetActualMaximum(field: Int): Int { trace.add("maximum:$field"); return 31 }
        override fun get(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun handleGetLimit(field: Int,type: Int): Int { trace.add("virtualLimit:$field:$type"); return core.handleGetLimit(field,type) }
    }

    @Test fun yearDefaultsPrecedenceOverflowAndAccessionDefaults() {
        val f=Fixture()
        assertEquals(1970,f.core.handleGetExtendedYear()); assertEquals(listOf("newer:1","newer:0","internal:19:1970"),f.trace)
        f.trace.clear(); f.preferYear=true
        assertEquals(2019,f.core.handleGetExtendedYear())
        assertEquals(listOf("newer:1","internal:1:1","internal:0:${f.current}"),f.trace)
        f.set[1]=true; f.fields[1]=Int.MAX_VALUE
        assertEquals(Int.MIN_VALUE+2017,f.core.handleGetExtendedYear())
        assertEquals(4,f.core.getDefaultMonthInYear(2019)); assertEquals(1,f.core.getDefaultDayInMonth(2019,4))
        val heisei=f.rules.getEraCode(1989,1,8); f.fields[0]=heisei; f.set[0]=true
        assertEquals(0,f.core.getDefaultMonthInYear(1989)); assertEquals(8,f.core.getDefaultDayInMonth(1989,0))
        assertEquals(1,f.core.getDefaultDayInMonth(1989,1)); assertEquals(0,f.core.getDefaultMonthInYear(1990))
    }

    @Test fun limitsActualMaximaAndMetadata() {
        val f=Fixture()
        assertEquals(0,f.core.handleGetLimit(0,0)); assertEquals(f.rules.getMaxEraCode(),f.core.handleGetLimit(0,99))
        for(type in 0..2) assertEquals(1,f.core.handleGetLimit(1,type))
        assertEquals(5000000-2019,f.core.handleGetLimit(1,3))
        f.fields[0]=f.rules.getEraCode(1989,1,8)
        assertEquals(31,f.core.getActualMaximum(1))
        f.fields[0]=f.rules.getMaxEraCode(); f.trace.clear()
        assertEquals(5000000-2019,f.core.getActualMaximum(1))
        assertEquals(listOf("get:0","virtualLimit:1:3","limit:1:3"),f.trace)
        assertEquals(31,f.core.getActualMaximum(5)); assertEquals("maximum:5",f.trace.last())
        assertFalse(f.core.isEra0CountingBackward()); assertFalse(f.core.haveDefaultCentury()); assertEquals("japanese",f.core.getType())
    }

    @Test fun gregorianFieldConversionAndPartialWriteOrder() {
        val f=Fixture(); f.core.handleComputeFields(1)
        assertEquals(listOf("base:1","internal:19","internal:2","internal:5","set:0:${f.current}","set:1:1"),f.trace)
        assertEquals(f.current,f.fields[0]); assertEquals(1,f.fields[1])
        val broken=Fixture(); broken.failField=1
        assertFailsWith<IllegalStateException> { broken.core.handleComputeFields(1) }
        assertEquals(broken.current,broken.fields[0]); assertTrue(broken.set[0]); assertFalse(broken.set[1])
    }

    @Test fun tentativeConfigurationPrecedenceAndFailurePropagation() {
        class Config(val property: String?,val environment: String?,val jdk: String?) : JapaneseCalendarCore.Companion.Configuration {
            val trace=ArrayList<String>()
            override fun systemProperty(name: String): String? { trace.add(name); return if(name=="ICU_ENABLE_TENTATIVE_ERA") property else jdk }
            override fun environmentVariable(name: String): String? { trace.add("env:$name"); return environment }
        }
        val property=Config("FALSE","true","present")
        assertFalse(JapaneseCalendarCore.enableTentativeEra(property)); assertEquals(listOf("ICU_ENABLE_TENTATIVE_ERA"),property.trace)
        assertTrue(JapaneseCalendarCore.enableTentativeEra(Config(null,"TrUe",null)))
        assertFalse(JapaneseCalendarCore.enableTentativeEra(Config(null," true ","present")))
        val fallback=Config(null,null,"")
        assertTrue(JapaneseCalendarCore.enableTentativeEra(fallback))
        assertEquals(listOf("ICU_ENABLE_TENTATIVE_ERA","env:ICU_ENABLE_TENTATIVE_ERA","jdk.calendar.japanese.supplemental.era"),fallback.trace)
        assertFalse(JapaneseCalendarCore.enableTentativeEra(Config(null,null,null)))
        val failing=object : JapaneseCalendarCore.Companion.Configuration {
            override fun systemProperty(name: String): String? = error("property failed")
            override fun environmentVariable(name: String): String? = error("environment must not be read")
        }
        assertEquals("property failed",assertFailsWith<IllegalStateException> { JapaneseCalendarCore.enableTentativeEra(failing) }.message)
    }
}

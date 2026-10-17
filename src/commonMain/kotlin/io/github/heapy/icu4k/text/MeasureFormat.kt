// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.number.DecimalFormatCore
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.LongNameHandler
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.number.*
import io.github.heapy.icu4k.number.NumberFormatter.UnitWidth
import io.github.heapy.icu4k.util.*
import kotlin.math.floor

/** Legacy localized measure lists, per-unit quantities and numeric h/m/s durations. */
open class MeasureFormat : UFormat {
    enum class FormatWidth(internal val listWidth: ListFormatter.Width, internal val unitWidth: UnitWidth, internal val currencyWidth: UnitWidth) {
        WIDE(ListFormatter.Width.WIDE,UnitWidth.FULL_NAME,UnitWidth.FULL_NAME),
        SHORT(ListFormatter.Width.SHORT,UnitWidth.SHORT,UnitWidth.ISO_CODE),
        NARROW(ListFormatter.Width.NARROW,UnitWidth.NARROW,UnitWidth.SHORT),
        NUMERIC(ListFormatter.Width.NARROW,UnitWidth.NARROW,UnitWidth.SHORT),
        @Deprecated("This API is ICU internal only.")
        DEFAULT_CURRENCY(ListFormatter.Width.SHORT,UnitWidth.FULL_NAME,UnitWidth.SHORT)
    }
    @kotlin.jvm.Transient private val formatWidth: FormatWidth?
    @kotlin.jvm.Transient private val rules: PluralRules?
    @kotlin.jvm.Transient private val numericFormatters: NumericFormatters?
    @kotlin.jvm.Transient private val numberFormat: NumberFormat?
    @kotlin.jvm.Transient private val numberFormatter: LocalizedNumberFormatter?
    @kotlin.jvm.Transient private var formatter1: NumberFormatterCacheEntry? = null
    @kotlin.jvm.Transient private var formatter2: NumberFormatterCacheEntry? = null
    @kotlin.jvm.Transient private var formatter3: NumberFormatterCacheEntry? = null
    @kotlin.jvm.Transient private var cacheLock = createCalendarCacheLock(this)
    private class NumberFormatterCacheEntry(val type: Int,val unit: MeasureUnit?,val perUnit: MeasureUnit?,val formatter: LocalizedNumberFormatter)
    internal class NumericFormatters(val hourMinute: String,val minuteSecond: String,val hourMinuteSecond: String)

    private constructor(locale: ULocale?,width: FormatWidth?,format: NumberFormat?,rules: PluralRules?,numeric: NumericFormatters?) : super() {
        setLocale(locale,locale)
        formatWidth=width
        this.rules=rules?:PluralRules.forLocale(locale)
        val nf=if(format==null) NumberFormat.getInstance(locale) else format.clone()
        numberFormat=nf
        var durations=numeric
        if(durations==null && width==FormatWidth.NUMERIC) {
            durations=localeToNumericDurationFormatters.get(locale!!)
            if(durations==null) {
                val bundle=UResourceBundle.getBundleInstance(ICUData.ICU_UNIT_BASE_NAME,locale) as ICUResourceBundle
                fun pattern(type:String)=bundle.getStringWithFallback("durationUnits/$type").replace("h","H")
                durations=NumericFormatters(pattern("hm"),pattern("ms"),pattern("hms"))
                localeToNumericDurationFormatters.put(locale,durations)
            }
        }
        numericFormatters=durations
        if(nf !is DecimalFormat) throw IllegalArgumentException()
        numberFormatter=nf.toNumberFormatter().unitWidth(width!!.unitWidth)
    }
    internal constructor(locale: ULocale?,width: FormatWidth?) : this(locale,width,null,null,null)
    internal constructor(locale: ULocale?,width: FormatWidth?,format: NumberFormat?,rules: PluralRules?) : this(locale,width,format,rules,null) {
        if(width==FormatWidth.NUMERIC) throw IllegalArgumentException("The format width 'numeric' is not allowed by this constructor")
    }
    protected constructor(other: MeasureFormat,@Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        other.copyLocaleTo(this)
        formatWidth=other.formatWidth;rules=other.rules;numericFormatters=other.numericFormatters
        numberFormat=other.numberFormat;numberFormatter=other.numberFormatter
        formatter1=other.formatter1;formatter2=other.formatter2;formatter3=other.formatter3
    }
    public override fun clone(): Any {
        val copy=(cloneHost()?:cloneNative()) as MeasureFormat
        copy.cacheLock=createCalendarCacheLock(copy)
        return copy
    }
    protected open fun cloneNative(): MeasureFormat = MeasureFormat(this,Unit)
    override fun format(input: Any?,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder {
        val previous=output!!.length
        position!!.setBeginIndex(0);position.setEndIndex(0)
        when(input) {
            is Collection<*> -> {
                val measures=arrayOfNulls<Measure>(input.size)
                var i=0
                for(item in input) {
                    if(item !is Measure) throw IllegalArgumentException(input.toString())
                    if(i>=measures.size) throw IndexOutOfBoundsException("Index: $i, Length: ${measures.size}")
                    measures[i++]=item
                }
                formatMeasuresInternal(output,position,measures)
            }
            is Array<*> -> if(measureFormatIsMeasureArray(input)) {
                @Suppress("UNCHECKED_CAST")
                formatMeasuresInternal(output,position,input as Array<out Measure?>)
            } else throw IllegalArgumentException(input.toString())
            is Measure -> {
                val result=formatMeasure(input)
                FormattedValueStringBuilderImpl.nextFieldPosition(result,position)
                Utility.appendTo(result,output)
            }
            else -> throw IllegalArgumentException(input!!.toString())
        }
        if(previous>0 && position.getEndIndex()!=0) {
            position.setBeginIndex(position.getBeginIndex()+previous);position.setEndIndex(position.getEndIndex()+previous)
        }
        return output
    }
    override fun parseObject(source: String?,position: ParsePosition?): Measure? = throw UnsupportedOperationException()
    fun formatMeasures(vararg measures: Measure?): String = formatMeasures(StringBuilder(),DontCareFieldPosition.INSTANCE,*measures).toString()
    open fun formatMeasurePerUnit(measure: Measure?,perUnit: MeasureUnit?,output: StringBuilder?,position: FormatFieldPosition?): StringBuilder {
        val quantity=DecimalQuantity_DualStorageBCD(measure!!.getNumber())
        val string=FormattedStringBuilder()
        getUnitFormatterFromCache(NUMBER_FORMATTER_STANDARD,measure.getUnit(),perUnit).formatImpl(quantity,string)
        DecimalFormatCore.fieldPositionHelper(quantity,string,position,output!!.length)
        Utility.appendTo(string,output)
        return output
    }
    open fun formatMeasures(output: StringBuilder?,position: FormatFieldPosition?,vararg measures: Measure?): StringBuilder {
        val previous=output!!.length
        formatMeasuresInternal(output,position,measures)
        if(previous>0 && position!!.getEndIndex()>0) {
            position.setBeginIndex(position.getBeginIndex()+previous);position.setEndIndex(position.getEndIndex()+previous)
        }
        return output
    }
    private fun formatMeasuresInternal(output: Appendable,position: FormatFieldPosition?,measures: Array<out Measure?>) {
        if(measures.isEmpty()) return
        if(measures.size==1) {
            val result=formatMeasure(measures[0])
            FormattedValueStringBuilderImpl.nextFieldPosition(result,position!!)
            Utility.appendTo(result,output)
            return
        }
        if(formatWidth==FormatWidth.NUMERIC) {
            val hms=toHMS(measures)
            if(hms!=null) { formatNumeric(hms,output);return }
        }
        val list=ListFormatter.getInstance(getLocale(),ListFormatter.Type.UNITS,formatWidth!!.listWidth)
        if(position!==DontCareFieldPosition.INSTANCE) { formatMeasuresSlowTrack(list,output,position,measures);return }
        val results=measures.mapIndexed { i,measure -> (if(i==measures.lastIndex) formatMeasure(measure) else formatMeasureInteger(measure)).toString() }
        list.formatImpl(results,false).appendTo(output)
    }
    open fun getUnitDisplayName(unit: MeasureUnit?): String? = LongNameHandler.getUnitDisplayName(getLocale(),unit,formatWidth!!.unitWidth)
    final override fun equals(other: Any?): Boolean = this===other || other is MeasureFormat && getWidth()==other.getWidth() && getLocale()!!.equals(other.getLocale()) && getNumberFormatInternal()!!.equals(other.getNumberFormatInternal())
    final override fun hashCode(): Int = (getLocale()!!.hashCode()*31+getNumberFormatInternal()!!.hashCode())*31+getWidth()!!.hashCode()
    open fun getWidth(): FormatWidth? = if(formatWidth==FormatWidth.DEFAULT_CURRENCY) FormatWidth.WIDE else formatWidth
    fun getLocale(): ULocale? = getLocale(ULocale.VALID_LOCALE)
    open fun getNumberFormat(): NumberFormat = numberFormat!!.clone()
    internal open fun getNumberFormatInternal(): NumberFormat? = numberFormat
    internal open fun getNumberFormatter(): LocalizedNumberFormatter? = numberFormatter
    internal open fun withLocale(locale: ULocale?): MeasureFormat = getInstance(locale,getWidth())
    internal open fun withNumberFormat(format: NumberFormat?): MeasureFormat = MeasureFormat(getLocale(),formatWidth,format,rules,numericFormatters)
    internal fun clearCache() = cacheLock.withLock { formatter1=null;formatter2=null;formatter3=null }
    private fun getUnitFormatterFromCache(type: Int,unit: MeasureUnit?,perUnit: MeasureUnit?): LocalizedNumberFormatter = cacheLock.withLock {
        val one=formatter1
        if(one!=null) {
            if(one.type==type && one.unit===unit && one.perUnit===perUnit) return@withLock one.formatter
            val two=formatter2
            if(two!=null) {
                if(two.type==type && two.unit===unit && two.perUnit===perUnit) return@withLock two.formatter
                val three=formatter3
                if(three!=null && three.type==type && three.unit===unit && three.perUnit===perUnit) return@withLock three.formatter
            }
        }
        val formatter=when(type) {
            NUMBER_FORMATTER_STANDARD -> getNumberFormatter()!!.unit(unit).perUnit(perUnit).unitWidth(formatWidth!!.unitWidth)
            NUMBER_FORMATTER_CURRENCY -> NumberFormatter.withLocale(getLocale()).unit(unit).perUnit(perUnit).unitWidth(formatWidth!!.currencyWidth)
            else -> getNumberFormatter()!!.unit(unit).perUnit(perUnit).unitWidth(formatWidth!!.unitWidth).precision(Precision.integer().withMode(RoundingUtils.mathContextUnlimited(RoundingMode.DOWN)))
        }
        formatter3=formatter2;formatter2=formatter1;formatter1=NumberFormatterCacheEntry(type,unit,perUnit,formatter)
        formatter
    }
    private fun formatMeasure(measure: Measure?): FormattedStringBuilder {
        val unit=measure!!.getUnit()
        val quantity=DecimalQuantity_DualStorageBCD(measure.getNumber())
        val string=FormattedStringBuilder()
        getUnitFormatterFromCache(if(unit is Currency) NUMBER_FORMATTER_CURRENCY else NUMBER_FORMATTER_STANDARD,unit,null).formatImpl(quantity,string)
        return string
    }
    private fun formatMeasureInteger(measure: Measure?): FormattedStringBuilder {
        val quantity=DecimalQuantity_DualStorageBCD(measure!!.getNumber())
        val string=FormattedStringBuilder()
        getUnitFormatterFromCache(NUMBER_FORMATTER_INTEGER,measure.getUnit(),null).formatImpl(quantity,string)
        return string
    }
    private fun formatMeasuresSlowTrack(list: ListFormatter,output: Appendable,position: FormatFieldPosition?,measures: Array<out Measure?>) {
        val fpos=FormatFieldPosition(position!!.getFieldAttribute(),position.getField())
        var found=-1
        val results=measures.mapIndexed { i,measure ->
            val result=if(i==measures.lastIndex) formatMeasure(measure) else formatMeasureInteger(measure)
            if(found==-1) {
                FormattedValueStringBuilderImpl.nextFieldPosition(result,fpos)
                if(fpos.getEndIndex()!=0) found=i
            }
            result.toString()
        }
        val builder=list.formatImpl(results,true)
        val offset=builder.getOffset(found)
        if(offset!=-1) { position.setBeginIndex(fpos.getBeginIndex()+offset);position.setEndIndex(fpos.getEndIndex()+offset) }
        builder.appendTo(output)
    }
    private fun formatNumeric(hms: Array<Number?>,output: Appendable) {
        val pattern=when {
            hms[0]!=null && hms[2]!=null -> {
                if(hms[1]==null) hms[1]=0
                hms[1]=floor(hms[1]!!.toDouble());hms[0]=floor(hms[0]!!.toDouble())
                numericFormatters!!.hourMinuteSecond
            }
            hms[0]!=null && hms[1]!=null -> { hms[0]=floor(hms[0]!!.toDouble());numericFormatters!!.hourMinute }
            hms[1]!=null && hms[2]!=null -> { hms[1]=floor(hms[1]!!.toDouble());numericFormatters!!.minuteSecond }
            else -> throw IllegalStateException()
        }
        val padded=numberFormatter!!.integerWidth(IntegerWidth.zeroFillTo(2))
        val string=FormattedStringBuilder()
        var protect=false;var i=0
        while(i<pattern.length) {
            val c=pattern[i]
            val value=when(c) { 'H'->hms[0];'m'->hms[1];'s'->hms[2];else->0 }
            when(c) {
                'H','m','s' -> if(protect) string.appendChar16(c,null) else {
                    if(i+1<pattern.length && pattern[i+1]==c) { string.append(padded.format(value),null);i++ }
                    else string.append(numberFormatter.format(value),null)
                }
                '\'' -> if(i+1<pattern.length && pattern[i+1]==c) { string.appendChar16(c,null);i++ } else protect=!protect
                else -> string.appendChar16(c,null)
            }
            i++
        }
        Utility.appendTo(string,output)
    }
    internal fun toTimeUnitProxy(): Any = MeasureProxy(getLocale(),formatWidth,getNumberFormatInternal(),1)
    internal fun toCurrencyProxy(): Any = MeasureProxy(getLocale(),formatWidth,getNumberFormatInternal(),2)
    private fun writeReplace(): Any = MeasureProxy(getLocale(),formatWidth,getNumberFormatInternal(),0)

    internal class MeasureProxy() : MeasureFormatExternalizable {
        private var locale: ULocale?=null
        private var formatWidth: FormatWidth?=null
        private var numberFormat: NumberFormat?=null
        private var subClass=0
        private var keyValues: MutableMap<Any?,Any?>?=null
        constructor(locale: ULocale?,width: FormatWidth?,format: NumberFormat?,subClass: Int) : this() {
            this.locale=locale;formatWidth=width;numberFormat=format;this.subClass=subClass;keyValues=HashMap()
        }
        override fun writeExternal(output: MeasureFormatObjectOutput) {
            output.writeByte(0);output.writeUTF(locale!!.toLanguageTag());output.writeByte(formatWidth!!.ordinal)
            output.writeObject(numberFormat);output.writeByte(subClass);output.writeObject(keyValues)
        }
        override fun readExternal(input: MeasureFormatObjectInput) {
            input.readByte();locale=ULocale.forLanguageTag(input.readUTF())
            formatWidth=FormatWidth.entries.getOrNull(input.readByte().toInt() and 255)?:FormatWidth.SHORT
            numberFormat=input.readObject() as NumberFormat?
            if(numberFormat==null) invalidMeasureFormatStream("Missing number format.")
            subClass=input.readByte().toInt() and 255
            keyValues=measureFormatOptionalValues(input.readObject())
            if(keyValues==null) invalidMeasureFormatStream("Missing optional values map.")
        }
        private fun readResolve(): Any = when(subClass) {
            0 -> getInstance(locale,formatWidth,numberFormat)
            1 -> {
                val style=when(formatWidth) { FormatWidth.WIDE -> TimeUnitFormat.FULL_NAME;FormatWidth.SHORT -> TimeUnitFormat.ABBREVIATED_NAME;else -> invalidMeasureFormatStream("Bad width: $formatWidth") }
                TimeUnitFormat(locale,style).setNumberFormat(numberFormat)
            }
            2 -> getCurrencyFormat(locale)
            else -> invalidMeasureFormatStream("Unknown subclass: $subClass")
        }
        companion object { private const val serialVersionUID = -6033308329886716770L }
    }
    companion object {
        private const val serialVersionUID = -7182021401701778240L
        private const val NUMBER_FORMATTER_STANDARD=1
        private const val NUMBER_FORMATTER_CURRENCY=2
        private const val NUMBER_FORMATTER_INTEGER=3
        private val localeToNumericDurationFormatters=SimpleCache<ULocale,NumericFormatters>()
        private val hmsTo012=hashMapOf<MeasureUnit,Int>(MeasureUnit.HOUR to 0,MeasureUnit.MINUTE to 1,MeasureUnit.SECOND to 2)
        private val localeIdToRangeFormat=HashMap<ULocale,String>()
        private val rangeLock=createAvailabilityLock()
        fun getInstance(locale: ULocale?,width: FormatWidth?): MeasureFormat = getInstance(locale,width,NumberFormat.getInstance(locale))
        fun getInstance(locale: ULocale?,width: FormatWidth?,format: NumberFormat?): MeasureFormat = MeasureFormat(locale,width,format,null,null)
        fun getCurrencyFormat(locale: ULocale?): MeasureFormat = CurrencyFormat(locale)
        fun getCurrencyFormat(): MeasureFormat = getCurrencyFormat(ULocale.getDefault(ULocale.Category.FORMAT))
        private fun toHMS(measures: Array<out Measure?>): Array<Number?>? {
            val result=arrayOfNulls<Number>(3)
            var last=-1
            for(item in measures) {
                val measure=item!!
                if(measure.getNumber().toDouble()<0.0) return null
                val index=hmsTo012[measure.getUnit()]?:return null
                if(index<=last) return null
                last=index;result[index]=measure.getNumber()
            }
            return result
        }
        @Deprecated("This API is ICU internal only.")
        fun getRangeFormat(locale: ULocale?,width: FormatWidth?): String {
            if(locale!!.getLanguage()=="fr") return getRangeFormat(ULocale.ROOT,width)
            rangeLock.withLock { localeIdToRangeFormat[locale] }?.let { return it }
            val bundle=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,locale) as ICUResourceBundle
            val real=bundle.getULocale()
            if(locale!=real) rangeLock.withLock { localeIdToRangeFormat[locale] }?.let {
                rangeLock.withLock { localeIdToRangeFormat[locale]=it };return it
            }
            val ns=NumberingSystem.getInstance(locale)
            val raw=try { bundle.getStringWithFallback("NumberElements/${ns.getName()}/miscPatterns/range") }
            catch(_:MissingResourceException) { bundle.getStringWithFallback("NumberElements/latn/patterns/range") }
            val result=SimpleFormatterImpl.compileToStringMinMaxArguments(raw,StringBuilder(),2,2)
            rangeLock.withLock { localeIdToRangeFormat[locale]=result;if(locale!=real) localeIdToRangeFormat[real]=result }
            return result
        }
    }
}
internal expect interface MeasureFormatExternalizable : ICUSerializable {
    fun writeExternal(output: MeasureFormatObjectOutput)
    fun readExternal(input: MeasureFormatObjectInput)
}
internal expect interface MeasureFormatObjectOutput {
    fun writeByte(value: Int)
    fun writeUTF(value: String)
    fun writeObject(value: Any?)
}
internal expect interface MeasureFormatObjectInput {
    fun readByte(): Byte
    fun readUTF(): String
    fun readObject(): Any?
}
internal expect fun invalidMeasureFormatStream(message: String): Nothing
internal expect fun measureFormatOptionalValues(value: Any?): MutableMap<Any?,Any?>?
internal expect fun measureFormatIsMeasureArray(value: Array<*>): Boolean

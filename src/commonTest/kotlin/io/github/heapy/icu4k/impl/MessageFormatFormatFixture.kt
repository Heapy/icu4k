package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*

internal object MessageArgumentField : FormatField("message argument field")
internal open class MessageFormatFormatFixture(val pattern: String,val id: String="en_US",mode: MessagePattern.ApostropheMode?=null) : MessageFormatFormatCore.Owner {
    override val messagePattern=if(mode==null) MessagePattern(pattern) else MessagePattern(mode).parse(pattern)
    override var locale: ULocale?=ULocale(id)
    override val argumentField: FormatField get()=MessageArgumentField
    override val cachedFormatters=HashMap<Int,MessageFormatFormatCore.Formatter?>()
    val core=MessageFormatFormatCore(this)
    private val numbers=MessageFormatFormatCore.CommonFormatter(NumberFormat.getInstance(locale)!!)
    init {
        for(i in 0 until messagePattern.countParts()) {
            val part=messagePattern.getPart(i)
            if(part.getType()==MessagePattern.Part.Type.ARG_START && part.getArgType()==MessagePattern.ArgType.SIMPLE) {
                val type=messagePattern.getSubstring(messagePattern.getPart(i+2))
                val style=messagePattern.getPart(i+3).let { if(it.getType()==MessagePattern.Part.Type.ARG_STYLE) messagePattern.getSubstring(it) else "" }
                if(type=="number") {
                    val formatter=when(style.trim()) {
                        "" -> NumberFormat.getInstance(locale)!!
                        "integer" -> NumberFormat.getIntegerInstance(locale)!!
                        "currency" -> NumberFormat.getCurrencyInstance(locale)!!
                        "percent" -> NumberFormat.getPercentInstance(locale)!!
                        else -> DecimalFormat(style,DecimalFormatSymbols(locale))
                    }
                    cachedFormatters[i]=MessageFormatFormatCore.CommonFormatter(formatter)
                }
            }
        }
    }
    override fun stockNumberFormatter(): MessageFormatFormatCore.Formatter=numbers
    override fun stockDateFormatter(): MessageFormatFormatCore.Formatter=error("Date formatter not configured by this test fixture")
    override fun isDateLike(value: Any): Boolean=value is Date || value is Calendar
    override fun child(pattern: String,mode: MessagePattern.ApostropheMode?): MessageFormatFormatCore=MessageFormatFormatFixture(pattern,id,mode).core
    fun text(args: Any?): String {
        val dest=MessageFormatFormatCore.Destination(StringBuilder())
        core.format(args,dest)
        return dest.output.toString()
    }
}

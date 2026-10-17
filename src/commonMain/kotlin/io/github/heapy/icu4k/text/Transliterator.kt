// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.createTextTrieMapLock
import io.github.heapy.icu4k.util.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Text transliteration with rules, registered transforms, filters and incremental input. */
abstract class Transliterator protected constructor(id: String?, filter: UnicodeFilter?) : StringTransform {
    open class Position(@JvmField var contextStart: Int, @JvmField var contextLimit: Int,
        @JvmField var start: Int, @JvmField var limit: Int) {
        constructor() : this(0,0,0,0)
        constructor(contextStart: Int, contextLimit: Int, start: Int) : this(contextStart,contextLimit,start,contextLimit)
        constructor(pos: Position) : this() { set(pos) }
        fun set(pos: Position) { contextStart=pos.contextStart;contextLimit=pos.contextLimit;start=pos.start;limit=pos.limit }
        fun validate(length: Int) {
            if(contextStart<0 || start<contextStart || limit<start || contextLimit<limit || length<contextLimit)
                throw IllegalArgumentException("Invalid Position {cs=$contextStart, s=$start, l=$limit, cl=$contextLimit}, len=$length")
        }
        override fun equals(other: Any?): Boolean = other is Position && contextStart==other.contextStart && contextLimit==other.contextLimit && start==other.start && limit==other.limit
        override fun hashCode(): Int {var result=1;for(value in intArrayOf(contextStart,contextLimit,start,limit))result=31*result+value;return result}
        override fun toString(): String = "[cs=$contextStart, s=$start, l=$limit, cl=$contextLimit]"
    }
    private var identifier: String? = id ?: throw NullPointerException()
    private var maximumContextLength = 0
    private var filterSet: UnicodeSet? = null
    private val execution = TransliteratorExecutionCore(object : TransliteratorExecutionCore.Input {
        override val id: String? get() = identifier
        override var filter: UnicodeSet? get() = filterSet; set(value) { filterSet=value }
        override fun handleTransliterate(text: Replaceable, pos: Position, incremental: Boolean) = this@Transliterator.handleTransliterate(text,pos,incremental)
    })
    init { setFilter(filter) }
    fun transliterate(text: Replaceable, start: Int, limit: Int): Int = execution.transliterate(text,start,limit)
    fun transliterate(text: Replaceable) = execution.transliterate(text)
    fun transliterate(text: String): String = execution.transliterate(text)
    fun transliterate(text: Replaceable, index: Position, insertion: String?) = execution.transliterate(text,index,insertion)
    fun transliterate(text: Replaceable, index: Position, insertion: Int) = execution.transliterate(text,index,insertion)
    fun transliterate(text: Replaceable, index: Position) = execution.transliterate(text,index,null)
    fun finishTransliteration(text: Replaceable, index: Position) = execution.finishTransliteration(text,index)
    protected abstract fun handleTransliterate(text: Replaceable, pos: Position, incremental: Boolean)
    open fun filteredTransliterate(text: Replaceable, index: Position, incremental: Boolean) = execution.filteredTransliterate(text,index,incremental)
    fun getMaximumContextLength(): Int = maximumContextLength
    protected open fun setMaximumContextLength(value: Int) {
        if(value<0)throw IllegalArgumentException("Invalid context length $value")
        maximumContextLength=value
    }
    fun getID(): String? = identifier
    protected fun setID(id: String?) { identifier=id }
    fun getFilter(): UnicodeFilter? = filterSet
    open fun setFilter(filter: UnicodeFilter?) = execution.setFilter(filter)
    open fun toRules(escapeUnprintable: Boolean): String = baseToRules(escapeUnprintable)
    protected fun baseToRules(escapeUnprintable: Boolean): String = execution.baseToRules(escapeUnprintable)
    internal fun elementBaseToRules(escapeUnprintable: Boolean): String = baseToRules(escapeUnprintable)
    open fun getElements(): Array<Transliterator> = if(this is CompoundTransliterator) Array(getCount()) { getTransliterator(it) } else arrayOf(this)
    fun getSourceSet(): UnicodeSet = UnicodeSet().also { addSourceTargetSet(getFilterAsUnicodeSet(UnicodeSet.ALL_CODE_POINTS),it,UnicodeSet()) }
    protected open fun handleGetSourceSet(): UnicodeSet = UnicodeSet()
    open fun getTargetSet(): UnicodeSet = UnicodeSet().also { addSourceTargetSet(getFilterAsUnicodeSet(UnicodeSet.ALL_CODE_POINTS),UnicodeSet(),it) }
    open fun addSourceTargetSet(inputFilter: UnicodeSet, sourceSet: UnicodeSet, targetSet: UnicodeSet) {
        val filter=getFilterAsUnicodeSet(inputFilter)
        val temp=UnicodeSet(handleGetSourceSet()).retainAll(filter)
        sourceSet.addAll(temp)
        for(s in temp) { val t=transliterate(s);if(s!=t)targetSet.addAll(t) }
    }
    open fun getFilterAsUnicodeSet(externalFilter: UnicodeSet): UnicodeSet = execution.getFilterAsUnicodeSet(externalFilter)
    fun getInverse(): Transliterator = getInstance(identifier,REVERSE)
    override fun transform(source: String): String = transliterate(source)
    fun interface Factory { fun getInstance(id: String): Transliterator? }

    companion object {
        const val FORWARD = 0
        const val REVERSE = 1
        internal const val ID_DELIM = ';'
        internal const val ID_SEP = '-'
        internal const val VARIANT_SEP = '/'
        private class FunctionAdapter(val value: Transliterator) : TransliterationFunction {
            override fun getID(): String = sourceValue(value.getID())
            override fun setFilter(filter: UnicodeSet) = value.setFilter(filter)
            override fun getTargetSet(): UnicodeSet = value.getTargetSet()
            override fun transliterate(text: Replaceable, start: Int, limit: Int): Int = value.transliterate(text,start,limit)
        }
        @Suppress("UNCHECKED_CAST") private fun <T> sourceValue(value: Any?): T = value as T
        internal val parserFactory = TransliterationFactory { basic, canonical -> getBasicInstance(basic,canonical)?.let(::FunctionAdapter) }
        private val registry = TransliteratorRegistryCore(object : TransliteratorRegistryCore.Host<Transliterator> {
            override val parserFactory get() = Companion.parserFactory
            override fun ruleBased(id: String, data: TransliterationData, filter: UnicodeSet?): Transliterator = RuleBasedTransliterator(id,data,filter)
            override fun compound(elements: List<Transliterator>, anonymousPasses: Int): Transliterator = CompoundTransliterator(elements,anonymousPasses)
            override fun getInstance(id: String): Transliterator = Companion.getInstance(id)
            override fun setID(instance: Transliterator, id: String) = instance.setID(id)
            override fun setFilter(instance: Transliterator, filter: UnicodeSet) = instance.setFilter(filter)
            override fun cloneStored(instance: Transliterator): Transliterator = when(instance) {
                is AnyTransliterator -> instance.safeClone()
                is RuleBasedTransliterator -> instance.safeClone()
                is CompoundTransliterator -> instance.safeClone()
                else -> instance
            }
        })
        private val displayNames = HashMap<CaseInsensitiveString,String>()
        private val displayLock = createTextTrieMapLock(displayNames)
        init {
            TransliterationResourceBootstrap().register(registry)
            registerSpecialInverse("Null","Null",false)
            registry.putClass("Any-Null",{ NullTransliterator() },true)
            RemoveTransliterator.register()
            EscapeTransliterator.register()
            UnescapeTransliterator.register()
            CaseTransliterator.register()
            UnicodeNameTransliterator.register()
            NameUnicodeTransliterator.register()
            NormalizationTransliterator.register()
            BreakTransliterator.register()
            AnyTransliterator.register()
        }
        @JvmStatic fun getInstance(id: String?): Transliterator = getInstance(id,FORWARD)
        @JvmStatic fun getInstance(id: String?, direction: Int): Transliterator {
            val canonical=StringBuilder()
            val ids=mutableListOf<TransliteratorIDParser.SingleID>()
            val filter=arrayOfNulls<UnicodeSet>(1)
            if(!TransliteratorIDParser.parseCompoundID(id!!,direction,canonical,ids,filter))throw IllegalArgumentException("Invalid ID $id")
            val elements=TransliteratorIDParser.instantiateList(ids,parserFactory).map { (it as FunctionAdapter).value }
            val result=if(ids.size>1 || canonical.indexOf(";")>=0)CompoundTransliterator(elements) else elements[0]
            result.setID(canonical.toString())
            filter[0]?.let {result.setFilter(it)}
            return result
        }
        internal fun getBasicInstance(id: String, canonical: String?): Transliterator? {
            val alias=StringBuilder()
            var result=registry.get(id,alias)
            if(alias.isNotEmpty())result=getInstance(alias.toString(),FORWARD)
            if(result!=null && canonical!=null)result.setID(canonical)
            return result
        }
        @JvmStatic fun createFromRules(id: String?, rules: String?, direction: Int): Transliterator {
            val parser=TransliteratorParser(parserFactory)
            if(rules==null)parser.parseLines(emptyArray(),direction)else parser.parse(rules,direction)
            val result: Transliterator
            when {
                parser.idBlockVector.isEmpty() && parser.dataVector.isEmpty() -> result=NullTransliterator()
                parser.idBlockVector.isEmpty() && parser.dataVector.size==1 -> result=RuleBasedTransliterator(id,parser.dataVector[0],parser.compoundFilter)
                parser.idBlockVector.size==1 && parser.dataVector.isEmpty() -> {
                    result=getInstance((parser.compoundFilter?.let {it.toPattern(false)+";"} ?: "")+parser.idBlockVector[0])
                    result.setID(id)
                }
                else -> {
                    val elements=mutableListOf<Transliterator>()
                    var pass=1
                    for(i in 0 until maxOf(parser.idBlockVector.size,parser.dataVector.size)) {
                        if(i<parser.idBlockVector.size && parser.idBlockVector[i].isNotEmpty()) {
                            val block=parser.idBlockVector[i]
                            if(getInstance(block) !is NullTransliterator)elements.add(getInstance(block))
                        }
                        if(i<parser.dataVector.size)elements.add(RuleBasedTransliterator("%Pass${pass++}",parser.dataVector[i],null))
                    }
                    result=CompoundTransliterator(elements,pass-1)
                    result.setID(id)
                    parser.compoundFilter?.let {result.setFilter(it)}
                }
            }
            return result
        }
        @JvmStatic fun registerFactory(id: String, factory: Factory?) = registry.putFactory(id,factory?.let { supplied -> { requested: String -> supplied.getInstance(requested) } },true)
        @JvmStatic fun registerInstance(instance: Transliterator) = registerInstance(instance,true)
        internal fun registerInstance(instance: Transliterator, visible: Boolean) = registry.putInstance(instance.getID()!!,instance,visible)
        @JvmStatic fun registerAlias(aliasID: String, realID: String?) = registry.putNullableAlias(aliasID,realID,true)
        internal fun registerSpecialInverse(target: String?, inverse: String?, bidirectional: Boolean) = TransliteratorIDParser.registerSpecialInverse(target,inverse,bidirectional)
        internal fun registerConstructor(id: String, constructor: ()->Transliterator?, displayName: String?) {
            registry.putClass(id,constructor,true)
            if(displayName!=null)displayLock.withLock {displayNames[CaseInsensitiveString(id)]=displayName}
        }
        @JvmStatic fun unregister(id: String) { displayLock.withLock {displayNames.remove(CaseInsensitiveString(id))};registry.remove(id) }
        @JvmStatic fun getAvailableIDs(): Enumeration<String> = registry.getAvailableIDs()
        @JvmStatic fun getAvailableSources(): Enumeration<String> = registry.getAvailableSources()
        @JvmStatic fun getAvailableTargets(source: String): Enumeration<String> = registry.getAvailableTargets(source)
        @JvmStatic fun getAvailableVariants(source: String, target: String): Enumeration<String> = registry.getAvailableVariants(source,target)
        @JvmStatic fun registerAny() = AnyTransliterator.register()
        @JvmStatic fun getDisplayName(id: String): String = getDisplayName(id,ULocale.getDefault(ULocale.Category.DISPLAY))
        @JvmStatic fun getDisplayName(id: String, locale: ULocale?): String {
            val bundle=UResourceBundle.getBundleInstance(ICUData.ICU_TRANSLIT_BASE_NAME,locale)
            val stv=TransliteratorIDParser.IDtoSTV(id)
            val canonical=stv[0]+"-"+stv[1]+(if(!stv[2].isNullOrEmpty())"/"+stv[2] else "")
            displayLock.withLock {displayNames[CaseInsensitiveString(canonical)]}?.let {return it}
            try {return bundle.getString("%Translit%%$canonical")}catch(_:MissingResourceException) { }
            try {
                val format=MessageFormat(bundle.getString("TransliteratorNamePattern"))
                val args=arrayOf<Any?>(2,stv[0],stv[1])
                for(j in 1..2)try {args[j]=bundle.getString("%Translit%"+args[j])}catch(_:MissingResourceException) { }
                val name=format.format(args,StringBuilder(),null).toString()
                return if(stv[2]!!.isNotEmpty())name+"/"+stv[2] else name
            }catch(_:MissingResourceException) { }
            throw RuntimeException()
        }
    }
}

package io.github.heapy.icu4k.text

internal class RegistryFixtureFunction(var idValue: String, val data: TransliterationData? = null, val elements: List<RegistryFixtureFunction>? = null, passes: Int = 0) : TransliterationFunction {
        var filterValue: UnicodeSet? = null
        val execution: TransliteratorExecutionCore = TransliteratorExecutionCore(object : TransliteratorExecutionCore.Input {
            override val id: String get() = idValue
            override var filter: UnicodeSet? get() = this@RegistryFixtureFunction.filterValue; set(value) { this@RegistryFixtureFunction.filterValue = value }
            override fun handleTransliterate(text: Replaceable, position: TransliterationPosition, incremental: Boolean) {
                when {
                    data != null -> data.ruleSet.handleTransliterate(text, position, incremental)
                    compound != null -> compound.handleTransliterate(text, position, incremental)
                    else -> position.start = position.limit
                }
            }
        })
        val compound: CompoundTransliteratorCore? = elements?.let { parts -> CompoundTransliteratorCore(parts.map { part -> object : CompoundTransliteratorCore.Element {
            override val id get() = part.idValue
            override val maximumContextLength get() = part.data?.ruleSet?.getMaximumContextLength() ?: 0
            override fun filteredTransliterate(text: Replaceable, pos: TransliterationPosition, incremental: Boolean) = part.execution.filteredTransliterate(text,pos,incremental)
            override fun toRules(escapeUnprintable: Boolean) = part.rules(escapeUnprintable)
            override fun baseToRules(escapeUnprintable: Boolean) = part.execution.baseToRules(escapeUnprintable)
            override fun addSourceTargetSet(filter: UnicodeSet, sourceSet: UnicodeSet, targetSet: UnicodeSet) = Unit
        } }.toTypedArray(), passes, { filterValue }, { it }) }
        override fun getID() = idValue
        override fun setFilter(filter: UnicodeSet) { execution.setFilter(filter) }
        override fun getTargetSet() = UnicodeSet()
        override fun transliterate(text: Replaceable, start: Int, limit: Int) = execution.transliterate(text,start,limit)
        fun rules(escape: Boolean): String = data?.ruleSet?.toRules(escape) ?: compound?.toRules(escape) ?: execution.baseToRules(escape)
    }
internal object RegistryFixtureHost : TransliteratorRegistryCore.Host<RegistryFixtureFunction> {
        init { TransliteratorIDParser.registerSpecialInverse("Null", "Null", false) }
        override val parserFactory = TransliterationFactory { basic, canonical -> RegistryFixtureFunction(canonical ?: basic) }
        override fun ruleBased(id: String, data: TransliterationData, filter: UnicodeSet?) = RegistryFixtureFunction(id,data).also { it.filterValue=filter }
        override fun compound(elements: List<RegistryFixtureFunction>, anonymousPasses: Int) = RegistryFixtureFunction("",elements=elements,passes=anonymousPasses)
        override fun getInstance(id: String): RegistryFixtureFunction {
            val canonical = id.removeSuffix(";")
            check(canonical == "Any-Null" || canonical == "Null") { id }
            return RegistryFixtureFunction(canonical)
        }
        override fun setID(instance: RegistryFixtureFunction, id: String) { instance.idValue=id }
        override fun cloneStored(instance: RegistryFixtureFunction) = if(instance.data == null && instance.elements == null) instance else RegistryFixtureFunction(instance.idValue,instance.data,instance.elements).also { it.filterValue=instance.filterValue?.let(::UnicodeSet) }
    }

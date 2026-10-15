// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.units.UnitIdentifierData.Constants

/** Shared identifier model and public MeasureUnit build bridge. */
open class MeasureUnitImpl {
    open fun build(): MeasureUnit = MeasureUnit.fromMeasureUnitImpl(this)

    private var identifier: String? = null
    private var complexity: UnitComplexity? = UnitComplexity.SINGLE
    private var constantDenominator = 0L
    private val singleUnits = ArrayList<SingleUnitImpl>()
    constructor()
    constructor(unit: SingleUnitImpl?) { appendSingleUnit(unit) }
    open fun copy(): MeasureUnitImpl = MeasureUnitImpl().also { out ->
        out.identifier = identifier; out.complexity = complexity; out.constantDenominator = constantDenominator
        for (unit in singleUnits) out.singleUnits.add(unit.copy())
    }
    open fun copyAndSimplify(): MeasureUnitImpl {
        val out = MeasureUnitImpl()
        for (unit in getSingleUnits()) {
            var exists = false
            for (old in out.getSingleUnits()) {
                if (old.getSimpleUnitID()!!.compareTo(unit.getSimpleUnitID()!!) == 0 && old.getPrefix()!!.getIdentifier().compareTo(unit.getPrefix()!!.getIdentifier()) == 0) {
                    old.setDimensionality(old.getDimensionality() + unit.getDimensionality()); exists = true; break
                }
            }
            if (!exists) out.appendSingleUnit(unit)
        }
        return out
    }
    open fun getSingleUnits(): ArrayList<SingleUnitImpl> = singleUnits
    open fun takeReciprocal() { identifier = null; for (unit in singleUnits) unit.setDimensionality(-unit.getDimensionality()) }
    open fun applyDimensionality(power: Int) { for (unit in singleUnits) unit.setDimensionality(unit.getDimensionality() * power) }
    open fun appendSingleUnit(unit: SingleUnitImpl?): Boolean {
        identifier = null
        if (unit == null) return false
        for (old in singleUnits) if (old.isCompatibleWith(unit)) { old.setDimensionality(old.getDimensionality() + unit.getDimensionality()); return false }
        singleUnits.add(unit.copy())
        if (singleUnits.size > 1 && complexity == UnitComplexity.SINGLE) setComplexity(UnitComplexity.COMPOUND)
        return true
    }
    open fun getSingleUnitImpl(): SingleUnitImpl = when (singleUnits.size) { 0 -> SingleUnitImpl(); 1 -> singleUnits[0].copy(); else -> throw UnsupportedOperationException() }
    open fun getIdentifier(): String? = identifier
    open fun getComplexity(): UnitComplexity? = complexity
    open fun setComplexity(value: UnitComplexity?) { complexity = value }
    open fun getConstantDenominator(): Long = constantDenominator
    open fun setConstantDenominator(value: Long) { constantDenominator = value }
    internal class MeasureUnitImplComparator(private val conversionRates: UnitConversionData) : Comparator<MeasureUnitImpl> {
        override fun compare(a: MeasureUnitImpl, b: MeasureUnitImpl): Int {
            val first = conversionRates.getSpecialMappingName(a)
            val second = conversionRates.getSpecialMappingName(b)
            if (first != null || second != null) {
                if (first == null) return -1
                if (second == null) return 1
                return first.compareTo(second)
            }
            return conversionRates.getFactorToBase(a).getConversionRate().compareTo(conversionRates.getFactorToBase(b).getConversionRate())
        }
    }
    internal class MeasureUnitImplWithIndexComparator(conversionRates: UnitConversionData) : Comparator<MeasureUnitImplWithIndex> {
        private val comparator = MeasureUnitImplComparator(conversionRates)
        override fun compare(a: MeasureUnitImplWithIndex, b: MeasureUnitImplWithIndex): Int = comparator.compare(a.unitImpl, b.unitImpl)
    }
    class MeasureUnitImplWithIndex internal constructor(internal var index: Int, internal var unitImpl: MeasureUnitImpl)
    open fun extractIndividualUnitsWithIndices(): ArrayList<MeasureUnitImplWithIndex> = if (getComplexity() == UnitComplexity.MIXED) ArrayList(getSingleUnits().mapIndexed { i, unit -> MeasureUnitImplWithIndex(i, MeasureUnitImpl(unit)) }) else arrayListOf(MeasureUnitImplWithIndex(0, copy()))
    private fun constantString(value: Long): String {
        val text = value.toString()
        if (value <= 1000) return text
        val zeros = text.count { it == '0' }
        return if (zeros == text.length - 1 && text[0] == '1') "1e$zeros" else text
    }
    open fun serialize() {
        if (getSingleUnits().isEmpty() && constantDenominator == 0L) return
        if (complexity == UnitComplexity.COMPOUND) getSingleUnits().sortWith { a, b -> a.compareTo(b) }
        val out = StringBuilder(); var beforePer = true; var firstNegative = false; var constantAdded = false
        for (unit in getSingleUnits()) {
            if (beforePer && unit.getDimensionality() < 0) { beforePer = false; firstNegative = true }
            else if (unit.getDimensionality() < 0) firstNegative = false
            if (firstNegative && constantDenominator > 0) { out.append("-per-").append(constantString(constantDenominator)); firstNegative = false; constantAdded = true }
            if (getComplexity() == UnitComplexity.MIXED) { if (out.isNotEmpty()) out.append("-and-") }
            else if (firstNegative) out.append(if (out.isEmpty()) "per-" else "-per-")
            else if (out.isNotEmpty()) out.append('-')
            out.append(unit.getNeutralIdentifier())
        }
        if (constantDenominator > 0 && !constantAdded) out.append("-per-").append(constantString(constantDenominator))
        identifier = out.toString()
    }
    enum class CompoundPart(private val index: Int) {
        PER(0), TIMES(1), AND(2);
        fun getTrieIndex(): Int = index + Constants.kCompoundPartOffset
        fun getValue(): Int = index
        companion object { fun getCompoundPartFromTrieIndex(index: Int): CompoundPart = when (index - Constants.kCompoundPartOffset) { 0 -> PER; 1 -> TIMES; 2 -> AND; else -> throw AssertionError("CompoundPart index must be 0, 1 or 2") } }
    }
    enum class PowerPart(private val power: Int) {
        P2(2), P3(3), P4(4), P5(5), P6(6), P7(7), P8(8), P9(9), P10(10), P11(11), P12(12), P13(13), P14(14), P15(15);
        fun getTrieIndex(): Int = power + Constants.kPowerPartOffset
        fun getValue(): Int = power
        companion object { fun getPowerFromTrieIndex(index: Int): Int = index - Constants.kPowerPartOffset }
    }
    enum class InitialCompoundPart {
        INITIAL_COMPOUND_PART_PER;
        fun getTrieIndex(): Int = Constants.kInitialCompoundPartOffset
        fun getValue(): Int = 0
        companion object { fun getInitialCompoundPartFromTrieIndex(index: Int): InitialCompoundPart = if (index == Constants.kInitialCompoundPartOffset) INITIAL_COMPOUND_PART_PER else throw IllegalArgumentException("Incorrect trieIndex") }
    }
    class UnitsParser private constructor(private var source: String) {
        private val trie = savedTrie.clone()
        private var index = 0
        private var afterPer = false
        private var justAfterPer = false
        private var sawAnd = false
        private fun parse(): MeasureUnitImpl {
            val out = MeasureUnitImpl()
            while (hasNext()) {
                sawAnd = false
                val (unit, constant) = nextSingleUnit()
                if (unit == null) {
                    if (out.getConstantDenominator() != 0L) throw IllegalArgumentException("Can't have multiple unit constants")
                    out.setConstantDenominator(constant!!); out.setComplexity(UnitComplexity.COMPOUND); continue
                }
                val added = out.appendSingleUnit(unit)
                if (sawAnd && !added) throw IllegalArgumentException("Two similar units are not allowed in a mixed unit.")
                if (out.singleUnits.size >= 2) {
                    val type = if (sawAnd) UnitComplexity.MIXED else UnitComplexity.COMPOUND
                    if (out.singleUnits.size == 2) out.setComplexity(type)
                    else if (out.getComplexity() != type) throw IllegalArgumentException("Can't have mixed compound units")
                }
            }
            if (out.singleUnits.isEmpty()) throw IllegalArgumentException("Error in parsing a unit identifier.")
            return out
        }
        private fun nextSingleUnit(): Pair<SingleUnitImpl?, Long?> {
            val out = SingleUnitImpl(); var state = 0; val atStart = index == 0
            var token = nextToken(); justAfterPer = false
            if (token.getType() == Token.Type.TYPE_ALIAS) { processAlias(token); token = nextToken() }
            if (atStart) {
                if (token.getType() == Token.Type.TYPE_UNIT_CONSTANT) throw IllegalArgumentException("Unit constant cannot be the first token")
                if (token.getType() == Token.Type.TYPE_INITIAL_COMPOUND_PART) {
                    afterPer = true; justAfterPer = true; out.setDimensionality(-1); token = nextToken()
                }
            } else {
                if (token.getType() != Token.Type.TYPE_COMPOUND_PART) throw IllegalArgumentException("token type must be TYPE_COMPOUND_PART")
                when (CompoundPart.getCompoundPartFromTrieIndex(token.getMatch())) {
                    CompoundPart.PER -> {
                        if (sawAnd) throw IllegalArgumentException("Mixed compound units not yet supported")
                        afterPer = true; justAfterPer = true; out.setDimensionality(-1)
                    }
                    CompoundPart.TIMES -> if (afterPer) out.setDimensionality(-1)
                    CompoundPart.AND -> { if (afterPer) throw IllegalArgumentException("Can't start with \"-and-\", and mixed compound units"); sawAnd = true }
                }
                token = nextToken()
            }
            if (token.getType() == Token.Type.TYPE_UNIT_CONSTANT) {
                if (!justAfterPer) throw IllegalArgumentException("Unit constant cannot be the first token")
                return null to token.getConstantDenominator()
            }
            while (true) {
                when (token.getType()) {
                    Token.Type.TYPE_POWER_PART -> { if (state != 0) throw IllegalArgumentException(); out.setDimensionality(out.getDimensionality() * token.getPower()); state = 1 }
                    Token.Type.TYPE_PREFIX -> { if (state == 2) throw IllegalArgumentException(); out.setPrefix(token.getPrefix()); state = 2 }
                    Token.Type.TYPE_SIMPLE_UNIT -> { out.setSimpleUnit(token.getSimpleUnitIndex(), UnitIdentifierData.getSimpleUnits()); return out to null }
                    Token.Type.TYPE_ALIAS -> processAlias(token)
                    else -> throw IllegalArgumentException()
                }
                if (!hasNext()) throw IllegalArgumentException("We ran out of tokens before finding a complete single unit.")
                token = nextToken()
            }
        }
        private fun hasNext(): Boolean = index < source.length
        private fun nextToken(): Token {
            trie.reset(); var match = -1; var previous = -1; val saved = index
            while (index < source.length) {
                val result = trie.next(source[index++].code)
                if (result == BytesTrie.Result.NO_MATCH) break
                if (result == BytesTrie.Result.NO_VALUE) continue
                match = trie.getValue(); previous = index
                if (result == BytesTrie.Result.FINAL_VALUE) break
                if (result != BytesTrie.Result.INTERMEDIATE_VALUE) throw IllegalArgumentException("Result must have an intermediate value")
            }
            if (match < 0) {
                if (!justAfterPer) throw IllegalArgumentException("Encountered unknown token starting at index $previous")
                val end = source.indexOf('-', saved).let { if (it == -1) source.length else it }
                val constant = source.substring(saved, end); index = end
                return Token.tokenWithConstant(constant)
            }
            index = previous; return Token(match.toLong())
        }
        private fun processAlias(token: Token) { source = UnitIdentifierData.getReplacementFromAliasIndex(token.getAliasIndex()) + source.substring(index); index = 0 }
        class Token private constructor(private val match: Long, private val type: Type) {
            constructor(match: Long) : this(match, calculateType(match))
            enum class Type { TYPE_UNDEFINED, TYPE_PREFIX, TYPE_COMPOUND_PART, TYPE_INITIAL_COMPOUND_PART, TYPE_POWER_PART, TYPE_SIMPLE_UNIT, TYPE_UNIT_CONSTANT, TYPE_ALIAS }
            fun getType(): Type = type
            fun getPrefix(): MeasurePrefix = unitArrayElement(prefixValues, match.toInt() - Constants.kPrefixOffset)
            fun getConstantDenominator(): Long = match
            fun getMatch(): Int = match.toInt()
            fun getAliasIndex(): Int = match.toInt() - Constants.kAliasOffset
            fun getInitialCompoundPart(): InitialCompoundPart = InitialCompoundPart.getInitialCompoundPartFromTrieIndex(match.toInt())
            fun getPower(): Int = PowerPart.getPowerFromTrieIndex(match.toInt())
            fun getSimpleUnitIndex(): Int = match.toInt() - Constants.kSimpleUnitOffset
            companion object {
                fun tokenWithConstant(constant: String?): Token = Token(parseUnitConstant(constant!!), Type.TYPE_UNIT_CONSTANT)
                private fun calculateType(match: Long): Type = when {
                    match <= 0 -> throw AssertionError("fMatch must have a positive value")
                    match < Constants.kCompoundPartOffset -> Type.TYPE_PREFIX
                    match < Constants.kInitialCompoundPartOffset -> Type.TYPE_COMPOUND_PART
                    match < Constants.kPowerPartOffset -> Type.TYPE_INITIAL_COMPOUND_PART
                    match < Constants.kSimpleUnitOffset -> Type.TYPE_POWER_PART
                    match < Constants.kAliasOffset -> Type.TYPE_SIMPLE_UNIT
                    else -> Type.TYPE_ALIAS
                }
            }
        }
        companion object {
            private val prefixValues = MeasurePrefix.entries.toTypedArray()
            private val savedTrie: CharsTrie = CharsTrieBuilder().also { builder ->
                for (prefix in prefixValues) builder.add(prefix.getIdentifier(), prefix.ordinal + Constants.kPrefixOffset)
                builder.add("-per-", CompoundPart.PER.getTrieIndex()); builder.add("-", CompoundPart.TIMES.getTrieIndex()); builder.add("-and-", CompoundPart.AND.getTrieIndex())
                builder.add("per-", InitialCompoundPart.INITIAL_COMPOUND_PART_PER.getTrieIndex())
                builder.add("square-", PowerPart.P2.getTrieIndex()); builder.add("cubic-", PowerPart.P3.getTrieIndex())
                for (power in PowerPart.entries) builder.add("pow${power.getValue()}-", power.getTrieIndex())
                for ((i, unit) in UnitIdentifierData.getSimpleUnits().withIndex()) builder.add(unit, i + Constants.kSimpleUnitOffset)
                for ((i, alias) in UnitIdentifierData.getAliases().withIndex()) builder.add(alias.alias!!, i + Constants.kAliasOffset)
            }.build(StringTrieBuilder.Option.FAST)
            fun parseForIdentifier(identifier: String?): MeasureUnitImpl? = if (identifier.isNullOrEmpty()) null else UnitsParser(identifier).parse()
        }
    }
    companion object {
        fun forIdentifier(identifier: String?): MeasureUnitImpl? = UnitsParser.parseForIdentifier(identifier)
        fun forCurrencyCode(code: String?): MeasureUnitImpl = MeasureUnitImpl().also { it.identifier = code }
    }
}
internal expect fun parseUnitConstant(value: String): Long

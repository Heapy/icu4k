// © 2022 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.locale.localeReadOnlyList
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Immutable CLDR display options with the source builder and identifier enums. */
class DisplayOptions private constructor(
    private val grammaticalCase: GrammaticalCase?,
    private val nounClass: NounClass?,
    private val pluralCategory: PluralCategory?,
    private val capitalization: Capitalization?,
    private val nameStyle: NameStyle?,
    private val displayLength: DisplayLength?,
    private val substituteHandling: SubstituteHandling?
) {
    fun getGrammaticalCase(): GrammaticalCase? = grammaticalCase
    fun getNounClass(): NounClass? = nounClass
    fun getPluralCategory(): PluralCategory? = pluralCategory
    fun getCapitalization(): Capitalization? = capitalization
    fun getNameStyle(): NameStyle? = nameStyle
    fun getDisplayLength(): DisplayLength? = displayLength
    fun getSubstituteHandling(): SubstituteHandling? = substituteHandling
    fun copyToBuilder(): Builder = Builder.create(this)

    open class Builder private constructor() {
        private var grammaticalCase: GrammaticalCase? = GrammaticalCase.UNDEFINED
        private var nounClass: NounClass? = NounClass.UNDEFINED
        private var pluralCategory: PluralCategory? = PluralCategory.UNDEFINED
        private var capitalization: Capitalization? = Capitalization.UNDEFINED
        private var nameStyle: NameStyle? = NameStyle.UNDEFINED
        private var displayLength: DisplayLength? = DisplayLength.UNDEFINED
        private var substituteHandling: SubstituteHandling? = SubstituteHandling.UNDEFINED
        open fun setGrammaticalCase(value: GrammaticalCase?): Builder { grammaticalCase = value; return this }
        open fun setNounClass(value: NounClass?): Builder { nounClass = value; return this }
        open fun setPluralCategory(value: PluralCategory?): Builder { pluralCategory = value; return this }
        open fun setCapitalization(value: Capitalization?): Builder { capitalization = value; return this }
        open fun setNameStyle(value: NameStyle?): Builder { nameStyle = value; return this }
        open fun setDisplayLength(value: DisplayLength?): Builder { displayLength = value; return this }
        open fun setSubstituteHandling(value: SubstituteHandling?): Builder { substituteHandling = value; return this }
        open fun build(): DisplayOptions = DisplayOptions(grammaticalCase, nounClass, pluralCategory, capitalization, nameStyle, displayLength, substituteHandling)
        internal companion object {
            fun create(options: DisplayOptions? = null): Builder = Builder().also { builder ->
                if (options != null) {
                    builder.grammaticalCase = options.grammaticalCase
                    builder.nounClass = options.nounClass
                    builder.pluralCategory = options.pluralCategory
                    builder.capitalization = options.capitalization
                    builder.nameStyle = options.nameStyle
                    builder.displayLength = options.displayLength
                    builder.substituteHandling = options.substituteHandling
                }
            }
        }
    }

    companion object { @JvmStatic fun builder(): Builder = Builder.create() }

    enum class NounClass(private val identifier: String) {
        UNDEFINED("undefined"),
        OTHER("other"),
        NEUTER("neuter"),
        FEMININE("feminine"),
        MASCULINE("masculine"),
        ANIMATE("animate"),
        INANIMATE("inanimate"),
        PERSONAL("personal"),
        COMMON("common");
        fun getIdentifier(): String = identifier
        companion object {
            @JvmField val VALUES: List<NounClass> = localeReadOnlyList(entries.toList())
            @JvmStatic fun fromIdentifier(identifier: String?): NounClass {
                if (identifier == null) return UNDEFINED
                for (value in VALUES) if (identifier.equals(value.getIdentifier())) return value
                return UNDEFINED
            }
        }
    }

    enum class NameStyle {
        UNDEFINED,
        STANDARD_NAMES,
        DIALECT_NAMES;
        companion object {
            @JvmField val VALUES: List<NameStyle> = localeReadOnlyList(entries.toList())
        }
    }

    enum class SubstituteHandling {
        UNDEFINED,
        SUBSTITUTE,
        NO_SUBSTITUTE;
        companion object {
            @JvmField val VALUES: List<SubstituteHandling> = localeReadOnlyList(entries.toList())
        }
    }

    enum class DisplayLength {
        UNDEFINED,
        LENGTH_FULL,
        LENGTH_SHORT;
        companion object {
            @JvmField val VALUES: List<DisplayLength> = localeReadOnlyList(entries.toList())
        }
    }

    enum class Capitalization {
        UNDEFINED,
        BEGINNING_OF_SENTENCE,
        MIDDLE_OF_SENTENCE,
        STANDALONE,
        UI_LIST_OR_MENU;
        companion object {
            @JvmField val VALUES: List<Capitalization> = localeReadOnlyList(entries.toList())
        }
    }

    enum class PluralCategory(private val identifier: String) {
        UNDEFINED("undefined"),
        ZERO("zero"),
        ONE("one"),
        TWO("two"),
        FEW("few"),
        MANY("many"),
        OTHER("other");
        fun getIdentifier(): String = identifier
        companion object {
            @JvmField val VALUES: List<PluralCategory> = localeReadOnlyList(entries.toList())
            @JvmStatic fun fromIdentifier(identifier: String?): PluralCategory {
                if (identifier == null) return UNDEFINED
                for (value in VALUES) if (identifier.equals(value.getIdentifier())) return value
                return UNDEFINED
            }
        }
    }

    enum class GrammaticalCase(private val identifier: String) {
        UNDEFINED("undefined"),
        ABLATIVE("ablative"),
        ACCUSATIVE("accusative"),
        COMITATIVE("comitative"),
        DATIVE("dative"),
        ERGATIVE("ergative"),
        GENITIVE("genitive"),
        INSTRUMENTAL("instrumental"),
        LOCATIVE("locative"),
        LOCATIVE_COPULATIVE("locative_copulative"),
        NOMINATIVE("nominative"),
        OBLIQUE("oblique"),
        PREPOSITIONAL("prepositional"),
        SOCIATIVE("sociative"),
        VOCATIVE("vocative");
        fun getIdentifier(): String = identifier
        companion object {
            @JvmField val VALUES: List<GrammaticalCase> = localeReadOnlyList(entries.toList())
            @JvmStatic fun fromIdentifier(identifier: String?): GrammaticalCase {
                if (identifier == null) return UNDEFINED
                for (value in VALUES) if (identifier.equals(value.getIdentifier())) return value
                return UNDEFINED
            }
        }
    }

}

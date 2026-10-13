// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.impl.RuleCharacterIterator
import io.github.heapy.icu4k.impl.CharacterPropertiesImpl
import io.github.heapy.icu4k.impl.UPropertyAliases
import io.github.heapy.icu4k.impl.ICUNumberParsing
import io.github.heapy.icu4k.util.VersionInfo
import kotlin.concurrent.atomics.AtomicReference
import io.github.heapy.icu4k.util.Freezable
import io.github.heapy.icu4k.util.OutputInt
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.BMPSet
import io.github.heapy.icu4k.impl.UnicodeSetStringSpan

/** ICU Unicode sets, including recursive patterns, strings and property expressions. */
class UnicodeSet() : UnicodeFilter(), Iterable<String>, Comparable<UnicodeSet>, Freezable<UnicodeSet> {
    enum class SpanCondition { NOT_CONTAINED, CONTAINED, SIMPLE, CONDITION_COUNT }
    private var list = IntArray(25).also { it[0] = HIGH }
    private var len = 1
    private var bmpSet: BMPSet? = null
    private var stringSpan: UnicodeSetStringSpan? = null
    private var buffer: IntArray? = null
    private var stringElements = ArrayList<String>()
    private var patternCache: String? = null
    constructor(pattern: String) : this() { applyPattern(pattern, null, null, IGNORE_SPACE) }
    constructor(pattern: String, ignoreWhitespace: Boolean) : this() { applyPattern(pattern, null, null, if (ignoreWhitespace) IGNORE_SPACE else 0) }
    constructor(pattern: String, options: Int) : this() { applyPattern(pattern, null, null, options) }
    constructor(pattern: String, position: ParsePosition?, symbols: SymbolTable?) : this() { applyPattern(pattern, position, symbols, IGNORE_SPACE) }
    constructor(pattern: String, position: ParsePosition?, symbols: SymbolTable?, options: Int) : this() { applyPattern(pattern, position, symbols, options) }
    constructor(other: UnicodeSet) : this() { set(other) }
    constructor(start: Int, end: Int) : this() { add(start, end) }
    constructor(vararg pairs: Int) : this() {
        require(pairs.size and 1 == 0) { "Must have even number of integers" }
        list = IntArray(pairs.size + 1); len = list.size
        var last = -1
        var i = 0
        while (i < pairs.size) {
            val start = pairs[i]
            require(last < start) { "Must be monotonically increasing." }
            list[i++] = start
            val limit = pairs[i] + 1
            require(start < limit) { "Must be monotonically increasing." }
            last = limit; list[i++] = limit
        }
        list[i] = HIGH
    }
    fun set(other: UnicodeSet): UnicodeSet { checkFrozen();
        list = other.list.copyOf(other.len); len = other.len
        stringElements = ArrayList(other.stringElements)
        patternCache = other.patternCache
        return this
    }
    fun clear(): UnicodeSet { checkFrozen(); list[0] = HIGH; len = 1; patternCache = null; stringElements.clear(); return this }
    fun compact(): UnicodeSet { checkFrozen(); if (len + 7 < list.size) list = list.copyOf(len); buffer = null; if (stringElements.isEmpty()) stringElements = ArrayList(); return this }
    internal fun maybeOnlyCaseSensitive(src: UnicodeSet): UnicodeSet {
        if (src.size() < 30) return src
        val sensitive = io.github.heapy.icu4k.lang.CharacterProperties.getBinaryPropertySet(UProperty.CASE_SENSITIVE)
        return if (src.hasStrings() || src.getRangeCount() > sensitive.getRangeCount()) sensitive.cloneAsThawed().retainAll(src)
        else src.clone().retainAll(sensitive)
    }
    fun closeOver(attribute: Int): UnicodeSet {
        checkFrozen()
        when (attribute and (CASE_INSENSITIVE or ADD_CASE_MAPPINGS)) {
            CASE_INSENSITIVE -> closeOverCaseInsensitive(false)
            ADD_CASE_MAPPINGS -> closeOverAddCaseMappings()
            SIMPLE_CASE_INSENSITIVE -> closeOverCaseInsensitive(true)
        }
        return this
    }
    private fun closeOverAddCaseMappings() {
        val csp = io.github.heapy.icu4k.impl.UCaseProps.INSTANCE
        val root = io.github.heapy.icu4k.impl.UCaseProps.LOC_ROOT
        val foldSet = UnicodeSet(this)
        val codePoints = maybeOnlyCaseSensitive(this)
        val full = StringBuilder()
        fun addMapping(result: Int) {
            if (result >= 0) {
                if (result > io.github.heapy.icu4k.impl.UCaseProps.MAX_STRING_LENGTH) foldSet.add(result)
                else { foldSet.add(full.toString()); full.setLength(0) }
            }
        }
        for (i in 0 until codePoints.getRangeCount()) {
            for (cp in codePoints.getRangeStart(i)..codePoints.getRangeEnd(i)) {
                addMapping(csp.toFullLower(cp, null, full, root))
                addMapping(csp.toFullTitle(cp, null, full, root))
                addMapping(csp.toFullUpper(cp, null, full, root))
                addMapping(csp.toFullFolding(cp, full, 0))
            }
        }
        if (hasStrings()) {
            // Root's word boundary resource supplies the same titlecasing boundaries as ICU4J.
            val boundaries = RuleBasedBreakIterator.getInstanceFromCompiledRules(
                io.github.heapy.icu4k.impl.ICUBinary.getRequiredData("brkitr/word.brk"))
            val titleIterator = object : io.github.heapy.icu4k.impl.CaseMapImpl.TitleIterator {
                override fun first(): Int = boundaries.first()
                override fun next(): Int = boundaries.next()
            }
            for (str in stringElements) {
                foldSet.add(io.github.heapy.icu4k.impl.CaseMapImpl.toLower(root, 0, str))
                boundaries.setText(str)
                foldSet.add(io.github.heapy.icu4k.impl.CaseMapImpl.toTitle(root, 0, titleIterator, str))
                foldSet.add(io.github.heapy.icu4k.impl.CaseMapImpl.toUpper(root, 0, str))
                foldSet.add(UCharacter.foldCase(str, 0))
            }
        }
        set(foldSet)
    }
    internal fun closeOverCaseInsensitive(simple: Boolean) {
        checkFrozen()
        val csp = io.github.heapy.icu4k.impl.UCaseProps.INSTANCE
        val foldSet = UnicodeSet(this)
        if (!simple && foldSet.hasStrings()) foldSet.stringElements.clear()
        val codePoints = maybeOnlyCaseSensitive(this)
        for (i in 0 until codePoints.getRangeCount()) {
            for (cp in codePoints.getRangeStart(i)..codePoints.getRangeEnd(i)) {
                if (simple) csp.addSimpleCaseClosure(cp, foldSet) else csp.addCaseClosure(cp, foldSet)
            }
        }
        if (hasStrings()) {
            val buffer = if (simple) StringBuilder() else null
            for (s in stringElements) {
                if (simple) {
                    if (scfString(s, buffer!!)) foldSet.remove(s).add(buffer)
                } else {
                    val folded = UCharacter.foldCase(s, 0)
                    if (!csp.addStringCaseClosure(folded, foldSet)) foldSet.add(folded)
                }
            }
        }
        set(foldSet)
    }
    fun applyPattern(pattern: String): UnicodeSet { checkFrozen(); return applyPattern(pattern, null, null, IGNORE_SPACE) }
    fun applyPattern(pattern: String, ignoreWhitespace: Boolean): UnicodeSet {
        checkFrozen(); return applyPattern(pattern, null, null, if (ignoreWhitespace) IGNORE_SPACE else 0)
    }
    fun applyPattern(pattern: String, options: Int): UnicodeSet { checkFrozen(); return applyPattern(pattern, null, null, options) }
    fun applyPattern(pattern: String, position: ParsePosition?, symbols: SymbolTable?, options: Int): UnicodeSet {
        val pos = position ?: ParsePosition(0)
        val rebuilt = StringBuilder()
        val chars = RuleCharacterIterator(pattern, symbols, pos)
        applyPattern(chars, symbols, rebuilt, options, 0)
        if (chars.inVariable()) syntaxError(chars, "Extra chars in variable value")
        patternCache = rebuilt.toString()
        if (position == null) {
            val index = if (options and IGNORE_SPACE != 0) PatternProps.skipWhiteSpace(pattern, pos.index) else pos.index
            if (index != pattern.length) throw IllegalArgumentException("Parse of \"$pattern\" failed at $index")
        }
        return this
    }
    /** Original recursive expression parser. */
    internal fun applyPattern(chars: RuleCharacterIterator, symbols: SymbolTable?, rebuilt: Appendable, options: Int, depth: Int) {
        if (depth > 100) syntaxError(chars, "Pattern nested too deeply")
        var opts = RuleCharacterIterator.PARSE_VARIABLES or RuleCharacterIterator.PARSE_ESCAPES
        if (options and IGNORE_SPACE != 0) opts = opts or RuleCharacterIterator.SKIP_WHITESPACE
        val pattern = StringBuilder()
        var usePattern = false
        var scratch: UnicodeSet? = null
        var backup: RuleCharacterIterator.Position? = null
        // lastItem: none, character, set. mode: before [, inside, after ].
        var lastItem = 0
        var lastChar = 0
        var mode = 0
        var op = '\u0000'
        var invert = false
        var lastString: String? = null
        clear()
        while (mode != 2 && !chars.atEnd()) {
            var c = 0
            var literal = false
            var nested: UnicodeSet? = null
            var setMode = 0
            if (resemblesPropertyPattern(chars, opts)) setMode = 2
            else {
                backup = chars.getPos(backup)
                c = chars.next(opts)
                literal = chars.isEscaped()
                if (c == '['.code && !literal) {
                    if (mode == 1) { chars.setPos(backup); setMode = 1 }
                    else {
                        mode = 1; pattern.append('[')
                        backup = chars.getPos(backup)
                        c = chars.next(opts); literal = chars.isEscaped()
                        if (c == '^'.code && !literal) {
                            invert = true; pattern.append('^')
                            backup = chars.getPos(backup)
                            c = chars.next(opts); literal = chars.isEscaped()
                        }
                        if (c == '-'.code) literal = true
                        else { chars.setPos(backup); continue }
                    }
                } else if (symbols != null) {
                    val matcher = symbols.lookupMatcher(c)
                    if (matcher != null) {
                        nested = matcher as? UnicodeSet ?: syntaxError(chars, "Syntax error")
                        setMode = 3
                    }
                }
            }
            if (setMode != 0) {
                if (lastItem == 1) {
                    if (op != '\u0000') syntaxError(chars, "Char expected after operator")
                    add(lastChar); appendPatternCodePoint(pattern, lastChar, false)
                    lastItem = 0; op = '\u0000'
                }
                if (op == '-' || op == '&') pattern.append(op)
                if (nested == null) {
                    if (scratch == null) scratch = UnicodeSet()
                    nested = scratch
                }
                val set = nested!!
                when (setMode) {
                    1 -> set.applyPattern(chars, symbols, pattern, options, depth + 1)
                    2 -> { chars.skipIgnored(opts); set.applyPropertyPattern(chars, pattern, symbols) }
                    3 -> pattern.append(set.toPattern(false))
                }
                usePattern = true
                if (mode == 0) { this.set(set); mode = 2; break }
                when (op) { '-' -> removeAll(set); '&' -> retainAll(set); '\u0000' -> addAll(set) }
                op = '\u0000'; lastItem = 2
                continue
            }
            if (mode == 0) syntaxError(chars, "Missing '['")
            if (!literal) {
                when (c) {
                    ']'.code -> {
                        if (lastItem == 1) { add(lastChar); appendPatternCodePoint(pattern, lastChar, false) }
                        if (op == '-') { add(op.code); pattern.append(op) }
                        else if (op == '&') syntaxError(chars, "Trailing '&'")
                        pattern.append(']'); mode = 2
                        continue
                    }
                    '-'.code -> {
                        if (op == '\u0000') {
                            if (lastItem != 0 || lastString != null) { op = '-'; continue }
                            else {
                                add(c)
                                c = chars.next(opts); literal = chars.isEscaped()
                                if (c == ']'.code && !literal) { pattern.append("-]"); mode = 2; continue }
                            }
                        }
                        syntaxError(chars, "'-' not after char, string, or set")
                    }
                    '&'.code -> {
                        if (lastItem == 2 && op == '\u0000') { op = '&'; continue }
                        syntaxError(chars, "'&' not after set")
                    }
                    '^'.code -> syntaxError(chars, "'^' not after '['")
                    '{'.code -> {
                        if (op != '\u0000' && op != '-') syntaxError(chars, "Missing operand after operator")
                        if (lastItem == 1) { add(lastChar); appendPatternCodePoint(pattern, lastChar, false) }
                        lastItem = 0
                        val buffer = StringBuilder()
                        var ok = false
                        while (!chars.atEnd()) {
                            c = chars.next(opts); literal = chars.isEscaped()
                            if (c == '}'.code && !literal) { ok = true; break }
                            UTF16.appendCodePoint(buffer, c)
                        }
                        if (!ok) syntaxError(chars, "Invalid multicharacter string")
                        val currentString = buffer.toString()
                        if (op == '-') {
                            val first = io.github.heapy.icu4k.lang.CharSequences.getSingleCodePoint(lastString ?: "")
                            val end = io.github.heapy.icu4k.lang.CharSequences.getSingleCodePoint(currentString)
                            if (first != Int.MAX_VALUE && end != Int.MAX_VALUE) add(first, end)
                            else {
                                // Expansion goes directly into the string container, as in ICU4J.
                                val expanded = stringRangeOutput()
                                try { io.github.heapy.icu4k.impl.StringRange.expand(lastString, currentString, true, expanded) }
                                catch (e: Exception) { syntaxError(chars, e.message ?: "null") }
                            }
                            lastString = null; op = '\u0000'
                        } else { add(currentString); lastString = currentString }
                        pattern.append('{'); appendPatternString(pattern, currentString); pattern.append('}')
                        continue
                    }
                    SymbolTable.SYMBOL_REF.code -> {
                        backup = chars.getPos(backup)
                        c = chars.next(opts); literal = chars.isEscaped()
                        val anchor = c == ']'.code && !literal
                        if (symbols == null && !anchor) {
                            c = SymbolTable.SYMBOL_REF.code; chars.setPos(backup)
                        } else if (anchor && op == '\u0000') {
                            if (lastItem == 1) { add(lastChar); appendPatternCodePoint(pattern, lastChar, false) }
                            add(UnicodeMatcher.ETHER.code); usePattern = true
                            pattern.append(SymbolTable.SYMBOL_REF).append(']'); mode = 2
                            continue
                        } else syntaxError(chars, "Unquoted '$'")
                    }
                }
            }
            when (lastItem) {
                0 -> {
                    if (op == '-' && lastString != null) syntaxError(chars, "Invalid range")
                    lastItem = 1; lastChar = c; lastString = null
                }
                1 -> {
                    if (op == '-') {
                        if (lastString != null || lastChar >= c) syntaxError(chars, "Invalid range")
                        add(lastChar, c); appendPatternCodePoint(pattern, lastChar, false)
                        pattern.append(op); appendPatternCodePoint(pattern, c, false)
                        lastItem = 0; op = '\u0000'
                    } else { add(lastChar); appendPatternCodePoint(pattern, lastChar, false); lastChar = c }
                }
                2 -> {
                    if (op != '\u0000') syntaxError(chars, "Set expected after operator")
                    lastChar = c; lastItem = 1
                }
            }
        }
        if (mode != 2) syntaxError(chars, "Missing ']'")
        chars.skipIgnored(opts)
        if (options and (CASE_INSENSITIVE or ADD_CASE_MAPPINGS) != 0) closeOver(options)
        if (invert) complement().removeAllStrings()
        if (usePattern) rebuilt.append(pattern) else rebuilt.append(_generatePattern(StringBuilder(), false, true))
    }
    private fun appendPatternString(result: StringBuilder, s: String) {
        var i = 0
        while (i < s.length) { val cp = UTF16.charAt(s, i); appendPatternCodePoint(result, cp, false); i += UTF16.getCharCount(cp) }
    }
    /** A sorted unique sink retaining partial expansion before an exception. */
    private fun stringRangeOutput(): MutableCollection<String> = object : AbstractMutableCollection<String>() {
        override val size: Int get() = stringElements.size
        override fun iterator(): MutableIterator<String> = stringElements.iterator()
        override fun add(element: String): Boolean {
            val index = stringElements.binarySearch(element)
            if (index >= 0) return false
            stringElements.add(-index - 1, element)
            return true
        }
    }
    internal fun applyPropertyPattern(pattern: String, position: ParsePosition, symbols: SymbolTable?): UnicodeSet? {
        var pos = position.index
        if (pos + 5 > pattern.length) return null
        var posix = false
        var isName = false
        var invert = false
        if (pattern.regionMatches(pos, "[:", 0, 2)) {
            posix = true
            pos = PatternProps.skipWhiteSpace(pattern, pos + 2)
            if (pos < pattern.length && pattern[pos] == '^') { ++pos; invert = true }
        } else if (pattern.regionMatches(pos, "\\p", 0, 2, true) || pattern.regionMatches(pos, "\\N", 0, 2)) {
            val c = pattern[pos + 1]
            invert = c == 'P'
            isName = c == 'N'
            pos = PatternProps.skipWhiteSpace(pattern, pos + 2)
            if (pos == pattern.length || pattern[pos++] != '{') return null
        } else return null
        val close = pattern.indexOf(if (posix) ":]" else "}", pos)
        if (close < 0) return null
        val equals = pattern.indexOf('=', pos)
        var propName: String
        var valueName: String?
        if (equals >= 0 && equals < close && !isName) {
            propName = pattern.substring(pos, equals)
            valueName = pattern.substring(equals + 1, close)
        } else {
            propName = pattern.substring(pos, close)
            valueName = null
            if (isName) { valueName = propName; propName = "na" }
        }
        applyPropertyAlias(propName, valueName, symbols)
        if (invert) complement().removeAllStrings()
        position.index = close + if (posix) 2 else 1
        return this
    }
    internal fun applyPropertyPattern(chars: RuleCharacterIterator, rebuilt: Appendable, symbols: SymbolTable?) {
        val pattern = chars.getCurrentBuffer()
        val start = chars.getCurrentBufferPos()
        val position = ParsePosition(start)
        applyPropertyPattern(pattern, position, symbols)
        val length = position.index - start
        if (length == 0) syntaxError(chars, "Invalid property pattern")
        chars.jumpahead(length)
        rebuilt.append(pattern.substring(start, position.index))
    }
    fun set(start: Int, end: Int): UnicodeSet { checkFrozen(); clear(); complement(start, end); return this }
    fun applyPropertyAlias(propertyAlias: String, valueAlias: String?): UnicodeSet = applyPropertyAlias(propertyAlias, valueAlias, null)
    fun applyPropertyAlias(propertyAlias: String, valueAlias: String?, symbols: SymbolTable?): UnicodeSet {
        checkFrozen()
        if (symbols is XSymbolTable && symbols.applyPropertyAlias(propertyAlias, valueAlias, this)) return this
        if (defaultXSymbolTable.load()?.applyPropertyAlias(propertyAlias, valueAlias, this) == true) return this
        var p: Int
        var v: Int
        var invert = false
        if (!valueAlias.isNullOrEmpty()) {
            p = UCharacter.getPropertyEnum(propertyAlias)
            if (p == UProperty.GENERAL_CATEGORY) p = UProperty.GENERAL_CATEGORY_MASK
            if (p in UProperty.BINARY_START until UProperty.BINARY_LIMIT || p in UProperty.INT_START until UProperty.INT_LIMIT || p in UProperty.MASK_START until UProperty.MASK_LIMIT) {
                try { v = UCharacter.getPropertyValueEnum(p, valueAlias) }
                catch (e: IllegalArgumentException) {
                    if (p == UProperty.CANONICAL_COMBINING_CLASS || p == UProperty.LEAD_CANONICAL_COMBINING_CLASS || p == UProperty.TRAIL_CANONICAL_COMBINING_CLASS) {
                        v = PatternProps.trimWhiteSpace(valueAlias).toInt()
                        if (v !in 0..255) throw e
                    } else throw e
                }
            } else when (p) {
                UProperty.NUMERIC_VALUE -> {
                    val value = ICUNumberParsing.parseDouble(PatternProps.trimWhiteSpace(valueAlias))
                    applyFilter({ UCharacter.getUnicodeNumericValue(it) == value }, CharacterPropertiesImpl.getInclusionsForProperty(p))
                    return this
                }
                UProperty.NAME -> {
                    val ch = UCharacter.getCharFromExtendedName(mungeCharName(valueAlias))
                    require(ch != -1) { "Invalid character name" }
                    clear(); add(ch)
                    return this
                }
                UProperty.UNICODE_1_NAME -> throw IllegalArgumentException("Unicode_1_Name (na1) not supported")
                UProperty.AGE -> {
                    val version = VersionInfo.getInstance(mungeCharName(valueAlias))
                    val noVersion = VersionInfo.getInstance(0)
                    applyFilter({ val age = UCharacter.getAge(it); age != noVersion && age <= version }, CharacterPropertiesImpl.getInclusionsForProperty(p))
                    return this
                }
                UProperty.SCRIPT_EXTENSIONS -> v = UCharacter.getPropertyValueEnum(UProperty.SCRIPT, valueAlias)
                UProperty.IDENTIFIER_TYPE -> v = UCharacter.getPropertyValueEnum(p, valueAlias)
                else -> throw IllegalArgumentException("Unsupported property")
            }
        } else {
            val names = UPropertyAliases.INSTANCE
            p = UProperty.GENERAL_CATEGORY_MASK
            v = names.getPropertyValueEnum(p, propertyAlias)
            if (v == -1) {
                p = UProperty.SCRIPT
                v = names.getPropertyValueEnum(p, propertyAlias)
                if (v == -1) {
                    p = names.getPropertyEnum(propertyAlias)
                    if (p in UProperty.BINARY_START until UProperty.BINARY_LIMIT) v = 1
                    else if (p == -1) {
                        when {
                            UPropertyAliases.compare("ANY", propertyAlias) == 0 -> return set(0, 0x10ffff)
                            UPropertyAliases.compare("ASCII", propertyAlias) == 0 -> return set(0, 127)
                            UPropertyAliases.compare("Assigned", propertyAlias) == 0 -> {
                                p = UProperty.GENERAL_CATEGORY_MASK
                                v = 1
                                invert = true
                            }
                            else -> throw IllegalArgumentException("Invalid property alias: $propertyAlias=$valueAlias")
                        }
                    } else throw IllegalArgumentException("Missing property value")
                }
            }
        }
        applyIntPropertyValue(p, v)
        if (invert) complement().removeAllStrings()
        return this
    }
    private fun mungeCharName(input: String): String {
        val source = PatternProps.trimWhiteSpace(input)
        var buffer: StringBuilder? = null
        for (i in source.indices) {
            var ch = source[i]
            if (PatternProps.isWhiteSpace(ch.code)) {
                if (buffer == null) buffer = StringBuilder().append(source, 0, i)
                else if (buffer[buffer.length - 1] == ' ') continue
                ch = ' '
            }
            buffer?.append(ch)
        }
        return buffer?.toString() ?: source
    }
    /** Replaces this set with the characters (and supported emoji strings) having the given property value. */
    fun applyIntPropertyValue(prop: Int, value: Int): UnicodeSet {
        val properties = io.github.heapy.icu4k.lang.UProperty
        when {
            prop == properties.GENERAL_CATEGORY_MASK -> applyFilter(
                { (1 shl io.github.heapy.icu4k.lang.UCharacter.getType(it)) and value != 0 },
                io.github.heapy.icu4k.impl.CharacterPropertiesImpl.getInclusionsForProperty(prop))
            prop == properties.SCRIPT_EXTENSIONS -> applyFilter(
                { io.github.heapy.icu4k.lang.UScript.hasScript(it, value) },
                io.github.heapy.icu4k.impl.CharacterPropertiesImpl.getInclusionsForProperty(prop))
            prop == properties.IDENTIFIER_TYPE -> applyFilter(
                { io.github.heapy.icu4k.impl.UCharacterProperty.INSTANCE.hasIDType(it, value) },
                io.github.heapy.icu4k.impl.CharacterPropertiesImpl.getInclusionsForProperty(prop))
            prop in 0 until properties.BINARY_LIMIT -> {
                if (value == 0 || value == 1) {
                    set(io.github.heapy.icu4k.lang.CharacterProperties.getBinaryPropertySet(prop))
                    if (value == 0) complement().removeAllStrings()
                } else clear()
            }
            prop in properties.INT_START until properties.INT_LIMIT -> applyFilter(
                { io.github.heapy.icu4k.lang.UCharacter.getIntPropertyValue(it, prop) == value },
                io.github.heapy.icu4k.impl.CharacterPropertiesImpl.getInclusionsForProperty(prop))
            else -> throw IllegalArgumentException("unsupported property $prop")
        }
        return this
    }
    private fun applyFilter(filter: (Int) -> Boolean, inclusions: UnicodeSet) {
        clear()
        var start = -1
        for (i in 0 until inclusions.getRangeCount()) {
            for (c in inclusions.getRangeStart(i)..inclusions.getRangeEnd(i)) {
                if (filter(c)) {
                    if (start < 0) start = c
                } else if (start >= 0) {
                    add(start, c - 1)
                    start = -1
                }
            }
        }
        if (start >= 0) add(start, 0x10ffff)
    }
    fun getRangeCount(): Int = len / 2
    fun getRangeStart(index: Int): Int = list[index * 2]
    fun getRangeEnd(index: Int): Int = list[index * 2 + 1] - 1
    fun hasStrings(): Boolean = stringElements.isNotEmpty()
    fun isEmpty(): Boolean = len == 1 && !hasStrings()
    /** Override property interpretation during UnicodeSet pattern parsing. */
    abstract class XSymbolTable : SymbolTable {
        override fun lookupMatcher(ch: Int): UnicodeMatcher? = null
        override fun lookup(s: String): CharArray? = null
        override fun parseReference(text: String, pos: ParsePosition, limit: Int): String? = null
        open fun applyPropertyAlias(propertyName: String, propertyValue: String?, result: UnicodeSet): Boolean = false
    }
    class EntryRange internal constructor() {
        @kotlin.jvm.JvmField var codepoint = 0
        @kotlin.jvm.JvmField var codepointEnd = 0
        override fun toString(): String = buildString {
            appendPatternCodePoint(this, codepoint, false)
            if (codepoint != codepointEnd) { append('-'); appendPatternCodePoint(this, codepointEnd, false) }
        }
    }
    /** Reuses one mutable entry per iterator. Do not alter the set during traversal. */
    fun ranges(): Iterable<EntryRange> = Iterable {
        object : MutableIterator<EntryRange> {
            private var pos = 0
            private val result = EntryRange()
            override fun hasNext(): Boolean = pos < len - 1
            override fun next(): EntryRange {
                if (!hasNext()) throw NoSuchElementException()
                result.codepoint = list[pos++]
                result.codepointEnd = list[pos++] - 1
                return result
            }
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }
    fun addBridges(dontCare: UnicodeSet): UnicodeSet {
        val gaps = UnicodeSet(this).complement().removeAllStrings()
        val iterator = UnicodeSetIterator(gaps)
        while (iterator.nextRange()) if (iterator.codepoint != 0 && iterator.codepointEnd != MAX_VALUE && dontCare.contains(iterator.codepoint, iterator.codepointEnd)) add(iterator.codepoint, iterator.codepointEnd)
        return this
    }
    fun findIn(value: CharSequence, fromIndex: Int, findNot: Boolean): Int {
        var index = fromIndex
        while (index < value.length) {
            val cp = UTF16.charAt(value, index)
            if (contains(cp) != findNot) break
            index += UTF16.getCharCount(cp)
        }
        return index
    }
    fun findLastIn(value: CharSequence, fromIndex: Int, findNot: Boolean): Int {
        var index = fromIndex - 1
        while (index >= 0) {
            val cp = UTF16.charAt(value, index)
            if (contains(cp) != findNot) break
            index -= UTF16.getCharCount(cp)
        }
        return if (index < 0) -1 else index
    }
    fun stripFrom(source: CharSequence, matches: Boolean): String = buildString {
        var pos = 0
        while (pos < source.length) {
            val inside = findIn(source, pos, !matches)
            append(source.subSequence(pos, inside))
            pos = findIn(source, inside, matches)
        }
    }
    enum class ComparisonStyle { SHORTER_FIRST, LEXICOGRAPHIC, LONGER_FIRST }
    override fun compareTo(other: UnicodeSet): Int = compareTo(other, ComparisonStyle.SHORTER_FIRST)
    fun compareTo(other: UnicodeSet, style: ComparisonStyle): Int {
        if (style != ComparisonStyle.LEXICOGRAPHIC) {
            val diff = size() - other.size()
            if (diff != 0) return if ((diff < 0) == (style == ComparisonStyle.SHORTER_FIRST)) -1 else 1
        }
        var i = 0
        while (true) {
            val result = list[i] - other.list[i]
            if (result != 0) {
                if (list[i] == HIGH) return if (!hasStrings()) 1 else compare(stringElements.first(), other.list[i])
                if (other.list[i] == HIGH) {
                    if (!other.hasStrings()) return -1
                    val comparison = compare(other.stringElements.first(), list[i])
                    return if (comparison > 0) -1 else if (comparison < 0) 1 else 0
                }
                return if (i and 1 == 0) result else -result
            }
            if (list[i] == HIGH) break
            ++i
        }
        return compare(stringElements, other.stringElements)
    }
    fun compareTo(other: Iterable<String>): Int = compare(this, other)
    fun addAll(s: CharSequence): UnicodeSet {
        checkFrozen()
        var i = 0
        while (i < s.length) {
            val cp = UTF16.charAt(s, i)
            add(cp)
            i += UTF16.getCharCount(cp)
        }
        return this
    }
    fun retainAll(s: CharSequence): UnicodeSet = retainAll(fromAll(s))
    fun removeAll(s: CharSequence): UnicodeSet = removeAll(fromAll(s))
    fun complementAll(s: CharSequence): UnicodeSet = complementAll(fromAll(s))
    fun addAll(source: Iterable<*>): UnicodeSet {
        checkFrozen()
        for (item in source) add(item!!.toString())
        return this
    }
    fun addAll(vararg collection: CharSequence): UnicodeSet {
        checkFrozen()
        for (string in collection) add(string)
        return this
    }
    fun containsAll(collection: Iterable<CharSequence>): Boolean {
        for (item in collection) if (!contains(item)) return false
        return true
    }
    fun containsNone(collection: Iterable<CharSequence>): Boolean {
        for (item in collection) if (contains(item)) return false
        return true
    }
    fun containsSome(collection: Iterable<CharSequence>): Boolean = !containsNone(collection)
    fun removeAll(collection: Iterable<CharSequence>): UnicodeSet {
        checkFrozen()
        for (item in collection) remove(item)
        return this
    }
    fun retainAll(collection: Iterable<CharSequence>): UnicodeSet {
        checkFrozen()
        return retainAll(UnicodeSet().addAll(collection))
    }
    fun containsAll(other: UnicodeSet): Boolean {
        var needA = true; var needB = true
        var aPtr = 0; var bPtr = 0
        val aLen = len - 1; val bLen = other.len - 1
        var startA = 0; var startB = 0; var limitA = 0; var limitB = 0
        while (true) {
            if (needA) {
                if (aPtr >= aLen) { if (needB && bPtr >= bLen) break; return false }
                startA = list[aPtr++]; limitA = list[aPtr++]
            }
            if (needB) {
                if (bPtr >= bLen) break
                startB = other.list[bPtr++]; limitB = other.list[bPtr++]
            }
            if (startB >= limitA) { needA = true; needB = false; continue }
            if (startB >= startA && limitB <= limitA) { needA = false; needB = true; continue }
            return false
        }
        return stringElements.containsAll(other.stringElements)
    }
    fun containsNone(other: UnicodeSet): Boolean {
        var needA = true; var needB = true
        var aPtr = 0; var bPtr = 0
        val aLen = len - 1; val bLen = other.len - 1
        var startA = 0; var startB = 0; var limitA = 0; var limitB = 0
        while (true) {
            if (needA) {
                if (aPtr >= aLen) break
                startA = list[aPtr++]; limitA = list[aPtr++]
            }
            if (needB) {
                if (bPtr >= bLen) break
                startB = other.list[bPtr++]; limitB = other.list[bPtr++]
            }
            if (startB >= limitA) { needA = true; needB = false; continue }
            if (startA >= limitB) { needA = false; needB = true; continue }
            return false
        }
        var a = 0; var b = 0
        while (a < stringElements.size && b < other.stringElements.size) {
            val comparison = stringElements[a].compareTo(other.stringElements[b])
            if (comparison == 0) return false
            if (comparison < 0) ++a else ++b
        }
        return true
    }
    fun containsNone(start: Int, end: Int): Boolean {
        validate(start); validate(end)
        var i = 0
        while (start >= list[i]) ++i
        return i and 1 == 0 && end < list[i]
    }
    fun containsNone(s: CharSequence): Boolean = span(s, SpanCondition.NOT_CONTAINED) == s.length
    fun containsSome(other: UnicodeSet): Boolean = !containsNone(other)
    fun containsSome(start: Int, end: Int): Boolean = !containsNone(start, end)
    fun containsSome(s: CharSequence): Boolean = !containsNone(s)
    fun containsAll(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = UTF16.charAt(s, i)
            if (!contains(cp)) return hasStrings() && containsAllFrom(s, 0)
            i += UTF16.getCharCount(cp)
        }
        return true
    }
    private fun containsAllFrom(s: String, i: Int): Boolean {
        if (i >= s.length) return true
        val cp = UTF16.charAt(s, i)
        if (contains(cp) && containsAllFrom(s, i + UTF16.getCharCount(cp))) return true
        for (string in stringElements) if (string.isNotEmpty() && s.startsWith(string, i) && containsAllFrom(s, i + string.length)) return true
        return false
    }
    /** Rank of a contained code point; string elements do not participate. */
    fun indexOf(c: Int): Int {
        validate(c)
        var i = 0
        var n = 0
        while (true) {
            val start = list[i++]
            if (c < start) return -1
            val limit = list[i++]
            if (c < limit) return n + c - start
            n += limit - start
        }
    }
    /** Code point at the given rank, or -1 outside the code-point portion. */
    fun charAt(index: Int): Int {
        var remaining = index
        if (remaining >= 0) {
            var i = 0
            val limit = len and -2
            while (i < limit) {
                val start = list[i++]
                val count = list[i++] - start
                if (remaining < count) return start + remaining
                remaining -= count
            }
        }
        return -1
    }
    fun size(): Int {
        var result = stringElements.size
        for (i in 0 until getRangeCount()) result += getRangeEnd(i) - getRangeStart(i) + 1
        return result
    }
    override fun contains(c: Int): Boolean { validate(c); return bmpSet?.contains(c) ?: stringSpan?.contains(c) ?: (findCodePoint(c) and 1 != 0) }
    fun contains(start: Int, end: Int): Boolean {
        validate(start); validate(end)
        val i = findCodePoint(start)
        return i and 1 != 0 && end < list[i]
    }
    fun contains(s: CharSequence): Boolean {
        val cp = singleCP(s)
        return if (cp >= 0) contains(cp) else stringElements.binarySearch(s.toString()) >= 0
    }
    fun add(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return add(cp)
        val str = s.toString()
        val index = stringElements.binarySearch(str)
        if (index < 0) { stringElements.add(-index - 1, str); patternCache = null }
        return this
    }
    fun add(c: Int): UnicodeSet { checkFrozen();
        validate(c)
        val i = findCodePoint(c)
        if (i and 1 != 0) return this
        if (c == list[i] - 1) {
            list[i] = c
            if (c == MAX_VALUE) { ensureCapacity(len + 1); list[len++] = HIGH }
            if (i > 0 && c == list[i - 1]) {
                list.copyInto(list, i - 1, i + 1, len); len -= 2
            }
        } else if (i > 0 && c == list[i - 1]) ++list[i - 1]
        else {
            if (len + 2 > list.size) {
                val temp = IntArray(nextCapacity(len + 2))
                list.copyInto(temp, 0, 0, i); list.copyInto(temp, i + 2, i, len); list = temp
            } else list.copyInto(list, i + 2, i, len)
            list[i] = c; list[i + 1] = c + 1; len += 2
        }
        patternCache = null
        return this
    }
    fun add(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start == end) return add(start)
        if (start > end) return this
        val limit = end + 1
        if (len and 1 != 0) {
            val lastLimit = if (len == 1) -2 else list[len - 2]
            if (lastLimit <= start) {
                if (lastLimit == start) {
                    list[len - 2] = limit
                    if (limit == HIGH) --len
                } else {
                    list[len - 1] = start
                    if (limit < HIGH) {
                        ensureCapacity(len + 2); list[len++] = limit; list[len++] = HIGH
                    } else { ensureCapacity(len + 1); list[len++] = HIGH }
                }
                patternCache = null
                return this
            }
        }
        return union(intArrayOf(start, limit, HIGH), 2)
    }
    fun addAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        union(other.list, other.len)
        // Snapshot handles adding a set to itself.
        for (s in other.stringElements.toList()) add(s)
        return this
    }
    fun remove(c: Int): UnicodeSet = remove(c, c)
    fun remove(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start <= end) intersect(intArrayOf(start, end + 1, HIGH), 2, 2)
        return this
    }
    fun retain(c: Int): UnicodeSet = retain(c, c)
    fun retain(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start <= end) intersect(intArrayOf(start, end + 1, HIGH), 2, 0) else clear()
        return this
    }
    fun complement(c: Int): UnicodeSet = complement(c, c)
    fun complement(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start <= end) xor(intArrayOf(start, end + 1, HIGH), 2)
        patternCache = null
        return this
    }
    fun complement(): UnicodeSet { checkFrozen();
        if (list[0] == MIN_VALUE) { list.copyInto(list, 0, 1, len); --len }
        else { ensureCapacity(len + 1); list.copyInto(list, 1, 0, len); list[0] = MIN_VALUE; ++len }
        patternCache = null
        return this
    }
    fun remove(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return remove(cp)
        val i = stringElements.binarySearch(s.toString())
        if (i >= 0) { stringElements.removeAt(i); patternCache = null }
        return this
    }
    fun retain(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return retain(cp)
        val str = s.toString()
        val present = stringElements.binarySearch(str) >= 0
        if (present && getRangeCount() == 0 && size() == 1) return this
        clear()
        if (present) add(str)
        return this
    }
    fun complement(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return complement(cp)
        val str = s.toString()
        val i = stringElements.binarySearch(str)
        if (i >= 0) stringElements.removeAt(i) else stringElements.add(-i - 1, str)
        patternCache = null
        return this
    }
    fun removeAllStrings(): UnicodeSet { checkFrozen(); if (hasStrings()) { stringElements.clear(); patternCache = null }; return this }
    fun retainAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        intersect(other.list, other.len, 0)
        stringElements.removeAll { other.stringElements.binarySearch(it) < 0 }
        return this
    }
    fun removeAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        intersect(other.list, other.len, 2)
        if (this === other) stringElements.clear()
        else stringElements.removeAll { other.stringElements.binarySearch(it) >= 0 }
        return this
    }
    fun complementAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        xor(other.list, other.len)
        for (str in other.stringElements.toList()) complement(str)
        return this
    }
    private fun xor(other: IntArray, otherLen: Int): UnicodeSet {
        ensureBufferCapacity(len + otherLen)
        val out = buffer!!
        var i = 0; var j = 0; var k = 0
        var a = list[i++]; var b = other[j++]
        while (true) {
            if (a < b) { out[k++] = a; a = list[i++] }
            else if (b < a) { out[k++] = b; b = other[j++] }
            else if (a != HIGH) { a = list[i++]; b = other[j++] }
            else { out[k++] = HIGH; len = k; break }
        }
        buffer = list; list = out; patternCache = null
        return this
    }
    private fun intersect(other: IntArray, otherLen: Int, initialPolarity: Int): UnicodeSet {
        ensureBufferCapacity(len + otherLen)
        val out = buffer!!
        var i = 0; var j = 0; var k = 0
        var a = list[i++]; var b = other[j++]
        var polarity = initialPolarity
        main@ while (true) {
            when (polarity) {
                0 -> {
                    if (a < b) { a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        out[k++] = a; a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                3 -> {
                    if (a < b) { out[k++] = a; a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { out[k++] = b; b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        out[k++] = a; a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                1 -> {
                    if (a < b) { a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { out[k++] = b; b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                2 -> {
                    if (b < a) { b = other[j++]; polarity = polarity xor 2 }
                    else if (a < b) { out[k++] = a; a = list[i++]; polarity = polarity xor 1 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
            }
        }
        out[k++] = HIGH; len = k
        buffer = list; list = out; patternCache = null
        return this
    }
    internal fun stringIterator(): Iterator<String> = stringElements.iterator()
    /** Read-only view over the current string storage; empty sets return an empty collection. */
    fun strings(): Collection<String> {
        if (!hasStrings()) return emptyList()
        val data = stringElements
        return object : AbstractCollection<String>() {
            override val size: Int get() = data.size
            override fun iterator(): Iterator<String> {
                val iterator = data.iterator()
                return object : Iterator<String> {
                    override fun hasNext(): Boolean = iterator.hasNext()
                    override fun next(): String = iterator.next()
                }
            }
        }
    }
    override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
        private var sourceList: IntArray? = if (len > 1) list else null
        private val sourceLength = len - 1
        private val sourceStrings = stringElements
        private var item = 0
        private var current = 0
        private var limit = 0
        private var strings: Iterator<String>? = null
        init {
            if (sourceList != null) { current = sourceList!![item++]; limit = sourceList!![item++] }
            else strings = sourceStrings.iterator()
        }
        override fun hasNext(): Boolean = sourceList != null || strings!!.hasNext()
        override fun next(): String {
            val data = sourceList ?: return strings!!.next()
            val cp = current++
            if (current >= limit) {
                if (item >= sourceLength) { strings = sourceStrings.iterator(); sourceList = null }
                else { current = data[item++]; limit = data[item++] }
            }
            return UTF16.valueOf(cp)
        }
        override fun remove(): Nothing = throw UnsupportedOperationException()
    }
    override fun toString(): String = toPattern(true)
    fun getRegexEquivalent(): String {
        if (!hasStrings()) return toString()
        val result = _generatePattern(StringBuilder("(?:"), true, false)
        for (string in stringElements) {
            result.append('|')
            var i = 0
            while (i < string.length) {
                val cp = UTF16.charAt(string, i)
                appendPatternCodePoint(result, cp, true)
                i += UTF16.getCharCount(cp)
            }
        }
        return result.append(')').toString()
    }
    fun matchesAt(text: CharSequence, offset: Int): Int {
        var lastLength = -1
        run strings@ {
            if (!hasStrings()) return@strings
            val firstChar = checkedMatchChar(text, offset)
            var trial: String? = null
            val iterator = stringElements.iterator()
            while (iterator.hasNext()) {
                trial = iterator.next()
                val firstStringChar = checkedMatchChar(trial, 0)
                if (firstStringChar < firstChar) continue
                if (firstStringChar > firstChar) return@strings
            }
            while (true) {
                val tempLength = matchesAtSubstring(text, offset, trial!!)
                if (lastLength > tempLength) return@strings
                lastLength = tempLength
                if (!iterator.hasNext()) break
                trial = iterator.next()
            }
        }
        if (lastLength < 2) {
            val cp = UTF16.charAt(text, offset)
            if (contains(cp)) lastLength = UTF16.getCharCount(cp)
        }
        return offset + lastLength
    }
    private fun checkedMatchChar(text: CharSequence, index: Int): Char {
        if (index < 0 || index >= text.length) throw IndexOutOfBoundsException()
        return text[index]
    }
    private fun matchesAtSubstring(text: CharSequence, offset: Int, substring: CharSequence): Int {
        val length = substring.length
        if (text.length + offset > length) return -1
        var i = 0
        var j = offset
        while (i < length) {
            if (checkedMatchChar(substring, i) != checkedMatchChar(text, j)) return -1
            ++i; ++j
        }
        return i
    }
    override fun toPattern(escapeUnprintable: Boolean): String {
        val cached = patternCache ?: return _generatePattern(StringBuilder(), escapeUnprintable).toString()
        if (!escapeUnprintable) return cached
        val result = StringBuilder()
        var oddBackslashes = false
        var i = 0
        while (i < cached.length) {
            val c = UTF16.charAt(cached, i)
            i += UTF16.getCharCount(c)
            if (Utility.isUnprintable(c)) { Utility.escape(result, c); oddBackslashes = false }
            else if (!oddBackslashes && c == '\\'.code) oddBackslashes = true
            else {
                if (oddBackslashes) result.append('\\')
                UTF16.appendCodePoint(result, c)
                oddBackslashes = false
            }
        }
        if (oddBackslashes) result.append('\\')
        return result.toString()
    }
    fun _generatePattern(result: StringBuilder, escapeUnprintable: Boolean, includeStrings: Boolean = true): StringBuilder {
        result.append('[')
        var i = 0
        var limit = len and -2
        if (len >= 4 && list[0] == 0 && limit == len && !hasStrings()) {
            result.append('^'); i = 1; --limit
        }
        while (i < limit) {
            val start = list[i]
            val end = list[i + 1] - 1
            if (end !in 0xd800..0xdbff) {
                appendPatternRange(result, start, end, escapeUnprintable)
                i += 2
            } else {
                val firstLead = i
                do { i += 2 } while (i < limit && list[i] <= 0xdbff)
                val firstAfterLead = i
                while (i < limit && list[i] <= 0xdfff) {
                    appendPatternRange(result, list[i], list[i + 1] - 1, escapeUnprintable)
                    i += 2
                }
                for (j in firstLead until firstAfterLead step 2) appendPatternRange(result, list[j], list[j + 1] - 1, escapeUnprintable)
            }
        }
        if (includeStrings) for (string in stringElements) {
            result.append('{')
            var index = 0
            while (index < string.length) {
                val lead = string[index].code
                val cp = if (lead in 0xd800..0xdbff && index + 1 < string.length && string[index + 1].code in 0xdc00..0xdfff)
                    ((lead - 0xd800) shl 10) + string[index + 1].code - 0xdc00 + 0x10000 else lead
                appendPatternCodePoint(result, cp, escapeUnprintable)
                index += UTF16.getCharCount(cp)
            }
            result.append('}')
        }
        return result.append(']')
    }
    private fun appendPatternRange(result: StringBuilder, start: Int, end: Int, escape: Boolean) {
        appendPatternCodePoint(result, start, escape)
        if (start != end) {
            if (start + 1 != end || start == 0xdbff) result.append('-')
            appendPatternCodePoint(result, end, escape)
        }
    }
    override fun matchesIndexValue(v: Int): Boolean {
        for (i in 0 until getRangeCount()) {
            val low = getRangeStart(i)
            val high = getRangeEnd(i)
            if (low and -256 == high and -256) {
                if (low and 255 <= v && v <= high and 255) return true
            } else if (low and 255 <= v || v <= high and 255) return true
        }
        for (string in stringElements) if (string.isNotEmpty() && UTF16.charAt(string, 0) and 255 == v) return true
        return false
    }
    override fun addMatchSetTo(toUnionTo: UnicodeSet) { toUnionTo.addAll(this) }
    override fun matches(text: Replaceable, offset: IntArray, limit: Int, incremental: Boolean): Int {
        if (offset[0] == limit) return if (contains(UnicodeMatcher.ETHER.code)) {
            if (incremental) UnicodeMatcher.U_PARTIAL_MATCH else UnicodeMatcher.U_MATCH
        } else UnicodeMatcher.U_MISMATCH
        if (hasStrings()) {
            val forward = offset[0] < limit
            val firstChar = text.charAt(offset[0])
            var highWaterLength = 0
            for (trial in stringElements) {
                if (trial.isEmpty()) continue
                val c = trial[if (forward) 0 else trial.lastIndex]
                if (forward && c > firstChar) break
                if (c != firstChar) continue
                val length = matchRest(text, offset[0], limit, trial)
                if (incremental && length == (if (forward) limit - offset[0] else offset[0] - limit)) return UnicodeMatcher.U_PARTIAL_MATCH
                if (length == trial.length) {
                    if (length > highWaterLength) highWaterLength = length
                    if (forward && length < highWaterLength) break
                }
            }
            if (highWaterLength != 0) {
                offset[0] += if (forward) highWaterLength else -highWaterLength
                return UnicodeMatcher.U_MATCH
            }
        }
        return super.matches(text, offset, limit, incremental)
    }
    private fun matchRest(text: Replaceable, start: Int, limit: Int, string: String): Int {
        val forward = start < limit
        val maxLength = minOf(if (forward) limit - start else start - limit, string.length)
        for (i in 1 until maxLength) {
            if (text.charAt(if (forward) start + i else start - i) != string[if (forward) i else string.lastIndex - i]) return 0
        }
        return maxLength
    }
    override fun freeze(): UnicodeSet {
        if (!isFrozen()) {
            compact()
            if (hasStrings()) stringSpan = UnicodeSetStringSpan(this, ArrayList(stringElements), UnicodeSetStringSpan.ALL)
            if (stringSpan == null || !stringSpan!!.needsStringSpanUTF16()) bmpSet = BMPSet(list, len)
        }
        return this
    }
    fun span(s: CharSequence, condition: SpanCondition): Int = span(s, 0, condition)
    fun span(s: CharSequence, start: Int, condition: SpanCondition): Int {
        val index = if (start < 0) 0 else if (start >= s.length) return s.length else start
        bmpSet?.let { return it.span(s, index, condition, null) }
        stringSpan?.let { return it.span(s, index, condition) }
        if (hasStrings()) {
            val which = if (condition == SpanCondition.NOT_CONTAINED) UnicodeSetStringSpan.FWD_UTF16_NOT_CONTAINED else UnicodeSetStringSpan.FWD_UTF16_CONTAINED
            val engine = UnicodeSetStringSpan(this, ArrayList(stringElements), which)
            if (engine.needsStringSpanUTF16()) return engine.span(s, index, condition)
        }
        return spanCodePointsAndCount(s, index, condition, null)
    }
    fun spanAndCount(s: CharSequence, start: Int, condition: SpanCondition, outCount: OutputInt?): Int {
        require(outCount != null) { "outCount must not be null" }
        val index = if (start < 0) 0 else if (start >= s.length) return s.length else start
        stringSpan?.let { return it.spanAndCount(s, index, condition, outCount) }
        bmpSet?.let { return it.span(s, index, condition, outCount) }
        if (hasStrings()) {
            val which = if (condition == SpanCondition.NOT_CONTAINED) UnicodeSetStringSpan.FWD_UTF16_NOT_CONTAINED else UnicodeSetStringSpan.FWD_UTF16_CONTAINED
            return UnicodeSetStringSpan(this, ArrayList(stringElements), which or UnicodeSetStringSpan.WITH_COUNT).spanAndCount(s, index, condition, outCount)
        }
        return spanCodePointsAndCount(s, index, condition, outCount)
    }
    fun spanBack(s: CharSequence, condition: SpanCondition): Int = spanBack(s, s.length, condition)
    fun spanBack(s: CharSequence, fromIndex: Int, condition: SpanCondition): Int {
        if (fromIndex <= 0) return 0
        val index = minOf(fromIndex, s.length)
        bmpSet?.let { return it.spanBack(s, index, condition) }
        stringSpan?.let { return it.spanBack(s, index, condition) }
        if (hasStrings()) {
            val which = if (condition == SpanCondition.NOT_CONTAINED) UnicodeSetStringSpan.BACK_UTF16_NOT_CONTAINED else UnicodeSetStringSpan.BACK_UTF16_CONTAINED
            val engine = UnicodeSetStringSpan(this, ArrayList(stringElements), which)
            if (engine.needsStringSpanUTF16()) return engine.spanBack(s, index, condition)
        }
        return spanCodePointsBack(s, index, condition)
    }
    override fun isFrozen(): Boolean = bmpSet != null || stringSpan != null
    override fun cloneAsThawed(): UnicodeSet = UnicodeSet(this)
    fun clone(): UnicodeSet = if (isFrozen()) this else UnicodeSet(this)
    /** Internal code-point-only freezing used by the string span engine. */
    internal fun freezeCodePointSet(): UnicodeSet {
        check(!hasStrings())
        if (bmpSet == null) { compact(); bmpSet = BMPSet(list, len) }
        return this
    }
    private fun checkFrozen() { if (isFrozen()) throw UnsupportedOperationException("Attempt to modify frozen object") }
    /** Original unfrozen code-point span fallback; caller handles empty/clamped input. */
    internal fun spanCodePointsAndCount(s: CharSequence, start: Int, condition: SpanCondition, outCount: OutputInt?): Int {
        bmpSet?.let { return it.span(s, start, condition, outCount) }
        val contained = condition != SpanCondition.NOT_CONTAINED
        var next = start
        var count = 0
        do {
            val lead = s[next].code
            var cp = lead
            if (lead in 0xd800..0xdbff && next + 1 < s.length && s[next + 1].code in 0xdc00..0xdfff)
                cp = ((lead - 0xd800) shl 10) + s[next + 1].code - 0xdc00 + 0x10000
            if (contained != contains(cp)) break
            ++count
            next += if (cp > 0xffff) 2 else 1
        } while (next < s.length)
        if (outCount != null) outCount.value = count
        return next
    }
    internal fun spanCodePointsBack(s: CharSequence, fromIndex: Int, condition: SpanCondition): Int {
        bmpSet?.let { return it.spanBack(s, fromIndex, condition) }
        val contained = condition != SpanCondition.NOT_CONTAINED
        var previous = fromIndex
        do {
            val trail = s[previous - 1].code
            var cp = trail
            if (trail in 0xdc00..0xdfff && previous >= 2 && s[previous - 2].code in 0xd800..0xdbff)
                cp = ((s[previous - 2].code - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
            if (contained != contains(cp)) break
            previous -= if (cp > 0xffff) 2 else 1
        } while (previous > 0)
        return previous
    }
    private fun union(other: IntArray, otherLen: Int): UnicodeSet {
        ensureBufferCapacity(len + otherLen)
        val out = buffer!!
        var i = 0; var j = 0; var k = 0
        var a = list[i++]; var b = other[j++]
        var polarity = 0
        main@ while (true) {
            when (polarity) {
                0 -> {
                    if (a < b) {
                        if (k > 0 && a <= out[k - 1]) a = maxOf(list[i], out[--k])
                        else { out[k++] = a; a = list[i] }
                        ++i; polarity = polarity xor 1
                    } else if (b < a) {
                        if (k > 0 && b <= out[k - 1]) b = maxOf(other[j], out[--k])
                        else { out[k++] = b; b = other[j] }
                        ++j; polarity = polarity xor 2
                    } else {
                        if (a == HIGH) break@main
                        if (k > 0 && a <= out[k - 1]) a = maxOf(list[i], out[--k])
                        else { out[k++] = a; a = list[i] }
                        ++i; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                3 -> {
                    if (b <= a) { if (a == HIGH) break@main; out[k++] = a }
                    else { if (b == HIGH) break@main; out[k++] = b }
                    a = list[i++]; polarity = polarity xor 1
                    b = other[j++]; polarity = polarity xor 2
                }
                1 -> {
                    if (a < b) { out[k++] = a; a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                2 -> {
                    if (b < a) { out[k++] = b; b = other[j++]; polarity = polarity xor 2 }
                    else if (a < b) { a = list[i++]; polarity = polarity xor 1 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
            }
        }
        out[k++] = HIGH; len = k
        buffer = list; list = out; patternCache = null
        return this
    }
    private fun findCodePoint(c: Int): Int {
        if (c < list[0]) return 0
        if (len >= 2 && c >= list[len - 2]) return len - 1
        var low = 0; var high = len - 1
        while (true) {
            val i = (low + high) ushr 1
            if (i == low) return high
            if (c < list[i]) high = i else low = i
        }
    }
    private fun nextCapacity(min: Int): Int = if (min < 25) min + 25 else if (min <= 2500) 5 * min else minOf(2 * min, MAX_LENGTH)
    private fun ensureCapacity(size: Int) { if (size > list.size) list = list.copyOf(nextCapacity(minOf(size, MAX_LENGTH))) }
    private fun ensureBufferCapacity(size: Int) {
        val bounded = minOf(size, MAX_LENGTH)
        if (buffer == null || buffer!!.size < bounded) buffer = IntArray(nextCapacity(bounded))
    }
    override fun equals(other: Any?): Boolean = other is UnicodeSet && len == other.len &&
        (0 until len).all { list[it] == other.list[it] } && stringElements == other.stringElements
    override fun hashCode(): Int {
        var result = len
        for (i in 0 until len) result = result * 1000003 + list[i]
        return result
    }
    companion object {
        internal fun scfString(s: CharSequence, scf: StringBuilder): Boolean {
            var i = 0
            while (i < s.length) {
                var c = UTF16.charAt(s, i)
                var folded = UCharacter.foldCase(c, UCharacter.FOLD_CASE_DEFAULT)
                if (folded != c) {
                    scf.setLength(0)
                    scf.append(s, 0, i)
                    while (true) {
                        scf.append(UTF16.valueOf(folded))
                        i += UTF16.getCharCount(c)
                        if (i == s.length) return true
                        c = UTF16.charAt(s, i)
                        folded = UCharacter.foldCase(c, UCharacter.FOLD_CASE_DEFAULT)
                    }
                }
                i += UTF16.getCharCount(c)
            }
            return false
        }
        internal fun resemblesPropertyPattern(chars: RuleCharacterIterator, options: Int): Boolean {
            val opts = options and RuleCharacterIterator.PARSE_ESCAPES.inv()
            val pos = chars.getPos(null)
            val c = chars.next(opts)
            var result = false
            if (c == '['.code || c == '\\'.code) {
                val d = chars.next(opts and RuleCharacterIterator.SKIP_WHITESPACE.inv())
                result = if (c == '['.code) d == ':'.code else d == 'N'.code || d == 'p'.code || d == 'P'.code
            }
            chars.setPos(pos)
            return result
        }
        private fun syntaxError(chars: RuleCharacterIterator, message: String): Nothing =
            throw IllegalArgumentException("Error: $message at \"${Utility.escape(chars.toString())}\"")
        private val defaultXSymbolTable = AtomicReference<XSymbolTable?>(null)
        fun getDefaultXSymbolTable(): XSymbolTable? = defaultXSymbolTable.load()
        fun setDefaultXSymbolTable(symbols: XSymbolTable?) {
            CharacterPropertiesImpl.clear()
            defaultXSymbolTable.store(symbols)
        }

        const val IGNORE_SPACE = 1
        @Deprecated("Use CASE_INSENSITIVE") const val CASE = 2
        const val CASE_INSENSITIVE = 2
        const val ADD_CASE_MAPPINGS = 4
        const val SIMPLE_CASE_INSENSITIVE = 6
        fun resemblesPattern(pattern: String, pos: Int): Boolean =
            (pos + 1 < pattern.length && pattern[pos] == '[') || resemblesPropertyPattern(pattern, pos)
        internal fun resemblesPropertyPattern(pattern: String, pos: Int): Boolean {
            if (pos + 5 > pattern.length) return false
            return pattern.regionMatches(pos, "[:", 0, 2) || pattern.regionMatches(pos, "\\p", 0, 2, true) || pattern.regionMatches(pos, "\\N", 0, 2)
        }
        fun getSingleCodePoint(s: CharSequence): Int = io.github.heapy.icu4k.lang.CharSequences.getSingleCodePoint(s)
        fun <T : Comparable<T>> compare(first: Collection<T>, other: Collection<T>, style: ComparisonStyle): Int {
            if (style != ComparisonStyle.LEXICOGRAPHIC) {
                val diff = first.size - other.size
                if (diff != 0) return if ((diff < 0) == (style == ComparisonStyle.SHORTER_FIRST)) -1 else 1
            }
            return compare(first, other)
        }
        fun <T, U : MutableCollection<T>> addAllTo(source: Iterable<T>, target: U): U {
            for (item in source) target.add(item)
            return target
        }
        fun <T> addAllTo(source: Iterable<T>, target: Array<T>): Array<T> {
            var i = 0
            for (item in source) target[i++] = item
            return target
        }

        private fun appendPatternCodePoint(result: StringBuilder, c: Int, escape: Boolean) {
            if (if (escape) Utility.isUnprintable(c) else Utility.shouldAlwaysBeEscaped(c)) { Utility.escape(result, c); return }
            if (c in intArrayOf(91, 93, 45, 94, 38, 92, 123, 125, 36, 58) || PatternProps.isWhiteSpace(c)) result.append('\\')
            result.append(UTF16.valueOf(c))
        }

        fun compare(string: CharSequence, codePoint: Int): Int = io.github.heapy.icu4k.lang.CharSequences.compare(string, codePoint)
        fun compare(codePoint: Int, string: CharSequence): Int = -compare(string, codePoint)
        fun <T : Comparable<T>> compare(first: Iterable<T>, other: Iterable<T>): Int = compare(first.iterator(), other.iterator())
        fun <T : Comparable<T>> compare(first: Iterator<T>, other: Iterator<T>): Int {
            while (true) {
                if (!first.hasNext()) return if (other.hasNext()) -1 else 0
                if (!other.hasNext()) return 1
                val result = first.next().compareTo(other.next())
                if (result != 0) return result
            }
        }
        fun from(s: CharSequence): UnicodeSet = UnicodeSet().add(s)
        fun fromAll(s: CharSequence): UnicodeSet = UnicodeSet().addAll(s)
        const val MIN_VALUE = 0
        const val MAX_VALUE = 0x10ffff
        private const val HIGH = 0x110000
        private const val MAX_LENGTH = HIGH + 1
        private fun validate(c: Int) { require(c in MIN_VALUE..MAX_VALUE) { "Invalid code point U+${c.toUInt().toString(16).uppercase().padStart(6, '0')}" } }
        private fun singleCP(s: CharSequence): Int {
            if (s.length == 1) return s[0].code
            if (s.length == 2 && s[0].code in 0xd800..0xdbff && s[1].code in 0xdc00..0xdfff)
                return ((s[0].code - 0xd800) shl 10) + s[1].code - 0xdc00 + 0x10000
            return -1
        }
    }
}

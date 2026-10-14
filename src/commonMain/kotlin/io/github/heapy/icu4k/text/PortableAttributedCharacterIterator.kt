package io.github.heapy.icu4k.text

/** Immutable run snapshot; clones share content but have independent cursors. */
internal class PortableAttributedCharacterIterator private constructor(
    private val text: String,
    private val data: Runs,
    private var index: Int,
) : FormatAttributedCharacterIterator {
    constructor(text: String, ranges: List<FormatAttributeRange>) : this(text, buildRuns(text, ranges), 0)
    override fun first(): Char { index = 0; return current() }
    override fun last(): Char { index = if (text.isEmpty()) 0 else text.lastIndex; return current() }
    override fun current(): Char = if (index == text.length) FORMAT_CHARACTER_DONE else text[index]
    override fun next(): Char { if (index < text.length) index++; return current() }
    override fun previous(): Char {
        if (index == 0) return FORMAT_CHARACTER_DONE
        index--
        return current()
    }
    override fun setIndex(position: Int): Char {
        if (position < 0 || position > text.length) throw IllegalArgumentException("Invalid index")
        index = position
        return current()
    }
    override fun getBeginIndex(): Int = 0
    override fun getEndIndex(): Int = text.length
    override fun getIndex(): Int = index
    override fun clone(): Any = PortableAttributedCharacterIterator(text, data, index)
    private fun run(): Int {
        var low = 0
        var high = data.starts.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (data.starts[middle] <= index) low = middle + 1 else high = middle
        }
        return low - 1
    }
    override fun getRunStart(): Int = if (index == text.length) index else data.starts[run()]
    override fun getRunLimit(): Int = if (index == text.length) index else data.starts[run() + 1]
    override fun getRunStart(attribute: FormatAttribute?): Int = start { current, candidate -> current[attribute] == candidate[attribute] }
    override fun getRunLimit(attribute: FormatAttribute?): Int = limit { current, candidate -> current[attribute] == candidate[attribute] }
    override fun getRunStart(attributes: Set<FormatAttribute>?): Int = start { current, candidate -> attributes!!.all { current[it] == candidate[it] } }
    override fun getRunLimit(attributes: Set<FormatAttribute>?): Int = limit { current, candidate -> attributes!!.all { current[it] == candidate[it] } }
    override fun getAttributes(): Map<FormatAttribute, Any> = if (index == text.length) emptyMap() else data.attributes[run()].toMap()
    override fun getAttribute(attribute: FormatAttribute?): Any? = if (index == text.length) null else data.attributes[run()][attribute]
    override fun getAllAttributeKeys(): Set<FormatAttribute> = data.attributes.flatMap { it.keys }.toSet()
    override fun equals(other: Any?): Boolean = other is PortableAttributedCharacterIterator && data === other.data && index == other.index
    override fun hashCode(): Int = text.hashCode() xor index xor text.length
    private inline fun start(matches: (Map<FormatAttribute, Any>, Map<FormatAttribute, Any>) -> Boolean): Int {
        if (index == text.length) return index
        val current = run()
        var candidate = current
        while (candidate > 0 && matches(data.attributes[current], data.attributes[candidate - 1])) candidate--
        return data.starts[candidate]
    }
    private inline fun limit(matches: (Map<FormatAttribute, Any>, Map<FormatAttribute, Any>) -> Boolean): Int {
        if (index == text.length) return index
        val current = run()
        var candidate = current + 1
        while (candidate < data.attributes.size && matches(data.attributes[current], data.attributes[candidate])) candidate++
        return data.starts[candidate]
    }
    private class Runs(val starts: IntArray, val attributes: List<Map<FormatAttribute, Any>>)
    companion object {
        private fun buildRuns(text: String, ranges: List<FormatAttributeRange>): Runs {
            val boundaries = mutableSetOf<Int>()
            boundaries.add(0); boundaries.add(text.length)
            for (range in ranges) {
                if (range.start < 0 || range.limit > text.length || range.start >= range.limit) throw IllegalArgumentException("Invalid substring range")
                boundaries.add(range.start); boundaries.add(range.limit)
            }
            val starts = boundaries.sorted().toIntArray()
            val attributes = (0 until starts.lastIndex).map { run ->
                buildMap<FormatAttribute, Any> {
                    for (range in ranges) if (starts[run] >= range.start && starts[run] < range.limit) put(range.field, range.value)
                }
            }
            return Runs(starts, attributes)
        }
    }
}

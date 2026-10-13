package io.github.heapy.icu4k.impl

/** Sorted mutable keyword map with live entries and fail-fast structural iteration. */
internal class LocaleKeywordMap : AbstractMutableMap<String, String>() {
    private class Entry(override val key: String, override var value: String) : MutableMap.MutableEntry<String, String> {
        override fun setValue(newValue: String): String { val old = value; value = newValue; return old }
        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun toString(): String = "$key=$value"
    }
    private val data = HashMap<String, Entry>()
    private var version = 0
    override val size: Int get() = data.size
    override fun containsKey(key: String): Boolean = data.containsKey(key)
    override fun get(key: String): String? = data[key]?.value
    override fun put(key: String, value: String): String? {
        val previous = data[key]
        if (previous != null) return previous.setValue(value)
        data[key] = Entry(key, value); version++; return null
    }
    override fun remove(key: String): String? {
        val previous = data.remove(key) ?: return null
        version++; return previous.value
    }
    override fun clear() { data.clear(); version++ }
    override val entries: MutableSet<MutableMap.MutableEntry<String, String>> get() = object : AbstractMutableSet<MutableMap.MutableEntry<String, String>>() {
        override val size: Int get() = data.size
        override fun add(element: MutableMap.MutableEntry<String, String>): Nothing = throw UnsupportedOperationException()
        override fun clear() { this@LocaleKeywordMap.clear() }
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, String>> {
            val keys = data.keys.sorted()
            var index = 0
            var expected = version
            var last: String? = null
            return object : MutableIterator<MutableMap.MutableEntry<String, String>> {
                override fun hasNext(): Boolean = index < keys.size
                override fun next(): MutableMap.MutableEntry<String, String> {
                    if (!hasNext()) throw NoSuchElementException()
                    if (expected != version) throw ConcurrentModificationException()
                    val key = keys[index++]; last = key; return data.getValue(key)
                }
                override fun remove() {
                    val key = last ?: throw IllegalStateException()
                    if (expected != version) throw ConcurrentModificationException()
                    this@LocaleKeywordMap.remove(key); expected = version; last = null
                }
            }
        }
    }
}
internal object LocaleEmptyKeywordMap : AbstractMutableMap<String, String>() {
    override fun put(key: String, value: String): Nothing = throw UnsupportedOperationException()
    override fun remove(key: String): String? = null
    override fun clear() {}
    override val entries: MutableSet<MutableMap.MutableEntry<String, String>> = object : AbstractMutableSet<MutableMap.MutableEntry<String, String>>() {
        override val size: Int = 0
        override fun add(element: MutableMap.MutableEntry<String, String>): Nothing = throw UnsupportedOperationException()
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, String>> = object : MutableIterator<MutableMap.MutableEntry<String, String>> {
            override fun hasNext(): Boolean = false
            override fun next(): Nothing = throw NoSuchElementException()
            override fun remove(): Nothing = throw IllegalStateException()
        }
    }
}

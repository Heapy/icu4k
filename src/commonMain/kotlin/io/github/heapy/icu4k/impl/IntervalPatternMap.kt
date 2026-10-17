// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** String-key hash-bucket traversal for interval stores, including Java-style tree-bin insertion. */
internal class IntervalPatternMap<V>(initialCapacity: Int? = null) : AbstractMutableMap<String?, V>() {
    private class Node<V>(val hash: Int, override val key: String?, initialValue: V) : MutableMap.MutableEntry<String?, V> {
        private var stored = initialValue
        override val value: V get() = stored
        var next: Node<V>? = null
        var previous: Node<V>? = null
        var parent: Node<V>? = null
        var left: Node<V>? = null
        var right: Node<V>? = null
        var red = false
        var tree = false
        override fun setValue(newValue: V): V = stored.also { stored = newValue }
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    }
    private var buckets: Array<Node<V>?> = arrayOfNulls(0)
    private var limit = initialCapacity?.let { rounded(it) } ?: 0
    private var count = 0
    private var changes = 0
    override val size: Int get() = count
    private fun hash(key: String?): Int {
        var h = 0
        if (key != null) for (ch in key) h = 31 * h + ch.code
        return h xor (h ushr 16)
    }
    private fun find(key: String?): Node<V>? {
        if (buckets.isEmpty()) return null
        val h = hash(key)
        var node = buckets[h and (buckets.size - 1)]
        while (node != null) { if (node.hash == h && node.key == key) return node; node = node.next }
        return null
    }
    override fun get(key: String?): V? = find(key)?.value
    override fun containsKey(key: String?): Boolean = find(key) != null
    override fun put(key: String?, value: V): V? {
        if (buckets.isEmpty()) grow()
        val h = hash(key); val index = h and (buckets.size - 1)
        val first = buckets[index]
        if (first == null) buckets[index] = Node(h, key, value)
        else {
            var current: Node<V>? = first
            var length = 0
            var last = first
            while (current != null) {
                if (current.hash == h && current.key == key) return current.setValue(value)
                length++; last = current; current = current.next
            }
            val added = Node(h, key, value)
            if (first.tree) {
                var parent: Node<V> = first
                var direction: Int
                while (true) {
                    direction = compare(added, parent)
                    val child = if (direction < 0) parent.left else parent.right
                    if (child == null) break
                    parent = child
                }
                added.tree = true; added.parent = parent
                added.previous = parent; added.next = parent.next
                parent.next?.previous = added; parent.next = added
                if (direction < 0) parent.left = added else parent.right = added
                front(index, balance(first, added))
            } else {
                last!!.next = added; added.previous = last
                if (length >= 8) { if (buckets.size < 64) grow() else treeify(index) }
            }
        }
        count++; changes++
        if (count > limit) grow()
        return null
    }
    private fun compare(a: Node<V>, b: Node<V>): Int = when {
        a.hash != b.hash -> a.hash.compareTo(b.hash)
        a.key == null -> -1
        b.key == null -> 1
        else -> a.key.compareTo(b.key)
    }
    private fun front(index: Int, root: Node<V>) {
        val first = buckets[index]
        if (root === first) return
        root.previous?.next = root.next
        root.next?.previous = root.previous
        root.previous = null; root.next = first
        first?.previous = root; buckets[index] = root
    }
    private fun rotate(root: Node<V>, pivot: Node<V>, leftward: Boolean): Node<V> {
        val promoted = (if (leftward) pivot.right else pivot.left)!!
        val middle = if (leftward) promoted.left else promoted.right
        if (leftward) pivot.right = middle else pivot.left = middle
        middle?.parent = pivot
        val ancestor = pivot.parent
        promoted.parent = ancestor
        if (ancestor != null) { if (ancestor.left === pivot) ancestor.left = promoted else ancestor.right = promoted }
        if (leftward) promoted.left = pivot else promoted.right = pivot
        pivot.parent = promoted
        return if (ancestor == null) promoted else root
    }
    private fun balance(initialRoot: Node<V>, added: Node<V>): Node<V> {
        var root = initialRoot; var node = added
        node.red = true
        while (node !== root && node.parent?.red == true) {
            var parent = node.parent!!
            var grand = parent.parent ?: break
            val leftSide = parent === grand.left
            val uncle = if (leftSide) grand.right else grand.left
            if (uncle?.red == true) {
                parent.red = false; uncle.red = false; grand.red = true; node = grand
            } else {
                if (node === if (leftSide) parent.right else parent.left) {
                    node = parent; root = rotate(root, node, leftSide)
                    parent = node.parent!!; grand = parent.parent!!
                }
                parent.red = false; grand.red = true
                root = rotate(root, grand, !leftSide)
            }
        }
        root.red = false
        return root
    }
    private fun treeify(index: Int) {
        var root: Node<V>? = null
        var node = buckets[index]
        while (node != null) {
            node.tree = true; node.left = null; node.right = null; node.parent = null
            if (root == null) { root = node; node.red = false }
            else {
                var parent = root
                var direction: Int
                while (true) {
                    direction = compare(node, parent!!)
                    val child = if (direction < 0) parent.left else parent.right
                    if (child == null) break
                    parent = child
                }
                node.parent = parent
                if (direction < 0) parent!!.left = node else parent!!.right = node
                root = balance(root, node)
            }
            node = node.next
        }
        root?.let { front(index, it) }
    }
    private fun grow() {
        val old = buckets
        if (old.size >= (1 shl 30)) { limit = Int.MAX_VALUE; return }
        val capacity = if (old.isNotEmpty()) old.size * 2 else if (limit > 0) limit else 16
        buckets = arrayOfNulls(capacity)
        limit = if (capacity >= (1 shl 30)) Int.MAX_VALUE else (capacity.toLong() * 3 / 4).toInt()
        if (old.isEmpty()) return
        for (first in old) {
            if (first == null) continue
            var lo: Node<V>? = null; var hi: Node<V>? = null
            var loLast: Node<V>? = null; var hiLast: Node<V>? = null
            var loCount = 0; var hiCount = 0
            val wasTree = first.tree
            var node: Node<V>? = first
            while (node != null) {
                val next = node.next; node.next = null
                if (node.hash and old.size == 0) { node.previous = loLast; if (loLast == null) lo = node else loLast.next = node; loLast = node; loCount++ }
                else { node.previous = hiLast; if (hiLast == null) hi = node else hiLast.next = node; hiLast = node; hiCount++ }
                node = next
            }
            for ((head, number, other) in listOf(Triple(lo, loCount, hi), Triple(hi, hiCount, lo))) {
                if (head == null) continue
                val index = head.hash and (capacity - 1)
                buckets[index] = head
                if (wasTree) {
                    if (number <= 6) {
                        var item: Node<V>? = head
                        while (item != null) { item.tree = false; item.parent = null; item.left = null; item.right = null; item = item.next }
                    } else if (other != null) treeify(index)
                }
            }
        }
    }
    override fun clear() { changes++; count = 0; buckets.fill(null) }
    override fun remove(key: String?): V? {
        val node = find(key) ?: return null
        val index = node.hash and (buckets.size - 1)
        node.previous?.next = node.next
        if (node.previous == null) buckets[index] = node.next
        node.next?.previous = node.previous
        count--; changes++
        if (node.tree && buckets[index] != null) treeify(index)
        return node.value
    }
    override val entries: MutableSet<MutableMap.MutableEntry<String?, V>> = object : AbstractMutableSet<MutableMap.MutableEntry<String?, V>>() {
        override val size: Int get() = count
        override fun add(element: MutableMap.MutableEntry<String?, V>): Boolean = throw UnsupportedOperationException()
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String?, V>> = object : MutableIterator<MutableMap.MutableEntry<String?, V>> {
            private var expected = changes
            private var bucket = 0
            private var next: Node<V>? = advance()
            private var previous: Node<V>? = null
            private fun advance(): Node<V>? { while (bucket < buckets.size) buckets[bucket++]?.let { return it }; return null }
            override fun hasNext(): Boolean = next != null
            override fun next(): MutableMap.MutableEntry<String?, V> {
                if (expected != changes) throw ConcurrentModificationException()
                val result = next ?: throw NoSuchElementException()
                previous = result; next = result.next ?: advance()
                return result
            }
            override fun remove() {
                if (expected != changes) throw ConcurrentModificationException()
                val item = previous ?: throw IllegalStateException()
                this@IntervalPatternMap.remove(item.key); expected = changes; previous = null
            }
        }
    }
    private companion object {
        fun rounded(size: Int): Int {
            require(size >= 0)
            var capacity = 1
            while (capacity < size && capacity < 1 shl 30) capacity *= 2
            return capacity
        }
    }
}

internal expect fun <V> intervalPatternMap(initialCapacity: Int? = null): MutableMap<String?, V>

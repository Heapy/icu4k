// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** Ordered AVL storage with comparator key identity and fail-fast structural iteration. */
internal class PatternOrderedMap<K: Comparable<K>,V: Any>: Iterable<Pair<K,V>> {
    private class Node<K,V>(val key: K,var value: V,var left: Node<K,V>?=null,var right: Node<K,V>?=null,var height: Int=1)
    private var root: Node<K,V>?=null
    private var modification=0
    private var count=0
    private fun height(node: Node<K,V>?): Int=node?.height?:0
    private fun update(node: Node<K,V>) { node.height=1+maxOf(height(node.left),height(node.right)) }
    private fun right(node: Node<K,V>): Node<K,V> { val top=node.left!!; node.left=top.right; top.right=node; update(node); update(top); return top }
    private fun left(node: Node<K,V>): Node<K,V> { val top=node.right!!; node.right=top.left; top.left=node; update(node); update(top); return top }
    operator fun get(key: K): V? {
        var node=root
        while(node!=null) { val order=key.compareTo(node.key); node=when { order<0 -> node.left; order>0 -> node.right; else -> return node.value } }
        return null
    }
    operator fun set(key: K,value: V) { root=put(root,key,value) }
    private fun put(node: Node<K,V>?,key: K,value: V): Node<K,V> {
        if(node==null) { modification++; count++; return Node(key,value) }
        val order=key.compareTo(node.key)
        when { order<0 -> node.left=put(node.left,key,value); order>0 -> node.right=put(node.right,key,value); else -> { node.value=value; return node } }
        return balance(node)
    }
    private fun balance(node: Node<K,V>): Node<K,V> {
        update(node)
        val balance=height(node.left)-height(node.right)
        if(balance>1) { if(height(node.left!!.left)<height(node.left!!.right)) node.left=left(node.left!!); return right(node) }
        if(balance< -1) { if(height(node.right!!.right)<height(node.right!!.left)) node.right=right(node.right!!); return left(node) }
        return node
    }
    fun remove(key: K): V? {
        val previous=get(key)?:return null
        root=delete(root!!,key); modification++; count--
        return previous
    }
    private fun delete(node: Node<K,V>,key: K): Node<K,V>? {
        val order=key.compareTo(node.key)
        if(order<0) node.left=delete(node.left!!,key)
        else if(order>0) node.right=delete(node.right!!,key)
        else {
            if(node.left==null) return node.right
            if(node.right==null) return node.left
            var successor=node.right!!; while(successor.left!=null) successor=successor.left!!
            return balance(Node(successor.key,successor.value,node.left,deleteMin(node.right!!)))
        }
        return balance(node)
    }
    private fun deleteMin(node: Node<K,V>): Node<K,V>? {
        if(node.left==null) return node.right
        node.left=deleteMin(node.left!!); return balance(node)
    }
    private fun clear() { root=null; count=0; modification++ }
    val keys: MutableSet<K> = object: AbstractMutableSet<K>() {
        override val size: Int get()=count
        override fun contains(element: K): Boolean=this@PatternOrderedMap[element]!=null
        override fun add(element: K): Boolean=throw UnsupportedOperationException()
        override fun remove(element: K): Boolean=this@PatternOrderedMap.remove(element)!=null
        override fun clear()=this@PatternOrderedMap.clear()
        override fun iterator(): MutableIterator<K> = object: MutableIterator<K> {
            private var expected=modification
            private val stack=ArrayList<Node<K,V>>()
            private var last: Node<K,V>?=null
            init { descend(root) }
            private fun descend(start: Node<K,V>?) { var node=start; while(node!=null) { stack.add(node); node=node.left } }
            override fun hasNext(): Boolean=stack.isNotEmpty()
            override fun next(): K {
                if(stack.isEmpty()) throw NoSuchElementException()
                if(expected!=modification) throw ConcurrentModificationException()
                val node=stack.removeAt(stack.lastIndex); descend(node.right); last=node; return node.key
            }
            override fun remove() {
                val previous=last?:throw IllegalStateException()
                if(expected!=modification) throw ConcurrentModificationException()
                this@PatternOrderedMap.remove(previous.key); expected=modification; last=null
                stack.clear(); var node=root
                while(node!=null) {
                    if(node.key.compareTo(previous.key)>0) { stack.add(node); node=node.left } else node=node.right
                }
            }
        }
    }
    fun asMutableMap(): MutableMap<K,V> = object: AbstractMutableMap<K,V>() {
        override fun put(key: K,value: V): V? = this@PatternOrderedMap[key].also { this@PatternOrderedMap[key]=value }
        override fun get(key: K): V? = this@PatternOrderedMap[key]
        override fun remove(key: K): V? = this@PatternOrderedMap.remove(key)
        override fun clear() = this@PatternOrderedMap.clear()
        override val entries: MutableSet<MutableMap.MutableEntry<K,V>> = object: AbstractMutableSet<MutableMap.MutableEntry<K,V>>() {
            override val size: Int get()=count
            override fun add(element: MutableMap.MutableEntry<K,V>): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K,V>> {
                val keys=this@PatternOrderedMap.keys.iterator()
                return object: MutableIterator<MutableMap.MutableEntry<K,V>> {
                    override fun hasNext(): Boolean=keys.hasNext()
                    override fun remove()=keys.remove()
                    override fun next(): MutableMap.MutableEntry<K,V> {
                        val k=keys.next()
                        return object: MutableMap.MutableEntry<K,V> {
                            override val key: K get()=k
                            override val value: V get()=this@PatternOrderedMap[k]!!
                            override fun setValue(newValue: V): V = value.also { this@PatternOrderedMap[k]=newValue }
                            override fun equals(other: Any?): Boolean=other is Map.Entry<*,*> && key==other.key && value==other.value
                            override fun hashCode(): Int=key.hashCode() xor value.hashCode()
                        }
                    }
                }
            }
        }
    }
    fun copy(): PatternOrderedMap<K,V> = PatternOrderedMap<K,V>().also { result ->
        fun copyNode(node: Node<K,V>?): Node<K,V>? = node?.let { Node(it.key,it.value,copyNode(it.left),copyNode(it.right),it.height) }
        result.root=copyNode(root); result.count=count
    }
    override fun iterator(): Iterator<Pair<K,V>> = object: Iterator<Pair<K,V>> {
        private val expected=modification
        private val stack=ArrayList<Node<K,V>>()
        init { descend(root) }
        private fun descend(start: Node<K,V>?) { var node=start; while(node!=null) { stack.add(node); node=node.left } }
        override fun hasNext(): Boolean=stack.isNotEmpty()
        override fun next(): Pair<K,V> {
            if(stack.isEmpty()) throw NoSuchElementException()
            if(expected!=modification) throw ConcurrentModificationException()
            val node=stack.removeAt(stack.lastIndex); descend(node.right)
            return node.key to node.value
        }
    }
}

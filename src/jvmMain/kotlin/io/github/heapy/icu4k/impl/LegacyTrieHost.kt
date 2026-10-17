package io.github.heapy.icu4k.impl
internal actual fun legacyTrieHashCode(kind: Int): Int {
    val type=when(kind){1->CharTrie::class.java;2->IntTrie::class.java;else->Trie::class.java}
    if(type.desiredAssertionStatus())throw AssertionError("hashCode not designed")
    return 42
}

// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

abstract class CacheBase<K : Any, V : Any, D> {
    abstract fun getInstance(key: K, data: D): V?
    protected abstract fun createInstance(key: K, data: D): V?
}

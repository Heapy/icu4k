// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Mutable output parameter, including the original null initial state. */
class Output<T>(var value: T? = null) {
    override fun toString(): String = value?.toString() ?: "null"
}

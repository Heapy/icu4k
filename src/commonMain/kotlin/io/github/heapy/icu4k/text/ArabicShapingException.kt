// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2008, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** A failure to shape Arabic text, for example because fixed-length expansion has no space. */
class ArabicShapingException(message: String?) : Exception(message) {
    companion object { private const val serialVersionUID = 5261531805497260490L }
}

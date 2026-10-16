// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone
internal actual fun localizedGMTRegionMatches(text: String, start: Int, pattern: String, patternStart: Int, length: Int): Boolean = LocalizedGMTOffsetCore.portableRegionMatches(text,start,pattern,patternStart,length)

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ICUSerializable

/** Shared format identity. JVM values are raw java.text.Format instances. */
expect abstract class Format : ICUSerializable

/** Portable format roots expose their protocol through UFormatBase or number adapters. */
abstract class PortableFormat protected constructor() : ICUSerializable

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

internal actual fun defaultJavaResourceLoader(): ICUResourceLoader = ICUResourceBundleReader.embeddedLoader
internal actual fun javaPropertyResourceEncoding(): String? = getICUConfigHostProperty("java.util.PropertyResourceBundle.encoding")

internal actual fun defaultICUResourceLoader(): ICUResourceLoader = ICUResourceBundleReader.embeddedLoader

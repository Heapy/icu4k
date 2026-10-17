// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.timezone.SimpleTimeZoneCore

internal class STZInfo : ICUSerializable {
    var sy = -1
    var sm = -1; var sdwm = 0; var sdw = 0; var st = 0; var sdm = 0; var sa = false
    var em = -1; var edwm = 0; var edw = 0; var et = 0; var edm = 0; var ea = false
    fun setStart(m: Int, dwm: Int, dw: Int, t: Int, dm: Int, after: Boolean) { sm = m; sdwm = dwm; sdw = dw; st = t; sdm = dm; sa = after }
    fun setEnd(m: Int, dwm: Int, dw: Int, t: Int, dm: Int, after: Boolean) { em = m; edwm = dwm; edw = dw; et = t; edm = dm; ea = after }
    fun applyTo(zone: SimpleTimeZone) {
        if (sy != -1) zone.setStartYear(sy)
        if (sm != -1) {
            if (sdm == -1) zone.setStartRule(sm, sdwm, sdw, st)
            else if (sdw == -1) zone.setStartRule(sm, sdm, st)
            else zone.setStartRule(sm, sdm, sdw, st, sa)
        }
        if (em != -1) {
            if (edm == -1) zone.setEndRule(em, edwm, edw, et)
            else if (edw == -1) zone.setEndRule(em, edm, et)
            else zone.setEndRule(em, edm, edw, et, ea)
        }
    }
    fun applyTo(zone: SimpleTimeZoneCore) {
        if (sy != -1) zone.setStartYear(sy)
        if (sm != -1) {
            if (sdm == -1) zone.setStartRule(sm, sdwm, sdw, st)
            else if (sdw == -1) zone.setStartRule(sm, sdm, st)
            else zone.setStartRule(sm, sdm, sdw, st, sa)
        }
        if (em != -1) {
            if (edm == -1) zone.setEndRule(em, edwm, edw, et)
            else if (edw == -1) zone.setEndRule(em, edm, et)
            else zone.setEndRule(em, edm, edw, et, ea)
        }
    }
    private companion object { private const val serialVersionUID = -7849612037842370168L }
}

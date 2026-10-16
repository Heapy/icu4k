package com.ibm.icu.impl;

/** Exercises the source's package-private virtual solar calculation boundary. */
public final class AstronomerArrayOracle extends CalendarAstronomer {
    public double[] values;
    public int calls;
    public AstronomerArrayOracle(double[] values) { super(0); this.values = values; }
    @Override double[] getSunLongitude(double julian) { calls++; return values; }
}

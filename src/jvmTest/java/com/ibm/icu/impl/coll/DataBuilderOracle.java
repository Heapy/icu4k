// SPDX-License-Identifier: Unicode-3.0
package com.ibm.icu.impl.coll;
public final class DataBuilderOracle {
    public static Object create() { return new CollationDataBuilder(); }
    public static Object modifier(final boolean change) {
        return new CollationDataBuilder.CEModifier() {
            public long modifyCE32(int ce32) { return change && (ce32 & 0x10000) != 0 ? Collation.ceFromCE32(ce32) + 0x100000000L : Collation.NO_CE; }
            public long modifyCE(long ce) { return change && (ce & 1) == 0 ? ce + 0x100000000L : Collation.NO_CE; }
        };
    }
    public static Object failingModifier(final int at) {
        return new CollationDataBuilder.CEModifier() {
            int count;
            private void check() { if (++count == at) throw new IllegalArgumentException("modifier"); }
            public long modifyCE32(int ce32) { check(); return Collation.NO_CE; }
            public long modifyCE(long ce) { check(); return Collation.NO_CE; }
        };
    }
}

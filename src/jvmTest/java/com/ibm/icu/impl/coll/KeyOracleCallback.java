// SPDX-License-Identifier: Unicode-3.0
package com.ibm.icu.impl.coll;

public final class KeyOracleCallback extends CollationKeys.LevelCallback {
    public final java.util.List<Integer> calls = new java.util.ArrayList<>();
    private final int stop;
    public KeyOracleCallback(int stop) { this.stop = stop; }
    @Override boolean needToWrite(int level) { calls.add(level); return level != stop; }
}

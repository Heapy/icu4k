// SPDX-License-Identifier: Unicode-3.0
package com.ibm.icu.impl.coll;

public final class RuleParserOracle {
    public interface Imports { String load(String locale, String type); }
    public final CollationRuleParser parser;
    public final CollationSettings settings = new CollationSettings();
    public final java.util.List<String> calls = new java.util.ArrayList<>();
    public final java.util.List<CharSequence> retained = new java.util.ArrayList<>();
    public RuleParserOracle(CollationData base, int failAt, boolean withSink, Imports imports) {
        parser = new CollationRuleParser(base);
        if (withSink) parser.setSink(new CollationRuleParser.Sink() {
            void record(String event) { calls.add(event); if(calls.size() == failAt) throw new IllegalArgumentException("sink"); }
            @Override void addReset(int strength, CharSequence str) { retained.add(str); record("reset|" + strength + "|" + str); }
            @Override void addRelation(int strength, CharSequence prefix, CharSequence str, CharSequence extension) {
                retained.add(prefix); retained.add(str); retained.add(extension);
                record("relation|" + strength + "|" + prefix + "|" + str + "|" + extension);
            }
            @Override void optimize(com.ibm.icu.text.UnicodeSet set) { record("optimize|" + set.toPattern(false)); }
            @Override void suppressContractions(com.ibm.icu.text.UnicodeSet set) { record("suppress|" + set.toPattern(false)); }
        });
        if (imports != null) parser.setImporter((locale,type) -> { calls.add("import|"+locale+"|"+type); return imports.load(locale,type); });
    }
    public static String loadRules(String locale, String type) { return CollationLoader.loadRules(new com.ibm.icu.util.ULocale(locale), type); }
    public void parse(String rules) throws java.text.ParseException { parser.parse(rules,settings); }
}

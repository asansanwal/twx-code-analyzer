package tca.rules;

import java.util.*;

/** Registry of every rule (order = category order in reports). */
public final class RuleSet {
    private RuleSet() {}
    public static List<Rule> all() {
        List<Rule> l = new ArrayList<>();
        l.addAll(AppRules.rules()); l.addAll(DependencyRules.rules()); l.addAll(ServiceRules.rules()); l.addAll(BpdRules.rules()); l.addAll(ScriptRules.rules()); l.addAll(AstRules.rules()); l.addAll(CoachRules.rules()); l.addAll(BusinessObjectRules.rules()); l.addAll(QualityRules.rules()); l.addAll(LegacyRules.rules());
        return l;
    }
    public static Rule byId(String id) { for (Rule r : all()) if (r.id.equals(id)) return r; return null; }
}

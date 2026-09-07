package tca.rules;

import java.util.*;
import tca.model.*;

/** Business object rules. */
public final class BusinessObjectRules {
    private BusinessObjectRules() {}
    static final String CAT = "Data";
    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-BO-001", "Business object with too many attributes", CAT, Severity.MAJOR, "The business object has more than 50 properties. Every instance/task save serialises the whole object; wide objects slow down the engine and the coaches.", "Split the object by concern (e.g. header / details / audit) and keep large lists out of the process variables (store references, load on demand).", "IDA check-too-big-businessobject-used") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("boAttributes", 50); for (TwxObject o : c.objects("twClass")) { int n = c.bo(o).properties.size(); if (n > max) out.add(Finding.of(this, o, "", "", "", n + " attributes (max " + max + ")", "")); } }
        });
        l.add(new Rule("TCA-BO-002", "Shared business object", CAT, Severity.MAJOR, "The business object is marked 'shared' (shared business data). Shared objects are stored and locked separately; used without need they slow down every access and complicate versioning.", "Use shared business objects only when instances really must share data; otherwise uncheck 'shared'.", "IDA check-shared-business-object") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("twClass")) if (c.bo(o).shared) out.add(Finding.of(this, o, "", "", "", "Business object '" + o.name + "' is shared", "")); }
        });
        l.add(new Rule("TCA-BO-003", "Business object without documentation", "Documentation", Severity.MINOR, "The business object has no description.", "Document the purpose and the owning domain of the object.", "IDA check-businessobject-documentation") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("twClass")) if (c.bo(o).documentation.trim().isEmpty()) out.add(Finding.of(this, o, "", "", "", "No documentation", "")); }
        });
        l.add(new Rule("TCA-BO-004", "Business object properties without documentation", "Documentation", Severity.INFO, "Properties have no description.", "Describe each property (meaning, format, allowed values).", "IDA check-businessobject-properties-documentation") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("twClass")) { int n = 0; for (BusinessObjectModel.Property p : c.bo(o).properties) if (p.documentation.trim().isEmpty()) n++; if (n > 0 && !c.bo(o).properties.isEmpty()) out.add(Finding.of(this, o, "", "", "properties", n + " of " + c.bo(o).properties.size() + " properties without documentation", "")); } }
        });
        l.add(new Rule("TCA-BO-005", "Property or object name not following conventions", "Naming", Severity.MINOR, "Business object names should be UpperCamelCase and property names lowerCamelCase; other styles (underscores, spaces, all caps) look inconsistent in scripts and JSON.", "Rename to the convention (ObjectName / propertyName).", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("twClass")) { BusinessObjectModel b = c.bo(o); if (!o.name.matches("[A-Z][A-Za-z0-9]*")) out.add(Finding.of(this, o, "", "", "", "Business object name '" + o.name + "' is not UpperCamelCase", "")); int n = 0; String ev = ""; for (BusinessObjectModel.Property p : b.properties) if (!p.name.matches("[a-z][A-Za-z0-9]*")) { n++; if (ev.length() < 120) ev += p.name + " "; } if (n > 0) out.add(Finding.of(this, o, "", "", "properties", n + " property name(s) not lowerCamelCase", ev.trim())); } }
        });
        l.add(new Rule("TCA-BO-006", "Property of type ANY", CAT, Severity.MINOR, "A property is typed ANY. ANY values are not validated, are serialised generically (slow) and nested ANY lists are not delivered to client-side coaches.", "Use a concrete business object or a typed list.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("twClass")) for (BusinessObjectModel.Property p : c.bo(o).properties) if (p.classRef.endsWith("12.c09c9b6e-aabd-4897-bef2-ed61db106297")) out.add(Finding.of(this, o, "", p.name, "property '" + p.name + "'", "Property '" + p.name + "' is typed ANY", "")); }
        });
        return l;
    }
}

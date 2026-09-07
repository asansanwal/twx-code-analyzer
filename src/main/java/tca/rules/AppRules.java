package tca.rules;

import java.util.*;
import tca.model.*;

/** Application level rules: artifact counts, toolkit versions, sizes (thresholds follow the IBM Deployment Accelerator defaults). */
public final class AppRules {
    private AppRules() {}
    static final String CAT = "Application";
    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-APP-001", "Too many toolkit dependencies", CAT, Severity.MAJOR, "The application depends on more toolkits than the recommended maximum (25). Every toolkit adds classloading, snapshot and deployment overhead and widens the change surface.",
                "Consolidate small toolkits, remove unused dependencies (Process Designer: Toolkits > remove) and prefer one shared utility toolkit per domain.", "IDA check-app-too-many-toolkit") {
            public void check(RuleContext c, List<Finding> out) { int n = c.twx.app.dependencies.size(), max = c.threshold("toolkits", 25); if (n > max) out.add(Finding.of(this, null, "", "", "dependencies", n + " toolkit dependencies (max " + max + ")", deps(c.twx.app))); }
        });
        l.add(new Rule("TCA-APP-002", "Same toolkit referenced in different versions", CAT, Severity.CRITICAL, "The dependency tree contains the same toolkit in two or more snapshot versions. Objects of both versions are loaded, behaviour depends on which copy resolves first and upgrades become unpredictable.",
                "Align every dependency (application and toolkits) on one snapshot of the toolkit; upgrade the toolkits that still reference the older version.", "IDA check-app-with-mismatch-version-toolkits") {
            public void check(RuleContext c, List<Finding> out) {
                Map<String, Set<String>> versions = new TreeMap<>();
                for (TwxPackage p : c.twx.allPackages()) for (TwxPackage.Dependency d : p.dependencies) { Set<String> s = versions.get(d.name); if (s == null) { s = new TreeSet<>(); versions.put(d.name, s); } s.add(d.snapshotName); }
                for (Map.Entry<String, Set<String>> e : versions.entrySet()) if (e.getValue().size() > 1) out.add(Finding.of(this, null, "", "", "toolkit " + e.getKey(), "Toolkit '" + e.getKey() + "' is referenced in " + e.getValue().size() + " versions: " + e.getValue(), ""));
            }
        }.impact(2));
        l.add(new Rule("TCA-APP-003", "Deep toolkit nesting", CAT, Severity.MAJOR, "Toolkit dependencies are nested deeper than 4 levels. Deep chains slow down snapshot resolution and make version conflicts (TCA-APP-002) likely.",
                "Flatten the dependency chain: let the application depend directly on the toolkits it uses and avoid toolkits that only re-export other toolkits.", "IDA check-toolkit-nested-level") {
            public void check(RuleContext c, List<Finding> out) { int d = depth(c.twx, c.twx.app, new HashSet<String>()), max = c.threshold("toolkitDepth", 4); if (d > max) out.add(Finding.of(this, null, "", "", "dependencies", "Toolkit nesting depth " + d + " (max " + max + ")", "")); }
        });
        l.add(new Rule("TCA-APP-004", "Large toolkit", CAT, Severity.MAJOR, "A toolkit is larger than 50 MB. Large toolkits (usually because of bundled web assets or libraries) slow down installation, snapshot creation and Process Designer.",
                "Move big static assets out of the toolkit (external web server or a dedicated asset toolkit), remove obsolete files and minified/unminified duplicates.", "IDA check-app-toolkit-size") {
            public void check(RuleContext c, List<Finding> out) { long max = c.threshold("toolkitSizeMb", 50) * 1024L * 1024L; for (TwxPackage p : c.twx.toolkits.values()) if (!c.isSystem(p) && p.zipSize > max) out.add(Finding.of(this, null, "", "", "toolkit " + p.label(), "Toolkit '" + p.name + "' is " + (p.zipSize / 1024 / 1024) + " MB", "")); }
        });
        String[][] counts = { {"process:10", "humanServices", "50", "client-side human services", "TCA-APP-010"}, {"process:3", "heritageHumanServices", "25", "heritage human services", "TCA-APP-011"}, {"process:12", "serviceFlows", "50", "service flows", "TCA-APP-012"}, {"process:6", "generalSystemServices", "50", "general system services", "TCA-APP-013"},
                {"process:4", "integrationServices", "25", "integration services", "TCA-APP-014"}, {"process:2", "ajaxServices", "25", "Ajax services", "TCA-APP-015"}, {"process:1", "decisionServices", "25", "decision services", "TCA-APP-016"}, {"process:7", "advancedIntegrationServices", "25", "advanced integration services", "TCA-APP-017"},
                {"bpd", "bpds", "15", "processes (BPDs)", "TCA-APP-018"}, {"twClass", "businessObjects", "50", "business objects", "TCA-APP-019"}, {"underCoverAgent", "ucas", "25", "undercover agents", "TCA-APP-020"}, {"epv", "epvs", "25", "exposed process values", "TCA-APP-021"}, {"webService", "webServices", "25", "web services", "TCA-APP-022"}, {"sla", "slas", "15", "service level agreements", "TCA-APP-023"} };
        for (final String[] k : counts) {
            l.add(new Rule(k[4], "Too many " + k[3], CAT, Severity.MINOR, "The application contains more " + k[3] + " than the recommended maximum (" + k[2] + "). Very large applications are hard to maintain, slow to open in Process Designer and to deploy; they usually mix several business domains.",
                    "Split the application by business capability, move reusable services and business objects to toolkits, archive obsolete artifacts.", "IDA check-app-with-too-many-*") {
                public void check(RuleContext c, List<Finding> out) {
                    int n = 0; String[] tt = k[0].split(":");
                    for (TwxObject o : c.twx.app.byType(tt[0])) { if (tt.length == 1) n++; else if (c.service(o).processTypeCode.equals(tt[1])) n++; }
                    int max = c.threshold(k[1], Integer.parseInt(k[2])); if (n > max) out.add(Finding.of(this, null, "", "", "inventory", n + " " + k[3] + " (max " + max + ")", ""));
                }
            });
        }
        l.add(new Rule("TCA-APP-030", "Environment variables without value", CAT, Severity.MINOR, "An environment variable has no default value in the application. Services reading tw.env.X get an empty string at runtime unless every environment is configured by hand.",
                "Give every environment variable a sensible default in the application settings and document which environments must override it.", "") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.twx.app.byType("environmentVariableSet")) for (String b : tca.util.Xml.blocks(o.xml, "environmentVariable")) { String n = tca.util.Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); if (n.isEmpty()) n = tca.util.Xml.text(b, "name"); String v = tca.util.Xml.text(b, "defaultValue"); if (v.trim().isEmpty()) out.add(Finding.of(this, o, "", n, "environment variable " + n, "Environment variable '" + n + "' has no default value", "")); }
            }
        });
        return l;
    }
    static String deps(TwxPackage p) { StringBuilder sb = new StringBuilder(); for (TwxPackage.Dependency d : p.dependencies) { if (sb.length() > 0) sb.append(", "); sb.append(d.name).append(' ').append(d.snapshotName); } return sb.toString(); }
    static int depth(TwxModel m, TwxPackage p, Set<String> seen) {
        int best = 0; if (!seen.add(p.snapshotId)) return 0;
        for (TwxPackage.Dependency d : p.dependencies) { TwxPackage t = m.toolkits.get(d.snapshotId); if (t != null && !d.system) best = Math.max(best, 1 + depth(m, t, seen)); }
        seen.remove(p.snapshotId); return best;
    }
}

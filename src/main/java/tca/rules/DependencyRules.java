package tca.rules;

import java.util.*;
import tca.model.*;

/** Dependency graph rules: toolkit cycles, recursive service calls, unused / missing artifacts. */
public final class DependencyRules {
    private DependencyRules() {}
    static final String CAT = "Dependencies";
    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-DEP-001", "Cyclic toolkit dependency", CAT, Severity.CRITICAL, "Toolkits depend on each other in a cycle (A needs B, B needs A). Process Center accepts it in some versions but installs, upgrades and snapshot migrations become fragile and the toolkits cannot be versioned independently.",
                "Break the cycle: move the shared artifacts into a lower-level common toolkit both can depend on, or merge the two toolkits.", "") {
            public void check(RuleContext c, List<Finding> out) {
                Map<String, List<String>> g = new HashMap<>(); Map<String, TwxPackage> byProject = new HashMap<>();
                for (TwxPackage p : c.twx.allPackages()) { byProject.put(p.id, p); List<String> e = new ArrayList<>(); for (TwxPackage.Dependency d : p.dependencies) e.add(d.projectId); g.put(p.id, e); }
                for (List<String> cyc : cycles(g)) { StringBuilder sb = new StringBuilder(); for (String id : cyc) { if (sb.length() > 0) sb.append(" -> "); TwxPackage p = byProject.get(id); sb.append(p == null ? id : p.name); } out.add(Finding.of(this, null, "", "", "dependencies", "Toolkit dependency cycle: " + sb, "")); }
            }
        }.impact(2));
        l.add(new Rule("TCA-DEP-002", "Recursive service call cycle", CAT, Severity.CRITICAL, "Services call each other in a cycle (directly or through other services). Unless a guard condition stops the recursion this ends in a stack overflow or an endless loop and it is very hard to debug.",
                "Restructure the services so the call graph is acyclic; if recursion is intended, add an explicit depth guard variable and document it.", "") {
            public void check(RuleContext c, List<Finding> out) {
                Map<String, List<String>> g = new HashMap<>(); Map<String, TwxObject> names = new HashMap<>();
                for (TwxObject o : c.objects("process")) { names.put(o.id, o); List<String> e = new ArrayList<>(); for (ServiceModel.Item it : c.service(o).items) if (!it.attachedRef.isEmpty()) { TwxObject t = c.twx.find(it.attachedRef); if (t != null && t.type.equals("process")) e.add(t.id); } g.put(o.id, e); }
                for (List<String> cyc : cycles(g)) { StringBuilder sb = new StringBuilder(); for (String id : cyc) { if (sb.length() > 0) sb.append(" -> "); sb.append(names.get(id).name); } out.add(Finding.of(this, names.get(cyc.get(0)), "", "", "call graph", "Service call cycle: " + sb, "")); }
            }
        }.impact(2));
        l.add(new Rule("TCA-DEP-003", "Unused service", CAT, Severity.MAJOR, "A service of the application is not referenced by any process, service, coach, UCA, web service or exposure. Dead artifacts still get deployed, analysed and migrated.",
                "Delete the service, or expose/attach it where it is meant to be used. Check the Process Center 'Where used' view before deleting.", "IDA check-app-unused-service") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.twx.app.byType("process")) { ServiceModel s = c.service(o); if (c.referencedBy(o.id).isEmpty() && !exposed(o) && !s.processTypeCode.equals("12") && !s.processTypeCode.equals("13")) out.add(Finding.of(this, o, "", "", "", "Service '" + o.name + "' (" + s.typeLabel() + ") is not used anywhere", "")); }
            }
        });
        l.add(new Rule("TCA-DEP-004", "Unused business object", CAT, Severity.MINOR, "A business object is not referenced by any variable, parameter, property, coach view binding or service.", "Delete obsolete business objects to keep the data model understandable.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.twx.app.byType("twClass")) if (c.referencedBy(o.id).isEmpty()) out.add(Finding.of(this, o, "", "", "", "Business object '" + o.name + "' is not referenced", "")); }
        });
        l.add(new Rule("TCA-DEP-005", "Unused coach view", CAT, Severity.MINOR, "A coach view is not used by any coach, human service or other coach view.", "Delete the coach view or move it to a UI toolkit if it is meant for reuse.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.twx.app.byType("coachView")) if (c.referencedBy(o.id).isEmpty() && !c.coachView(o).template) out.add(Finding.of(this, o, "", "", "", "Coach view '" + o.name + "' is not used", "")); }
        });
        l.add(new Rule("TCA-DEP-006", "Reference to a missing artifact", CAT, Severity.CRITICAL, "An object references an artifact id that does not exist in the application or in any bundled toolkit (deleted artifact, wrong toolkit version or a dependency that is not part of the export).",
                "Open the object in Process Designer, fix the broken reference (re-select the service, business object or coach view) and re-export; check that every dependency is included.", "") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.twx.app.objects.values()) { if (o.type.equals("artifact") || o.type.equals("contribution")) continue; int n = 0; StringBuilder ev = new StringBuilder();
                    for (String r : o.refs()) { if (r.startsWith("2069.") || r.startsWith("2064.") || r.startsWith("2066.") || r.startsWith("2063.") || r.startsWith("2025.") || r.startsWith("2027.") || r.startsWith("2054.") || r.startsWith("2055.") || r.startsWith("2056.") || r.startsWith("2007.") || r.startsWith("3011.") || r.startsWith("3012.") || r.startsWith("65.") || r.startsWith("66.") || r.startsWith("67.") || r.startsWith("68.") || r.startsWith("70.") || r.startsWith("73.")) continue;
                        if (c.twx.find(r) == null && isArtifactPrefix(r)) { n++; if (ev.length() < 300) ev.append(r).append(' '); } }
                    if (n > 0) out.add(Finding.of(this, o, "", "", "", n + " reference(s) to artifacts that are not in the export", ev.toString().trim())); }
            }
        });
        return l;
    }
    static boolean isArtifactPrefix(String r) { String p = r.substring(0, r.indexOf('.')); return p.equals("1") || p.equals("25") || p.equals("64") || p.equals("12") || p.equals("61") || p.equals("21") || p.equals("24") || p.equals("50") || p.equals("4") || p.equals("62") || p.equals("47"); }
    static boolean exposed(TwxObject o) { return o.xml.contains("<exposedType>") && !o.xml.contains("<exposedType isNull") || o.xml.contains("<participantRef>") && !o.xml.contains("<participantRef isNull"); }

    /** Elementary cycles (Johnson-lite: DFS from each node, report each cycle once by its smallest rotation). */
    public static List<List<String>> cycles(Map<String, List<String>> g) {
        List<List<String>> out = new ArrayList<>(); Set<String> seenKeys = new HashSet<>();
        for (String start : g.keySet()) { Deque<String> path = new ArrayDeque<>(); dfs(g, start, start, path, new HashSet<String>(), out, seenKeys, 0); }
        return out;
    }
    private static void dfs(Map<String, List<String>> g, String start, String node, Deque<String> path, Set<String> onPath, List<List<String>> out, Set<String> seen, int depth) {
        if (depth > 40 || out.size() > 200) return; path.addLast(node); onPath.add(node);
        for (String nx : g.getOrDefault(node, Collections.<String>emptyList())) {
            if (nx.equals(start)) { List<String> cyc = new ArrayList<>(path); String key = canonical(cyc); if (seen.add(key)) out.add(cyc); }
            else if (!onPath.contains(nx) && g.containsKey(nx) && nx.compareTo(start) > 0) dfs(g, start, nx, path, onPath, out, seen, depth + 1);
        }
        path.removeLast(); onPath.remove(node);
    }
    private static String canonical(List<String> cyc) { int min = 0; for (int i = 1; i < cyc.size(); i++) if (cyc.get(i).compareTo(cyc.get(min)) < 0) min = i; StringBuilder sb = new StringBuilder(); for (int i = 0; i < cyc.size(); i++) sb.append(cyc.get((min + i) % cyc.size())).append('>'); return sb.toString(); }
}

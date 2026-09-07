package tca.engine;

import java.util.*;
import java.util.regex.*;
import tca.model.*;
import tca.rules.RuleContext;
import tca.util.Json;

/** Where the application uses each bundled toolkit. Duplicate snapshots of one toolkit are merged into one logical toolkit.
 *  Evidence tiers per toolkit object:
 *  - used:     an application object references the toolkit object's id in its XML (calls, bindings, types, includes) - certain;
 *  - possible: an application script instantiates a business object with new tw.object.<Name>() and exactly one toolkit declares that name (the application itself none) - inferred;
 *  - none:     neither. "No detected usage" is not proof that the toolkit can be removed: runtime lookups by name (tw.system.model.findProcessByName ...) are not visible statically.
 *  Ambiguous constructor names (several toolkits declare the same business object) are reported as diagnostics, not as usage.
 *  Toolkit-to-toolkit references are listed separately (a toolkit needed only by another toolkit is still required). */
public final class ToolkitUsage {
    private ToolkitUsage() {}
    static final Pattern CTOR = Pattern.compile("new\\s+tw\\.object\\.([A-Za-z_$][\\w$]*)\\s*\\(");

    static final class Group { String key, name, acronym; boolean system; final List<TwxPackage> snapshots = new ArrayList<>(); }

    public static Map<String, Object> compute(RuleContext c) {
        TwxModel twx = c.twx; Set<String> appIds = twx.app.objects.keySet();
        // constructor evidence from the application scripts
        Map<String, Set<TwxObject>> ctorUses = new HashMap<>();
        for (TwxObject o : twx.app.objects.values()) if (o.type.equals("process") || o.type.equals("bpd") || o.type.equals("coachView"))
            for (Script s : c.scripts(o)) { Matcher m = CTOR.matcher(s.code); while (m.find()) { Set<TwxObject> u = ctorUses.get(m.group(1)); if (u == null) { u = new LinkedHashSet<>(); ctorUses.put(m.group(1), u); } u.add(o); } }
        Map<String, List<TwxObject>> boByName = new HashMap<>();
        for (TwxPackage t : twx.toolkits.values()) for (TwxObject o : t.byType("twClass")) { List<TwxObject> l = boByName.get(o.name); if (l == null) { l = new ArrayList<>(); boByName.put(o.name, l); } l.add(o); }
        Set<String> appBoNames = new HashSet<>(); for (TwxObject o : twx.app.byType("twClass")) appBoNames.add(o.name);
        // merge snapshots of the same toolkit
        LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
        for (TwxPackage t : twx.toolkits.values()) { String key = !t.id.isEmpty() ? "project:" + t.id : !t.acronym.isEmpty() ? "acronym:" + t.acronym : "name:" + t.name; Group g = groups.get(key); if (g == null) { g = new Group(); g.key = key; g.name = t.name; g.acronym = t.acronym; g.system = c.isSystem(t); groups.put(key, g); } g.snapshots.add(t); }
        Map<String, Set<String>> refBy = c.referencedBy();
        List<Object> out = new ArrayList<>(); List<Object> diagnostics = new ArrayList<>(); int used = 0, possible = 0, none = 0;
        for (Group g : groups.values()) {
            List<Object> objects = new ArrayList<>(); int nUsed = 0, nPossible = 0, total = 0; Set<String> byToolkits = new TreeSet<>(); Map<String, Integer> byType = new TreeMap<>();
            for (TwxPackage t : g.snapshots) for (TwxObject o : t.objects.values()) {
                if (o.type.equals("artifact") || o.type.equals("contribution")) continue; total++;
                Set<TwxObject> users = new LinkedHashSet<>(); String status = "none", evidence = "";
                Set<String> r = refBy.get(o.id);
                if (r != null) for (String id : r) { if (appIds.contains(id)) users.add(twx.find(id)); else { TwxObject u = twx.find(id); if (u != null && !g.snapshots.contains(u.pkg)) byToolkits.add(u.pkg.name); } }
                if (!users.isEmpty()) { status = "used"; evidence = "object id referenced in the application XML"; }
                else if (o.type.equals("twClass") && ctorUses.containsKey(o.name) && !appBoNames.contains(o.name)) {
                    List<TwxObject> same = boByName.get(o.name); Set<String> owners = new TreeSet<>(); for (TwxObject b : same) owners.add(b.pkg.name);
                    if (owners.size() == 1) { status = "possible"; evidence = "new tw.object." + o.name + "() in application scripts"; users.addAll(ctorUses.get(o.name)); }
                    else if (t == g.snapshots.get(0)) diagnostics.add(Json.obj("code", "ambiguous-constructor-name", "message", "new tw.object." + o.name + "() matches a business object in several toolkits (" + owners + "); not counted as usage", "candidates", new ArrayList<>(owners)));
                }
                if (status.equals("none")) continue;
                if (status.equals("used")) nUsed++; else nPossible++; byType.merge(o.typeLabel(), 1, Integer::sum);
                List<Object> ul = new ArrayList<>(); for (TwxObject u : users) if (u != null) ul.add(Json.obj("id", u.id, "name", u.name, "type", u.type, "typeLabel", u.type.equals("process") ? c.service(u).typeLabel() : u.typeLabel()));
                objects.add(Json.obj("id", o.id, "name", o.name, "type", o.type, "typeLabel", o.type.equals("process") ? c.service(o).typeLabel() : o.typeLabel(), "snapshot", t.snapshotName, "status", status, "evidence", evidence, "usedBy", ul));
            }
            String status = nUsed > 0 ? "used" : nPossible > 0 ? "possible" : "none"; if (status.equals("used")) used++; else if (status.equals("possible")) possible++; else none++;
            List<Object> snaps = new ArrayList<>(); for (TwxPackage t : g.snapshots) snaps.add(Json.obj("snapshot", t.snapshotName, "snapshotId", t.snapshotId, "objects", t.objects.size(), "sizeBytes", t.zipSize));
            out.add(Json.obj("key", g.key, "name", g.name, "acronym", g.acronym, "system", g.system, "status", status, "snapshots", snaps, "objects", total, "used", nUsed, "possible", nPossible, "byType", byType, "usedByToolkits", new ArrayList<>(byToolkits), "objectList", objects));
        }
        return Json.obj("summary", Json.obj("toolkits", groups.size(), "used", used, "possible", possible, "none", none), "toolkits", out, "diagnostics", diagnostics);
    }
}

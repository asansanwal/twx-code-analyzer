package tca.engine;

import java.util.*;
import tca.model.*;
import tca.rules.Finding;
import tca.util.Json;

/** Snapshot comparison: object level differences between two TWX models (added / removed / changed by versionId) and the findings delta between two reports. */
public final class Diff {
    private Diff() {}
    public static Map<String, Object> objects(TwxModel a, TwxModel b) {
        Map<String, TwxObject> ma = new HashMap<>(), mb = new HashMap<>();
        for (TwxObject o : a.app.objects.values()) ma.put(key(o), o); for (TwxObject o : b.app.objects.values()) mb.put(key(o), o);
        List<Object> added = new ArrayList<>(), removed = new ArrayList<>(), changed = new ArrayList<>(); int unchanged = 0;
        for (Map.Entry<String, TwxObject> e : mb.entrySet()) { TwxObject o = e.getValue(), old = ma.get(e.getKey()); if (old == null) added.add(row(o)); else if (!old.versionId.equals(o.versionId) || old.xml.length() != o.xml.length() && !old.xml.equals(o.xml)) { Map<String, Object> r = row(o); r.put("sizeBefore", old.xml.length()); r.put("sizeAfter", o.xml.length()); changed.add(r); } else unchanged++; }
        for (Map.Entry<String, TwxObject> e : ma.entrySet()) if (!mb.containsKey(e.getKey())) removed.add(row(e.getValue()));
        List<Object> tk = new ArrayList<>(); Map<String, String> ta = new TreeMap<>(), tb = new TreeMap<>();
        for (TwxPackage.Dependency d : a.app.dependencies) ta.put(d.name, d.snapshotName); for (TwxPackage.Dependency d : b.app.dependencies) tb.put(d.name, d.snapshotName);
        for (String n : new TreeSet<>(union(ta.keySet(), tb.keySet()))) { String va = ta.get(n), vb = tb.get(n); if (va == null || vb == null || !va.equals(vb)) tk.add(Json.obj("name", n, "before", va, "after", vb, "change", va == null ? "added" : vb == null ? "removed" : "version")); }
        return Json.obj("before", Json.obj("name", a.app.name, "snapshot", a.app.snapshotName, "objects", a.app.objects.size()), "after", Json.obj("name", b.app.name, "snapshot", b.app.snapshotName, "objects", b.app.objects.size()),
                "sameApplication", a.app.id.equals(b.app.id), "added", added, "removed", removed, "changed", changed, "unchanged", unchanged, "toolkitChanges", tk);
    }
    public static Map<String, Object> findings(List<Finding> before, List<Finding> after) {
        Map<String, Finding> fa = new HashMap<>(), fb = new HashMap<>(); for (Finding f : before) fa.put(f.key(), f); for (Finding f : after) fb.put(f.key(), f);
        List<Object> nw = new ArrayList<>(), fixed = new ArrayList<>(); int same = 0;
        for (Map.Entry<String, Finding> e : fb.entrySet()) if (!fa.containsKey(e.getKey())) nw.add(e.getValue().toJson()); else same++;
        for (Map.Entry<String, Finding> e : fa.entrySet()) if (!fb.containsKey(e.getKey())) fixed.add(e.getValue().toJson());
        return Json.obj("new", nw, "fixed", fixed, "unchanged", same);
    }
    static String key(TwxObject o) { return o.type + "|" + o.name; }
    static Map<String, Object> row(TwxObject o) { return Json.obj("id", o.id, "name", o.name, "type", o.type, "typeLabel", o.typeLabel(), "versionId", o.versionId); }
    static <T> Set<T> union(Set<T> a, Set<T> b) { Set<T> s = new HashSet<>(a); s.addAll(b); return s; }
}

package tca.model;

import java.util.*;

/** The loaded TWX: the exported package plus every bundled toolkit (keyed by snapshot id). */
public class TwxModel {
    public String fileName = "";
    public long fileSize;
    public TwxPackage app;
    public final LinkedHashMap<String, TwxPackage> toolkits = new LinkedHashMap<>();
    public final List<Map<String, Object>> diagnostics = new ArrayList<>();   // loader problems (missing object files, unreadable toolkit zips), reported with the analysis
    private Map<String, TwxObject> index;
    public void diagnostic(String code, String message) { diagnostics.add(tca.util.Json.obj("code", code, "message", message)); }
    public List<TwxPackage> allPackages() { List<TwxPackage> l = new ArrayList<>(); l.add(app); l.addAll(toolkits.values()); return l; }
    public TwxObject find(String id) {
        if (index == null) { index = new HashMap<>(); for (TwxPackage p : allPackages()) index.putAll(p.objects); }
        if (id == null) return null; TwxObject o = index.get(id); if (o == null && id.startsWith("/")) o = index.get(id.substring(1));
        if (o == null && id.contains("/")) o = index.get(id.substring(id.lastIndexOf('/') + 1)); return o;
    }
    public List<TwxObject> objects(String type, boolean includeToolkits) {
        List<TwxObject> l = new ArrayList<>();
        for (TwxPackage p : includeToolkits ? allPackages() : Collections.singletonList(app)) for (TwxObject o : p.objects.values()) if (type == null || o.type.equals(type)) l.add(o);
        return l;
    }
    /** Toolkit package for a dependency prefix uuid (the part before "/" in class refs) - dependency ids are 2069.<uuid>. */
    public TwxPackage toolkitByDependency(TwxPackage from, String depUuid) {
        for (TwxPackage.Dependency d : from.dependencies) if (d.snapshotId.endsWith(depUuid) || d.projectId.endsWith(depUuid)) return toolkits.get(d.snapshotId);
        return null;
    }
}

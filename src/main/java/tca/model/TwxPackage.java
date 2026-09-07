package tca.model;

import java.util.*;

/** A process application or toolkit inside a TWX (META-INF/package.xml + objects). */
public class TwxPackage {
    public String id = "", name = "", acronym = "", snapshotId = "", snapshotName = "", branchId = "", branchName = "", description = "";
    public String buildVersion = "", buildId = "", snapshotDate = "";   // package.xml root: product version that produced the export (8.5.6, 8.6.0, 20.0.0.2 ...)
    public boolean toolkit;
    public long zipSize;
    public final List<Dependency> dependencies = new ArrayList<>();
    public final LinkedHashMap<String, TwxObject> objects = new LinkedHashMap<>();
    public final List<String> files = new ArrayList<>();          // files/... members (managed asset content)
    public final Map<String, String> jsFiles = new LinkedHashMap<>();   // managed asset id -> JavaScript source of *.js assets (server files define globals for the scripts)
    public static class Dependency { public String projectId = "", name = "", acronym = "", snapshotId = "", snapshotName = "", branchId = ""; public boolean system; }
    public String label() { return name + " (" + acronym + (snapshotName.isEmpty() ? "" : " " + snapshotName) + ")"; }
    public List<TwxObject> byType(String t) { List<TwxObject> l = new ArrayList<>(); for (TwxObject o : objects.values()) if (o.type.equals(t)) l.add(o); return l; }
    public Map<String, Integer> typeCounts() { Map<String, Integer> m = new TreeMap<>(); for (TwxObject o : objects.values()) m.merge(o.type, 1, Integer::sum); return m; }
}

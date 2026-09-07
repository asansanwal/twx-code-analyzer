package tca.engine;

import java.util.*;
import tca.model.*;
import tca.rules.*;
import tca.util.Json;

/** Result of an analysis: metadata, inventory, findings (ranked) and summary counts. */
public class Report {
    public String id = "", fileName = "", analyzedAt = "", appName = "", acronym = "", snapshotName = "", snapshotId = "", projectId = "", branchId = "", engineVersion = Analyzer.VERSION;
    public long fileSize, durationMs;
    public int objectCount, toolkitCount, scriptCount, scriptLines;
    public Map<String, Integer> typeCounts = new TreeMap<>();
    public List<Map<String, Object>> toolkits = new ArrayList<>();
    public List<Finding> findings = new ArrayList<>();
    public List<Map<String, Object>> rules = new ArrayList<>();    // rules applied (id, title, category, severity, count)
    public String buildVersion = "", bawVersion = "";               // product version that exported the package (package.xml buildVersion) and its label
    public Map<String, Object> coverage = new LinkedHashMap<>();     // scripts parsed / skipped (syntax errors), status complete | partial
    public List<Map<String, Object>> diagnostics = new ArrayList<>();   // loader problems + analysis notes (unknown version ...)
    public Map<String, Object> toolkitUsage = new LinkedHashMap<>();  // ToolkitUsage.compute()
    /** "IBM BPM 8.5.7" / "BAW 20.0.0.2" from the package buildVersion, empty when unknown. */
    public static String bawLabel(String v) { if (v == null || v.isEmpty()) return ""; if (v.startsWith("7.") || v.startsWith("8.")) return "IBM BPM " + v; if (v.matches("(1[89]|2\\d)\\..*")) return "BAW " + v; return v; }
    public Map<String, Object> toJson() {
        Map<String, Integer> bySev = new LinkedHashMap<>(), byCat = new TreeMap<>(); int total = 0;
        for (Severity s : Severity.values()) bySev.put(s.name(), 0);
        for (Finding f : findings) { bySev.merge(f.severity, 1, Integer::sum); byCat.merge(f.category, 1, Integer::sum); total += f.score; }
        List<Object> fl = new ArrayList<>(); for (Finding f : findings) fl.add(f.toJson());
        Map<String, Object> m = Json.obj("id", id, "fileName", fileName, "fileSize", fileSize, "analyzedAt", analyzedAt, "engineVersion", engineVersion, "durationMs", durationMs,
                "app", Json.obj("name", appName, "acronym", acronym, "snapshot", snapshotName, "snapshotId", snapshotId, "projectId", projectId, "branchId", branchId, "buildVersion", buildVersion, "bawVersion", bawVersion),
                "inventory", Json.obj("objects", objectCount, "toolkits", toolkitCount, "scripts", scriptCount, "scriptLines", scriptLines, "types", typeCounts, "toolkitList", toolkits),
                "summary", Json.obj("findings", findings.size(), "score", total, "bySeverity", bySev, "byCategory", byCat, "healthScore", healthScore(total)),
                "coverage", coverage, "diagnostics", diagnostics, "toolkitUsage", toolkitUsage, "rules", rules, "findings", fl);
        return m;
    }
    /** 0..100, 100 = no findings; decreases with the weighted total relative to the size of the application. */
    /** 100 = no findings. Weighted findings per application object (density) decay the score: density 3 -> 93, 10 -> 78, 30 -> 47, 65 -> 20. */
    public int healthScore(int total) { double size = Math.max(50, objectCount); double density = total / size; return (int) Math.max(0, Math.round(100 * Math.exp(-density / 40.0))); }
}

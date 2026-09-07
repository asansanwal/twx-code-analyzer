package tca.rules;

import java.util.*;
import tca.model.TwxObject;
import tca.util.Json;

/** One detected issue with its full location path (package > object type > object > step/script) and the remediation text of its rule. */
public class Finding {
    public String ruleId, title, category, severity, message, remediation, reference = "";
    public String packageName = "", packageAcronym = "", packageId = "", objectId = "", objectName = "", objectType = "", objectTypeLabel = "", itemId = "", itemName = "", location = "", evidence = "";
    public boolean toolkit;
    public int score;
    public int line, column;          // 1-based position inside the script / XML when known (0 = not applicable)
    public String snippet = "", confidence = "high";
    public List<String> tags = new ArrayList<>();

    public static Finding of(Rule r, TwxObject o, String itemId, String itemName, String location, String message, String evidence) {
        Finding f = new Finding(); f.ruleId = r.id; f.title = r.title; f.category = r.category; f.severity = r.severity.name(); f.remediation = r.remediation; f.reference = r.reference; f.message = message; f.evidence = evidence == null ? "" : evidence;
        if (o != null) { f.packageName = o.pkg.name; f.packageAcronym = o.pkg.acronym; f.packageId = o.pkg.snapshotId; f.objectId = o.id; f.objectName = o.name; f.objectType = o.type; f.objectTypeLabel = o.typeLabel(); f.toolkit = o.pkg.toolkit; }
        f.itemId = itemId == null ? "" : itemId; f.itemName = itemName == null ? "" : itemName; f.location = location == null ? "" : location; f.score = r.severity.weight * r.impact; f.confidence = r.confidence;
        if (f.evidence.startsWith("line ")) { int c = f.evidence.indexOf(':'); if (c > 5) try { f.line = Integer.parseInt(f.evidence.substring(5, c).trim()); f.snippet = f.evidence.substring(c + 1).trim(); } catch (NumberFormatException e) {} }
        return f;
    }
    /** Sets the exact position (1-based line / column) and the source line of the finding. */
    public Finding at(int line, int column, String snippet) { this.line = line; this.column = column; this.snippet = snippet == null ? "" : snippet; if (evidence.isEmpty() && line > 0) evidence = "line " + line + (column > 0 ? ":" + column : "") + ": " + this.snippet; return this;
    }
    /** Human readable path: Application (ACR 1.2) / Service / Name / step 'X'. */
    public String path() { StringBuilder sb = new StringBuilder(); sb.append(packageName).append(" (").append(packageAcronym).append(")"); if (!objectTypeLabel.isEmpty()) sb.append(" / ").append(objectTypeLabel); if (!objectName.isEmpty()) sb.append(" / ").append(objectName); if (!itemName.isEmpty()) sb.append(" / ").append(itemName); else if (!location.isEmpty()) sb.append(" / ").append(location); return sb.toString(); }
    public Map<String, Object> toJson() {
        return Json.obj("ruleId", ruleId, "title", title, "category", category, "severity", severity, "score", score, "message", message, "remediation", remediation, "reference", reference,
                "package", packageName, "acronym", packageAcronym, "packageId", packageId, "toolkit", toolkit, "objectId", objectId, "objectName", objectName, "objectType", objectType, "objectTypeLabel", objectTypeLabel,
                "itemId", itemId, "itemName", itemName, "location", location, "evidence", evidence, "line", line, "column", column, "snippet", snippet, "confidence", confidence, "path", path(), "tags", tags);
    }
    /** Rebuilds a finding from its JSON form (stored reports of the process application). */
    @SuppressWarnings("unchecked")
    public static Finding fromJson(Map<String, Object> m) {
        Finding f = new Finding(); f.ruleId = s(m, "ruleId"); f.title = s(m, "title"); f.category = s(m, "category"); f.severity = s(m, "severity"); f.message = s(m, "message"); f.remediation = s(m, "remediation"); f.reference = s(m, "reference");
        f.packageName = s(m, "package"); f.packageAcronym = s(m, "acronym"); f.packageId = s(m, "packageId"); f.toolkit = Boolean.TRUE.equals(m.get("toolkit")); f.objectId = s(m, "objectId"); f.objectName = s(m, "objectName"); f.objectType = s(m, "objectType"); f.objectTypeLabel = s(m, "objectTypeLabel");
        f.itemId = s(m, "itemId"); f.itemName = s(m, "itemName"); f.location = s(m, "location"); f.evidence = s(m, "evidence"); f.score = m.get("score") instanceof Number ? ((Number) m.get("score")).intValue() : 0;
        f.line = m.get("line") instanceof Number ? ((Number) m.get("line")).intValue() : 0; f.column = m.get("column") instanceof Number ? ((Number) m.get("column")).intValue() : 0; f.snippet = s(m, "snippet"); if (m.get("confidence") != null) f.confidence = s(m, "confidence");
        if (m.get("tags") instanceof List) for (Object t : (List<Object>) m.get("tags")) f.tags.add(String.valueOf(t));
        return f;
    }
    static String s(Map<String, Object> m, String k) { Object v = m.get(k); return v == null ? "" : String.valueOf(v); }
    /** Stable key for comparing reports of two snapshots (rule + object name/type + item + message stem). */
    public String key() { return ruleId + "|" + objectType + "|" + objectName + "|" + itemName + "|" + location; }
}

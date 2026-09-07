package tca.rules;

import java.util.*;
import tca.util.Json;

/**
 * User customisation of the rule set: which rules run, the severity and impact of each rule, the weight of each severity
 * and the numeric thresholds. Everything is optional: an empty settings object is the built-in behaviour.
 * <p>
 * JSON form (also the export / import file format, {@code format} = {@value #FORMAT}):
 * <pre>
 * { "format": "twx-code-analyzer-settings", "version": 1, "engine": "1.2",
 *   "includeToolkits": false,
 *   "severityWeights": { "CRITICAL": 100, "MAJOR": 40, "MINOR": 10, "INFO": 2 },
 *   "thresholds": { "scriptLinesMedium": 100, ... },
 *   "rules": { "TCA-JS-001": { "enabled": false, "severity": "MAJOR", "impact": 2 }, ... } }
 * </pre>
 * {@link #fromJson(Map)} accepts a full document (every rule listed, as written by {@link #document(List)}) as well as a
 * partial one (only the changed rules); values equal to the rule defaults are dropped, so the stored form stays compact.
 */
public class RuleSettings {
    public static final String FORMAT = "twx-code-analyzer-settings";
    public static final int VERSION = 1;
    /** Threshold names with their built-in defaults and labels (the rules read them through RuleContext.threshold). */
    public static final String[][] THRESHOLDS = {
        {"scriptLinesMedium", "100", "Script length: medium warning (lines)"}, {"scriptLinesHigh", "300", "Script length: high warning (lines)"}, {"complexity", "30", "Cyclomatic complexity per service / process"}, {"logStatements", "5", "Log statements per script"},
        {"serviceSteps", "40", "Steps per service"}, {"serviceParams", "15", "Parameters per service"}, {"privateVariables", "30", "Private variables per service / process"}, {"assignmentsPerStep", "10", "Pre/post assignments per step"}, {"coachesPerService", "5", "Coaches per human service"},
        {"stepsBeforeCoach", "5", "Steps before the first coach"}, {"sequentialActivities", "5", "Sequential system activities in a process"}, {"bpdFlowObjects", "30", "Flow objects per process"}, {"searchableFields", "25", "Searchable business data fields"}, {"contextAttributes", "150", "Execution context attributes (variables x properties)"},
        {"boAttributes", "50", "Properties per business object"}, {"nestedCshs", "25", "Nested client-side human services per service"}, {"coachControls", "500", "Controls per coach"}, {"coachViewJsLines", "300", "JavaScript lines per coach view"}, {"viewResources", "4", "Included files per coach view"},
        {"assetKb", "300", "Managed asset size (KB)"}, {"toolkits", "25", "Toolkit dependencies"}, {"toolkitDepth", "4", "Toolkit dependency depth"}, {"toolkitSizeMb", "50", "Toolkit size (MB)"}};

    /** Per-rule override; null fields = rule default. */
    public static class Override {
        public Boolean enabled; public Severity severity; public Integer impact;
        boolean isEmpty() { return enabled == null && severity == null && impact == null; }
    }
    public boolean includeToolkits;
    public final Map<String, Integer> severityWeights = new LinkedHashMap<>();
    public final Map<String, Integer> thresholds = new LinkedHashMap<>();
    public final Map<String, Override> rules = new LinkedHashMap<>();
    /** Problems found while reading a document (unknown rule ids, bad values); the offending entries are ignored. */
    public final List<String> warnings = new ArrayList<>();

    public boolean isCustomized() { return !severityWeights.isEmpty() || !thresholds.isEmpty() || !rules.isEmpty(); }
    public boolean enabled(Rule r) { Override o = rules.get(r.id); return o == null || o.enabled == null || o.enabled; }
    public Severity severity(Rule r) { Override o = rules.get(r.id); return o == null || o.severity == null ? r.severity : o.severity; }
    public int impact(Rule r) { Override o = rules.get(r.id); return o == null || o.impact == null ? r.impact : o.impact; }
    public boolean hasOverride(Rule r) { return rules.containsKey(r.id); }
    public int weight(String severity) { Integer w = severityWeights.get(severity); if (w != null) return w; try { return Severity.valueOf(severity).weight; } catch (IllegalArgumentException e) { return 0; } }
    public int weight(Severity s) { return weight(s.name()); }
    public int threshold(String name, int def) { Integer v = thresholds.get(name); return v == null ? def : v; }
    /** Score of a finding of the given severity raised by the rule (severity weight x impact). */
    public int score(Rule r, String severity) { return weight(severity) * impact(r); }

    // ---- JSON -------------------------------------------------------------------------------------------------------
    /** Reads a settings document (full or partial). Unknown rule ids are kept (the document may come from another engine version) but reported in {@link #warnings}; use {@link #prune(List)} to drop them. */
    @SuppressWarnings("unchecked")
    public static RuleSettings fromJson(Map<String, Object> m) {
        RuleSettings s = new RuleSettings(); if (m == null) return s;
        if (m.get("format") != null && !FORMAT.equals(m.get("format"))) s.warnings.add("unexpected format '" + m.get("format") + "' (expected " + FORMAT + ")");
        s.includeToolkits = Boolean.TRUE.equals(m.get("includeToolkits")) || "true".equals(String.valueOf(m.get("includeToolkits")));
        if (m.get("severityWeights") instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("severityWeights")).entrySet()) {
            Severity sev = severityOf(e.getKey()); Integer w = intOf(e.getValue());
            if (sev == null) s.warnings.add("unknown severity '" + e.getKey() + "' in severityWeights"); else if (w == null || w < 0 || w > 100000) s.warnings.add("invalid weight for " + sev.name() + ": " + e.getValue()); else if (w != sev.weight) s.severityWeights.put(sev.name(), w);
        }
        Object th = m.get("thresholds"); if (!(th instanceof Map)) th = m;   // flat form: the threshold names directly in the settings map (older UI)
        for (String[] t : THRESHOLDS) { Object v = ((Map<String, Object>) th).get(t[0]); if (v == null) continue; Integer i = intOf(v); if (i == null || i < 0) s.warnings.add("invalid threshold " + t[0] + ": " + v); else if (i != Integer.parseInt(t[1])) s.thresholds.put(t[0], i); }
        if (th != m) for (String k : ((Map<String, Object>) th).keySet()) if (!isThreshold(k)) s.warnings.add("unknown threshold '" + k + "'");
        if (m.get("rules") instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("rules")).entrySet()) {
            if (!(e.getValue() instanceof Map)) { s.warnings.add("rule " + e.getKey() + ": expected an object"); continue; }
            Map<String, Object> r = (Map<String, Object>) e.getValue(); Override o = new Override();
            if (r.get("enabled") != null) o.enabled = Boolean.TRUE.equals(r.get("enabled")) || "true".equals(String.valueOf(r.get("enabled")));
            if (r.get("severity") != null) { o.severity = severityOf(String.valueOf(r.get("severity"))); if (o.severity == null) s.warnings.add("rule " + e.getKey() + ": unknown severity '" + r.get("severity") + "'"); }
            if (r.get("impact") != null) { o.impact = intOf(r.get("impact")); if (o.impact == null || o.impact < 1 || o.impact > 3) { s.warnings.add("rule " + e.getKey() + ": impact must be 1, 2 or 3"); o.impact = null; } }
            s.rules.put(e.getKey(), o);
        }
        return s;
    }
    public static RuleSettings fromJson(String json) { Object o = json == null || json.trim().isEmpty() ? null : Json.parse(json); if (o != null && !(o instanceof Map)) throw new IllegalArgumentException("settings must be a JSON object"); @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o; return fromJson(m); }

    /** Drops overrides equal to the rule defaults and overrides of rules the engine does not have (reported as warnings). */
    public RuleSettings prune(List<Rule> all) {
        Map<String, Rule> byId = new HashMap<>(); for (Rule r : all) byId.put(r.id, r);
        for (Iterator<Map.Entry<String, Override>> it = rules.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Override> e = it.next(); Rule r = byId.get(e.getKey()); Override o = e.getValue();
            if (r == null) { warnings.add("unknown rule '" + e.getKey() + "' ignored"); it.remove(); continue; }
            if (Boolean.TRUE.equals(o.enabled)) o.enabled = null; if (o.severity == r.severity) o.severity = null; if (o.impact != null && o.impact == r.impact) o.impact = null;
            if (o.isEmpty()) it.remove();
        }
        return this;
    }

    /** Compact form: only what differs from the defaults (stored on disk, embedded in reports). */
    public Map<String, Object> toJson() {
        Map<String, Object> rs = new LinkedHashMap<>();
        for (Map.Entry<String, Override> e : rules.entrySet()) { Override o = e.getValue(); Map<String, Object> m = new LinkedHashMap<>(); if (o.enabled != null) m.put("enabled", o.enabled); if (o.severity != null) m.put("severity", o.severity.name()); if (o.impact != null) m.put("impact", o.impact); rs.put(e.getKey(), m); }
        return Json.obj("format", FORMAT, "version", VERSION, "engine", tca.engine.Analyzer.VERSION, "includeToolkits", includeToolkits, "severityWeights", severityWeights, "thresholds", thresholds, "rules", rs);
    }

    /** Full document for export and for the settings editor: every weight, threshold and rule with its default and effective values. */
    public Map<String, Object> document(List<Rule> all) {
        Map<String, Object> w = new LinkedHashMap<>(); for (Severity s : Severity.values()) w.put(s.name(), weight(s));
        Map<String, Object> th = new LinkedHashMap<>(); List<Object> thList = new ArrayList<>();
        for (String[] t : THRESHOLDS) { int def = Integer.parseInt(t[1]); th.put(t[0], threshold(t[0], def)); thList.add(Json.obj("name", t[0], "label", t[2], "default", def, "value", threshold(t[0], def))); }
        Map<String, Object> rs = new LinkedHashMap<>();
        for (Rule r : all) rs.put(r.id, Json.obj("enabled", enabled(r), "severity", severity(r).name(), "impact", impact(r), "title", r.title, "category", r.category, "defaultSeverity", r.severity.name(), "defaultImpact", r.impact, "confidence", r.confidence, "customized", hasOverride(r)));
        return Json.obj("format", FORMAT, "version", VERSION, "engine", tca.engine.Analyzer.VERSION, "exportedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()), "customized", isCustomized(),
                "includeToolkits", includeToolkits, "severityWeights", w, "thresholds", th, "thresholdList", thList, "rules", rs);
    }

    /** The settings map handed to the analyzer: includeToolkits + flat thresholds (read by RuleContext) + this object under "ruleSettings". */
    public Map<String, Object> toAnalyzerSettings() { Map<String, Object> m = new HashMap<>(); m.put("includeToolkits", includeToolkits); m.putAll(thresholds); m.put("ruleSettings", this); return m; }
    /** The settings of an analyzer settings map: the embedded object, else the map read as a (flat or nested) document. */
    public static RuleSettings of(Map<String, Object> analyzerSettings) {
        if (analyzerSettings == null) return new RuleSettings();
        Object o = analyzerSettings.get("ruleSettings"); if (o instanceof RuleSettings) return (RuleSettings) o;
        RuleSettings s = fromJson(analyzerSettings); s.includeToolkits = Boolean.TRUE.equals(analyzerSettings.get("includeToolkits")); return s;
    }
    /** Merges request-level values (thresholds, rules, weights) over stored settings; the request wins where it says something. */
    public RuleSettings mergedWith(RuleSettings over) {
        RuleSettings s = new RuleSettings(); s.includeToolkits = includeToolkits; s.severityWeights.putAll(severityWeights); s.thresholds.putAll(thresholds); s.rules.putAll(rules); s.warnings.addAll(warnings);
        if (over != null) { s.severityWeights.putAll(over.severityWeights); s.thresholds.putAll(over.thresholds); s.rules.putAll(over.rules); s.warnings.addAll(over.warnings); }
        return s;
    }

    static boolean isThreshold(String k) { for (String[] t : THRESHOLDS) if (t[0].equals(k)) return true; return false; }
    static Severity severityOf(String s) { if (s == null) return null; try { return Severity.valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; } }
    public static Integer intOf(Object v) { if (v instanceof Number) return ((Number) v).intValue(); if (v instanceof String) try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException e) {} return null; }
}

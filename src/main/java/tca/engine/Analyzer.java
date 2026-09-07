package tca.engine;

import java.text.SimpleDateFormat;
import java.util.*;
import tca.model.*;
import tca.rules.*;

/** Runs the rule set over a TWX model and produces a Report (findings ranked by score, then severity, then path). */
public class Analyzer {
    public static final String VERSION = "1.1";
    private final List<Rule> rules;
    public Analyzer() { this(RuleSet.all()); }
    public Analyzer(List<Rule> rules) { this.rules = rules; }
    public List<Rule> rules() { return rules; }
    public final Map<String, Long> ruleTimes = new LinkedHashMap<>();   // last run: rule id -> ms (for tuning)

    public Report analyze(TwxModel twx, Map<String, Object> settings) {
        long t0 = System.currentTimeMillis(); boolean incTk = settings != null && Boolean.TRUE.equals(settings.get("includeToolkits"));
        RuleContext ctx = new RuleContext(twx, settings, incTk); Report r = new Report();
        r.fileName = twx.fileName; r.fileSize = twx.fileSize; r.appName = twx.app.name; r.acronym = twx.app.acronym; r.snapshotName = twx.app.snapshotName; r.snapshotId = twx.app.snapshotId; r.projectId = twx.app.id; r.branchId = twx.app.branchId;
        r.analyzedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()); r.objectCount = twx.app.objects.size(); r.toolkitCount = twx.toolkits.size(); r.typeCounts = twx.app.typeCounts();
        for (TwxPackage p : twx.toolkits.values()) r.toolkits.add(tca.util.Json.obj("name", p.name, "acronym", p.acronym, "snapshot", p.snapshotName, "snapshotId", p.snapshotId, "objects", p.objects.size(), "system", ctx.isSystem(p), "sizeBytes", p.zipSize));
        for (Script s : ctx.allScripts()) { r.scriptCount++; r.scriptLines += s.lines(); }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Rule rule : rules) {
            List<Finding> out = new ArrayList<>(); long rt = System.currentTimeMillis();
            try { rule.check(ctx, out); } catch (RuntimeException e) { Finding f = Finding.of(rule, null, "", "", "", "Rule failed: " + e, ""); f.severity = Severity.INFO.name(); f.score = 0; out.add(f); }
            for (Finding f : out) if (f.packageName.isEmpty()) { f.packageName = twx.app.name; f.packageAcronym = twx.app.acronym; f.packageId = twx.app.snapshotId; }
            ruleTimes.put(rule.id, System.currentTimeMillis() - rt); counts.put(rule.id, out.size()); r.findings.addAll(out);
        }
        Collections.sort(r.findings, new Comparator<Finding>() { public int compare(Finding a, Finding b) { int c = Integer.compare(b.score, a.score); if (c != 0) return c; c = a.severity.compareTo(b.severity); if (c != 0) return c; c = a.objectName.compareTo(b.objectName); return c != 0 ? c : a.ruleId.compareTo(b.ruleId); } });
        for (Rule rule : rules) { Map<String, Object> rj = rule.toJson(); rj.put("count", counts.get(rule.id)); r.rules.add(rj); }
        r.buildVersion = twx.app.buildVersion; r.bawVersion = Report.bawLabel(twx.app.buildVersion);
        r.diagnostics.addAll(twx.diagnostics);
        if (r.bawVersion.isEmpty()) r.diagnostics.add(tca.util.Json.obj("code", "BAW_VERSION_UNKNOWN", "message", "The export does not state the product version (package.xml buildVersion); version-specific advice in the findings is generic."));
        List<Object> skipped = new ArrayList<>(); @SuppressWarnings("unchecked") Map<Script, Object> ast = (Map<Script, Object>) ctx.cache.get("ast");
        if (ast != null) for (Map.Entry<Script, Object> e : ast.entrySet()) if (e.getValue() == null && skipped.size() < 200) skipped.add(tca.util.Json.obj("objectId", e.getKey().object.id, "object", e.getKey().object.name, "location", e.getKey().location, "reason", "javascript-syntax-error"));
        r.coverage.put("scripts", r.scriptCount); r.coverage.put("scriptsParsed", ctx.scriptsParsed); r.coverage.put("scriptsWithSyntaxErrors", ctx.scriptsWithSyntaxErrors); r.coverage.put("skipped", skipped);
        boolean incomplete = false; for (Map<String, Object> d : twx.diagnostics) if ("object-file-missing".equals(d.get("code")) || "toolkit-unreadable".equals(d.get("code"))) incomplete = true;
        r.coverage.put("status", incomplete ? "partial" : ctx.scriptsWithSyntaxErrors > 0 ? "partial" : "complete");
        r.toolkitUsage = ToolkitUsage.compute(ctx);
        r.durationMs = System.currentTimeMillis() - t0; return r;
    }
}

package tca.enterprise;

import java.util.*;
import tca.engine.Pdf;

/** One-page management summary of an analysis: health, verdict, policy, trend over the previous analyses of the application, top risks and top rules. */
public final class ExecutivePdf {
    private ExecutivePdf() {}
    @SuppressWarnings("unchecked")
    public static byte[] render(Map<String, Object> report, List<Map<String, Object>> trend, String workspace) {
        Map<String, Object> app = (Map<String, Object>) report.get("app"), s = (Map<String, Object>) report.get("summary"), inv = (Map<String, Object>) report.get("inventory"), gate = (Map<String, Object>) report.get("gate"), pol = (Map<String, Object>) report.get("policy");
        Pdf p = new Pdf(false); p.footer = "TWX Code Analyzer - executive summary - " + app.get("name") + " " + app.get("snapshot");
        p.heading("Executive summary", 18); p.paragraph(app.get("name") + " (" + app.get("acronym") + ")  snapshot " + app.get("snapshot") + "  |  analyzed " + report.get("analyzedAt") + (workspace == null || workspace.isEmpty() ? "" : "  |  workspace " + workspace) + (pol == null ? "" : "  |  policy " + pol.get("name") + (pol.get("version") == null ? "" : " v" + pol.get("version"))), 9, 0.35);
        Map<String, Object> bs = (Map<String, Object>) s.get("bySeverity"); String verdict = gate == null || gate.get("passed") == null ? "no quality gate" : Boolean.TRUE.equals(gate.get("passed")) ? "PASSED" : "FAILED";
        p.heading("Health " + s.get("healthScore") + " / 100   -   Verdict: " + verdict, 14);
        List<String[]> row = new ArrayList<>(); row.add(new String[] { String.valueOf(s.get("findings")), String.valueOf(bs.get("CRITICAL")), String.valueOf(bs.get("MAJOR")), String.valueOf(bs.get("MINOR")), String.valueOf(bs.get("INFO")), String.valueOf(s.get("accepted") == null ? 0 : s.get("accepted")), String.valueOf(s.get("score")), String.valueOf(inv.get("objects")), String.valueOf(inv.get("scripts")) });
        p.table(new String[] { "Open findings", "Critical", "Major", "Minor", "Info", "Accepted", "Weighted score", "Artifacts", "Scripts" }, new double[] { .12, .1, .1, .1, .1, .1, .13, .12, .13 }, row, 9);
        if (gate != null && gate.get("checks") instanceof List && !((List<Object>) gate.get("checks")).isEmpty()) {
            p.heading("Quality gate", 12); List<String[]> gr = new ArrayList<>();
            for (Map<String, Object> c : (List<Map<String, Object>>) gate.get("checks")) gr.add(new String[] { String.valueOf(c.get("label")), String.valueOf(c.get("limit")), String.valueOf(c.get("actual")), Boolean.TRUE.equals(c.get("passed")) ? "passed" : "failed" });
            p.table(new String[] { "Check", "Limit", "Actual", "Result" }, new double[] { .5, .15, .15, .2 }, gr, 9);
        }
        if (trend != null && trend.size() > 1) {
            p.heading("Trend (" + trend.size() + " analyses of this application)", 12); List<String[]> tr = new ArrayList<>();
            for (Map<String, Object> m : trend) tr.add(new String[] { String.valueOf(m.get("analyzedAt")), String.valueOf(m.get("snapshot")), String.valueOf(m.get("health")), String.valueOf(m.get("findings")), m.get("gate") == null ? "-" : Boolean.TRUE.equals(m.get("gate")) ? "passed" : "failed" });
            p.table(new String[] { "Analyzed", "Snapshot", "Health", "Open findings", "Gate" }, new double[] { .25, .3, .15, .15, .15 }, tr, 9);
        }
        // top risks: artifacts by the sum of their open finding scores
        Map<String, double[]> byArtifact = new LinkedHashMap<>(); Map<String, String> label = new HashMap<>();
        for (Map<String, Object> f : (List<Map<String, Object>>) report.get("findings")) { if (Boolean.TRUE.equals(f.get("accepted"))) continue; String k = f.get("objectTypeLabel") + " " + f.get("objectName"); double[] v = byArtifact.get(k); if (v == null) { v = new double[2]; byArtifact.put(k, v); label.put(k, String.valueOf(f.get("objectTypeLabel")).isEmpty() ? "Application" : k); } v[0] += ((Number) f.get("score")).doubleValue(); v[1]++; }
        List<Map.Entry<String, double[]>> arts = new ArrayList<>(byArtifact.entrySet()); Collections.sort(arts, new Comparator<Map.Entry<String, double[]>>() { public int compare(Map.Entry<String, double[]> a, Map.Entry<String, double[]> b) { return Double.compare(b.getValue()[0], a.getValue()[0]); } });
        p.heading("Top risks (artifacts by weighted score)", 12); List<String[]> ar = new ArrayList<>(); int i = 0; for (Map.Entry<String, double[]> e : arts) { if (i++ >= 10) break; ar.add(new String[] { String.valueOf(i), label.get(e.getKey()), String.valueOf((int) e.getValue()[0]), String.valueOf((int) e.getValue()[1]) }); }
        p.table(new String[] { "#", "Artifact", "Weighted score", "Open findings" }, new double[] { .06, .58, .18, .18 }, ar, 9);
        List<Map<String, Object>> rules = new ArrayList<>(); for (Map<String, Object> r : (List<Map<String, Object>>) report.get("rules")) if (((Number) r.get("count")).intValue() > 0) rules.add(r);
        Collections.sort(rules, new Comparator<Map<String, Object>>() { public int compare(Map<String, Object> a, Map<String, Object> b) { return ((Number) b.get("count")).intValue() - ((Number) a.get("count")).intValue(); } });
        p.heading("Most frequent rules", 12); List<String[]> rr = new ArrayList<>(); i = 0; for (Map<String, Object> r : rules) { if (i++ >= 10) break; rr.add(new String[] { r.get("id") + " " + r.get("title"), String.valueOf(r.get("severity")), String.valueOf(r.get("count")) }); }
        p.table(new String[] { "Rule", "Severity", "Findings" }, new double[] { .66, .17, .17 }, rr, 9);
        p.paragraph("Static analysis is advisory: findings are confirmed in the application context before production logic is changed. Accepted findings are excluded from the health score and the verdict. Generated by TWX Code Analyzer.", 8, 0.35);
        return p.build();
    }
}

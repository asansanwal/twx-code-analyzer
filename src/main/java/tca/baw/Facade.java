package tca.baw;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import tca.engine.*;
import tca.model.*;
import tca.parse.TwxLoader;
import tca.rules.*;
import tca.search.Searcher;
import tca.util.Json;

/**
 * Entry point of the BAW / CP4BA process application: one Java integration method, JSON in, JSON out.
 * <pre>call(op, argsJson, folder)</pre>
 * The TWX files live in a folder on the server (environment variable twxFolder); the engine loads them on demand and keeps the
 * last few parsed models in memory. Each analysis writes its full report to {folder}/.tca/{reportKey}.json so that the process
 * instance only has to remember the summary. Errors never throw: the result is {"error": "..."}.
 *
 * Operations (args in braces):
 *  ping                                          version, folder, java
 *  files                                         TWX files in the folder
 *  analyze    {fileName, includeToolkits, settings?}   runs the rules with the stored settings (+ optional overrides), stores the report, returns the summary (with reportKey)
 *  report     {reportKey}                        the stored report (findings, rules, inventory)
 *  objects    {fileName, toolkits}               object tree of the TWX
 *  object     {fileName, objectId, xml}          object detail + diagram
 *  search     {fileName, q, regex, case, scope, types, toolkits, limit}
 *  compare    {fileA, reportA, fileB, reportB}   object differences and findings delta
 *  rules                                         rule catalogue with the effective (customised) severity / impact / enabled
 *  settings                                      full rule settings document (weights, thresholds, every rule) - the export file format
 *  saveSettings {settings}                       stores a settings document (object or JSON text, full or partial, validated) in {folder}/.tca/settings.json; returns {saved, warnings, settings}
 *  resetSettings                                 back to the built-in defaults
 *  toolkitUsage {fileName}                      toolkit usage (used / possible / none per toolkit artifact) - also part of every stored report
 *  pdf        {reportKey, sev, cat, rule, type, conf, q, sort}   findings PDF (same filter as the UI) as {fileName, base64}
 *  toolkitUsagePdf {reportKey, keys}             toolkit usage PDF for the selected toolkit keys ("k1|k2", empty = all) as {fileName, base64}
 *  export     {url, user, password, snapshotId, fileName}   exports a Process Center snapshot into the folder
 *  deleteFile {fileName}                         removes a TWX file (and its stored reports)
 */
public class Facade {
    public static final String VERSION = Analyzer.VERSION;
    static final int CACHE_SIZE = 3;
    static final Map<String, Loaded> cache = new LinkedHashMap<String, Loaded>(8, 0.75f, true) { protected boolean removeEldestEntry(Map.Entry<String, Loaded> e) { return size() > CACHE_SIZE; } };
    static final Analyzer analyzer = new Analyzer();
    static class Loaded { long modified; TwxModel model; RuleContext context; }
    /** Marker for results that are already JSON text (stored reports) and must not be re-serialized. */
    static final class Raw { final String json; Raw(String j) { json = j; } }

    public Facade() {}

    public String call(String op, String args, String folder) {
        try {
            Object r = dispatch(op == null ? "" : op.trim(), parseArgs(args), folderOf(folder));
            return r instanceof Raw ? ((Raw) r).json : Json.write(r);
        } catch (Throwable e) { return Json.write(Json.obj("error", message(e))); }
    }

    @SuppressWarnings("unchecked")
    Object dispatch(String op, Map<String, Object> a, File folder) throws Exception {
        if (op.equals("ping")) return Json.obj("version", VERSION, "folder", folder.getAbsolutePath(), "java", System.getProperty("java.version"), "cached", cache.size());
        if (op.equals("files")) return files(folder);
        if (op.equals("analyze")) return analyze(folder, str(a, "fileName"), bool(a, "includeToolkits"), a.get("settings"));
        if (op.equals("report")) return new Raw(readReport(folder, str(a, "reportKey")));
        if (op.equals("objects")) return ObjectViews.objects(context(folder, str(a, "fileName")), bool(a, "toolkits"));
        if (op.equals("object")) { RuleContext c = context(folder, str(a, "fileName")); TwxObject o = c.twx.find(str(a, "objectId")); if (o == null) throw new IllegalArgumentException("unknown object " + str(a, "objectId"));
            return Json.obj("detail", ObjectViews.objectDetail(c, o, bool(a, "xml")), "diagram", ObjectViews.diagram(c, o)); }
        if (op.equals("search")) { Set<String> types = new HashSet<>(); for (String t : str(a, "types").split(",")) if (!t.trim().isEmpty()) types.add(t.trim());
            int limit = a.get("limit") instanceof Number ? ((Number) a.get("limit")).intValue() : 500;
            return Searcher.search(context(folder, str(a, "fileName")), str(a, "q"), bool(a, "regex"), bool(a, "case"), a.containsKey("scope") ? str(a, "scope") : "all", types, bool(a, "toolkits"), limit); }
        if (op.equals("compare")) return compare(folder, str(a, "fileA"), str(a, "reportA"), str(a, "fileB"), str(a, "reportB"));
        if (op.equals("pdf")) { Report r = reportObject(folder, str(a, "reportKey")); return Json.obj("fileName", "report-" + fileTag(r) + ".pdf", "base64", java.util.Base64.getEncoder().encodeToString(PdfReport.findings(r, PdfReport.filterFindings(r, strMap(a)), PdfReport.describe(strMap(a))))); }
        if (op.equals("toolkitUsagePdf")) { Report r = reportObject(folder, str(a, "reportKey")); Set<String> keys = new HashSet<>(); for (String k : str(a, "keys").split("\\|")) if (!k.isEmpty()) keys.add(k); @SuppressWarnings("unchecked") List<Map<String, Object>> all = (List<Map<String, Object>>) r.toolkitUsage.get("toolkits"); List<Map<String, Object>> sel = new ArrayList<>(); if (all != null) for (Map<String, Object> t : all) if (keys.isEmpty() || keys.contains(String.valueOf(t.get("key")))) sel.add(t); return Json.obj("fileName", "toolkit-usage-" + fileTag(r) + ".pdf", "base64", java.util.Base64.getEncoder().encodeToString(PdfReport.toolkitUsage(r, sel))); }
        if (op.equals("toolkitUsage")) return ToolkitUsage.compute(context(folder, str(a, "fileName")));
        if (op.equals("rules")) { RuleSettings rs = settings(folder); List<Object> l = new ArrayList<>(); for (Rule r : analyzer.rules()) { Map<String, Object> m = r.toJson(); m.put("defaultSeverity", r.severity.name()); m.put("severity", rs.severity(r).name()); m.put("impact", rs.impact(r)); m.put("enabled", rs.enabled(r)); m.put("customized", rs.hasOverride(r)); l.add(m); } return l; }
        if (op.equals("settings")) return settings(folder).document(analyzer.rules());
        if (op.equals("saveSettings")) { Object v = a.get("settings"); RuleSettings rs = (v instanceof Map ? RuleSettings.fromJson((Map<String, Object>) v) : RuleSettings.fromJson(v == null ? "" : String.valueOf(v))).prune(analyzer.rules()); saveSettings(folder, rs); return Json.obj("saved", true, "customized", rs.isCustomized(), "warnings", rs.warnings, "settings", rs.document(analyzer.rules())); }
        if (op.equals("resetSettings")) { saveSettings(folder, new RuleSettings()); return Json.obj("saved", true, "customized", false, "warnings", new ArrayList<Object>(), "settings", new RuleSettings().document(analyzer.rules())); }
        if (op.equals("export")) return ProcessCenterExport.export(str(a, "url"), str(a, "user"), str(a, "password"), str(a, "snapshotId"), new File(folder, safeName(str(a, "fileName"))));
        if (op.equals("deleteFile")) return deleteFile(folder, str(a, "fileName"));
        throw new IllegalArgumentException("unknown operation '" + op + "'");
    }

    // ---- files ------------------------------------------------------------------------------------------------------
    static Map<String, Object> files(File folder) {
        List<Object> l = new ArrayList<>(); File[] fs = folder.listFiles();
        if (fs != null) { Arrays.sort(fs); for (File f : fs) if (f.isFile() && f.getName().toLowerCase().endsWith(".twx")) l.add(Json.obj("name", f.getName(), "size", f.length(), "modified", iso(f.lastModified()))); }
        return Json.obj("folder", folder.getAbsolutePath(), "files", l);
    }
    static Map<String, Object> deleteFile(File folder, String name) throws IOException {
        File f = new File(folder, safeName(name)); if (!f.isFile()) throw new FileNotFoundException(name);
        Files.delete(f.toPath()); synchronized (cache) { cache.remove(name); }
        int reports = 0; File[] rs = new File(folder, ".tca").listFiles(); String prefix = keyPrefix(name);
        if (rs != null) for (File r : rs) if (r.getName().startsWith(prefix + "-") && r.getName().endsWith(".json") && r.delete()) reports++;
        return Json.obj("deleted", name, "reports", reports);
    }

    // ---- model cache ------------------------------------------------------------------------------------------------
    static Loaded load(File folder, String name) throws IOException {
        File f = new File(folder, safeName(name)); if (!f.isFile()) throw new FileNotFoundException("TWX file not found: " + name);
        synchronized (cache) {
            Loaded l = cache.get(name); if (l != null && l.modified == f.lastModified()) return l;
            l = new Loaded(); l.modified = f.lastModified(); l.model = TwxLoader.load(f); l.context = new RuleContext(l.model, new HashMap<String, Object>(), true); cache.put(name, l); return l;
        }
    }
    static RuleContext context(File folder, String name) throws IOException { return load(folder, name).context; }

    // ---- rule settings ----------------------------------------------------------------------------------------------
    static File settingsFile(File folder) { return new File(new File(folder, ".tca"), "settings.json"); }
    static RuleSettings settings(File folder) { File f = settingsFile(folder); if (!f.isFile()) return new RuleSettings(); try { return RuleSettings.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)); } catch (Exception e) { return new RuleSettings(); } }
    static void saveSettings(File folder, RuleSettings s) throws IOException { File f = settingsFile(folder); if (!s.isCustomized()) { f.delete(); return; } f.getParentFile().mkdirs(); Files.write(f.toPath(), Json.writePretty(s.toJson()).getBytes(StandardCharsets.UTF_8)); }

    // ---- analysis ---------------------------------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    static Map<String, Object> analyze(File folder, String name, boolean includeToolkits, Object overrides) throws IOException {
        long t0 = System.currentTimeMillis(); Loaded l = load(folder, name); RuleSettings rs = settings(folder);
        if (overrides instanceof Map) rs = rs.mergedWith(RuleSettings.fromJson((Map<String, Object>) overrides).prune(analyzer.rules())); else if (overrides != null && !String.valueOf(overrides).trim().isEmpty()) rs = rs.mergedWith(RuleSettings.fromJson(String.valueOf(overrides)).prune(analyzer.rules()));
        rs.includeToolkits = includeToolkits; Map<String, Object> settings = rs.toAnalyzerSettings();
        Report r; synchronized (analyzer) { r = analyzer.analyze(l.model, settings); }
        String key = keyPrefix(name) + "-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()); r.id = key;
        Map<String, Object> json = r.toJson(); File dir = new File(folder, ".tca"); dir.mkdirs();
        Files.write(new File(dir, key + ".json").toPath(), Json.write(json).getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) json.get("summary");
        return Json.obj("reportKey", key, "fileName", name, "fileSize", l.model.fileSize, "fileModified", iso(l.modified), "analyzedAt", r.analyzedAt, "engineVersion", r.engineVersion, "includeToolkits", includeToolkits,
                "app", r.appName, "acronym", r.acronym, "snapshot", r.snapshotName, "projectId", r.projectId, "objects", r.objectCount, "toolkits", r.toolkitCount,
                "findings", r.findings.size(), "score", summary.get("score"), "health", summary.get("healthScore"), "bySeverity", summary.get("bySeverity"), "customized", rs.isCustomized(), "durationMs", System.currentTimeMillis() - t0);
    }
    /** Stored report rebuilt as a Report object (findings, rules, toolkit usage, settings) for the PDF renderers. */
    static Report reportObject(File folder, String key) throws IOException { Report r = Report.fromJson(readReport(folder, key)); r.id = key; return r; }
    static String fileTag(Report r) { return ((r.acronym.isEmpty() ? r.appName : r.acronym) + "-" + r.snapshotName).replaceAll("[^A-Za-z0-9._-]+", "_"); }
    static Map<String, String> strMap(Map<String, Object> a) { Map<String, String> m = new HashMap<>(); for (Map.Entry<String, Object> e : a.entrySet()) if (e.getValue() != null) m.put(e.getKey(), String.valueOf(e.getValue())); return m; }
    static String readReport(File folder, String key) throws IOException {
        if (!key.matches("[A-Za-z0-9._\\-]+")) throw new IllegalArgumentException("bad report key");
        File f = new File(new File(folder, ".tca"), key + ".json"); if (!f.isFile()) throw new FileNotFoundException("stored report " + key + " not found - analyze the file again");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> compare(File folder, String fileA, String reportA, String fileB, String reportB) throws IOException {
        Map<String, Object> ra = reportOrAnalyze(folder, fileA, reportA), rb = reportOrAnalyze(folder, fileB, reportB);
        Map<String, Object> res = Diff.objects(load(folder, fileA).model, load(folder, fileB).model);
        res.put("findings", Diff.findings(findings(ra), findings(rb))); res.put("summaryBefore", ra.get("summary")); res.put("summaryAfter", rb.get("summary"));
        return res;
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> reportOrAnalyze(File folder, String file, String key) throws IOException {
        if (key != null && !key.isEmpty()) { try { return (Map<String, Object>) Json.parse(readReport(folder, key)); } catch (FileNotFoundException e) { /* stored report gone: analyze again */ } }
        Report r; synchronized (analyzer) { r = analyzer.analyze(load(folder, file).model, settings(folder).toAnalyzerSettings()); } return r.toJson();
    }
    @SuppressWarnings("unchecked")
    static List<Finding> findings(Map<String, Object> report) { List<Finding> l = new ArrayList<>(); for (Object o : (List<Object>) report.get("findings")) l.add(Finding.fromJson((Map<String, Object>) o)); return l; }

    // ---- helpers ----------------------------------------------------------------------------------------------------
    static File folderOf(String folder) {
        File f = folder == null || folder.trim().isEmpty() ? new File(System.getProperty("java.io.tmpdir"), "twx-code-analyzer") : new File(folder.trim());
        f.mkdirs(); return f;
    }
    /** File names are plain names inside the folder: no path separators, no parent references. */
    static String safeName(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..") || name.startsWith(".")) throw new IllegalArgumentException("invalid file name '" + name + "'");
        return name;
    }
    static String keyPrefix(String fileName) { String b = fileName.toLowerCase().endsWith(".twx") ? fileName.substring(0, fileName.length() - 4) : fileName; return b.replaceAll("[^A-Za-z0-9._]+", "_"); }
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseArgs(String args) { if (args == null || args.trim().isEmpty()) return new HashMap<>(); Object o = Json.parse(args); if (!(o instanceof Map)) throw new IllegalArgumentException("args must be a JSON object"); return (Map<String, Object>) o; }
    static String str(Map<String, Object> a, String k) { Object v = a.get(k); return v == null ? "" : String.valueOf(v); }
    static boolean bool(Map<String, Object> a, String k) { Object v = a.get(k); return v instanceof Boolean ? (Boolean) v : v != null && (String.valueOf(v).equals("true") || String.valueOf(v).equals("1")); }
    static String iso(long millis) { java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); return f.format(new Date(millis)); }
    static String message(Throwable e) { String m = e.getMessage(); return (m == null || m.isEmpty() ? e.getClass().getSimpleName() : (e instanceof RuntimeException && !(e instanceof IllegalArgumentException) ? e.getClass().getSimpleName() + ": " : "") + m); }
}

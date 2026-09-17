package tca.parse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import tca.model.*;
import tca.util.Xml;

/** Reads a TWX (zip) into a TwxModel: package.xml, every object XML, the bundled toolkits (toolkits/*.zip, recursively read). */
public final class TwxLoader {
    private TwxLoader() {}
    private static final Pattern OBJECT = Pattern.compile("<object id=\"([^\"]*)\" versionId=\"([^\"]*)\" name=\"([^\"]*)\" type=\"([^\"]*)\"");

    public static TwxModel load(File f) throws IOException { byte[] b = java.nio.file.Files.readAllBytes(f.toPath()); TwxModel m = load(b); m.fileName = f.getName(); return m; }

    public static TwxModel load(byte[] bytes) throws IOException {
        TwxModel model = new TwxModel(); model.fileSize = bytes.length;
        Map<String, byte[]> entries = readZip(bytes);
        model.app = parsePackage(entries, bytes.length, model);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().startsWith("toolkits/") && e.getKey().endsWith(".zip")) {
                try {
                    Map<String, byte[]> te = readZip(e.getValue()); if (!te.containsKey("META-INF/package.xml")) { model.diagnostic("toolkit-without-manifest", e.getKey() + " has no META-INF/package.xml and was skipped"); continue; }
                    TwxPackage tp = parsePackage(te, e.getValue().length, model); model.toolkits.put(tp.snapshotId, tp);
                } catch (Exception ex) { model.diagnostic("toolkit-unreadable", e.getKey() + " could not be read: " + ex); }
            }
        }
        for (TwxPackage p : model.allPackages()) nameEmbeddedProcesses(p);
        return model;
    }
    /** Embedded subprocess BPDs are exported with their GUID as name; label them "<activity> (in <parent process>)" from the activity that embeds them. */
    static void nameEmbeddedProcesses(TwxPackage p) {
        List<TwxObject> bpds = p.byType("bpd");
        for (TwxObject o : bpds) {
            if (!o.name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) continue;
            String ref = "<embeddedProcessId>" + o.id + "</embeddedProcessId>";
            for (TwxObject b : bpds) { int i = b.xml.indexOf(ref); if (b == o || i < 0) continue;
                int fo = b.xml.lastIndexOf("<flowObject", i); String activity = fo >= 0 ? Xml.text(b.xml.substring(fo, i), "name") : "";
                o.name = (activity.isEmpty() ? "embedded process" : activity) + " (in " + b.name + ")"; break; }
        }
    }

    static Map<String, byte[]> readZip(byte[] bytes) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e; byte[] buf = new byte[65536];
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream(); int n; while ((n = z.read(buf)) > 0) bo.write(buf, 0, n); out.put(e.getName(), bo.toByteArray());
            }
        }
        return out;
    }

    static TwxPackage parsePackage(Map<String, byte[]> entries, long zipSize, TwxModel model) {
        TwxPackage p = new TwxPackage(); p.zipSize = zipSize;
        String pkg = new String(entries.get("META-INF/package.xml"), StandardCharsets.UTF_8);
        int r0 = pkg.indexOf("<p:package"), r1 = r0 >= 0 ? pkg.indexOf('>', r0) : -1;
        if (r1 > r0) { String root = pkg.substring(r0, r1); p.buildVersion = Xml.attrOf(root, "buildVersion"); p.buildId = Xml.attrOf(root, "buildId"); }
        int t0 = pkg.indexOf("<target>"), t1 = pkg.indexOf("</target>"); String target = t0 >= 0 && t1 > t0 ? pkg.substring(t0, t1) : pkg;
        String pr = Xml.openTag(target, "project", 0);
        if (!pr.isEmpty()) { p.id = Xml.attrOf(pr, "id"); p.name = Xml.attrOf(pr, "name"); p.acronym = Xml.attrOf(pr, "shortName"); p.toolkit = "true".equals(Xml.attrOf(pr, "isToolkit")); p.description = Xml.attrOf(pr, "description"); }
        String sn = Xml.openTag(target, "snapshot", 0); if (!sn.isEmpty()) { p.snapshotId = Xml.attrOf(sn, "id"); p.snapshotName = Xml.attrOf(sn, "name"); p.snapshotDate = Xml.attrOf(sn, "originalCreationDate"); }
        String br = Xml.openTag(target, "branch", 0); if (!br.isEmpty()) { p.branchId = Xml.attrOf(br, "id"); p.branchName = Xml.attrOf(br, "name"); }
        for (String[] dep : Xml.elements(pkg, "dependency")) {
            TwxPackage.Dependency d = new TwxPackage.Dependency(); String body = dep[1];
            String x = Xml.openTag(body, "project", 0); if (!x.isEmpty()) { d.projectId = Xml.attrOf(x, "id"); d.name = Xml.attrOf(x, "name"); d.acronym = Xml.attrOf(x, "shortName"); d.system = "true".equals(Xml.attrOf(x, "isSystem")); }
            String y = Xml.openTag(body, "snapshot", 0); if (!y.isEmpty()) { d.snapshotId = Xml.attrOf(y, "id"); d.snapshotName = Xml.attrOf(y, "name"); }
            String z = Xml.openTag(body, "branch", 0); if (!z.isEmpty()) d.branchId = Xml.attrOf(z, "id");
            p.dependencies.add(d);
        }
        Matcher om = OBJECT.matcher(pkg);
        while (om.find()) {
            String id = om.group(1), versionId = om.group(2); byte[] ob = entries.get("objects/" + id + ".xml"); if (ob == null) ob = entries.get("objects/" + versionId + ".xml");
            if (ob == null && model != null) model.diagnostic("object-file-missing", p.acronym + ": objects/" + id + ".xml (" + om.group(4) + " '" + Xml.unescape(om.group(3)) + "') is not in the export");
            p.objects.put(id, new TwxObject(id, Xml.unescape(om.group(3)), om.group(4), versionId, ob == null ? "" : new String(ob, StandardCharsets.UTF_8), p));
        }
        for (String n : entries.keySet()) if (n.startsWith("files/")) {
            p.files.add(n); String[] parts = n.split("/"); TwxObject asset = parts.length >= 2 ? p.objects.get(parts[1]) : null;
            if (asset != null && asset.name.toLowerCase().endsWith(".js") && entries.get(n).length < 3_000_000) p.jsFiles.put(asset.id, new String(entries.get(n), StandardCharsets.UTF_8));
        }
        return p;
    }
}

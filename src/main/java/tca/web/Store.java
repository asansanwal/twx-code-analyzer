package tca.web;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import tca.util.Json;

/** File based history: data/<id>/report.json + upload.twx + meta.json. No database. */
public class Store {
    public final File root;
    public Store(File root) { this.root = root; root.mkdirs(); }
    public String newId() { return new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-" + Integer.toHexString(new Random().nextInt(0xffff)); }
    public File dir(String id) { if (!id.matches("[A-Za-z0-9\\-_]+")) throw new IllegalArgumentException("bad id"); return new File(root, id); }
    public void save(String id, byte[] twx, String reportJson, Map<String, Object> meta) throws IOException { File d = dir(id); d.mkdirs(); Files.write(new File(d, "upload.twx").toPath(), twx); Files.write(new File(d, "report.json").toPath(), reportJson.getBytes(StandardCharsets.UTF_8)); Files.write(new File(d, "meta.json").toPath(), Json.writePretty(meta).getBytes(StandardCharsets.UTF_8)); }
    public String report(String id) throws IOException { return new String(Files.readAllBytes(new File(dir(id), "report.json").toPath()), StandardCharsets.UTF_8); }
    public byte[] twx(String id) throws IOException { return Files.readAllBytes(new File(dir(id), "upload.twx").toPath()); }
    public boolean exists(String id) { return new File(dir(id), "report.json").exists(); }
    public void delete(String id) { File d = dir(id); File[] fs = d.listFiles(); if (fs != null) for (File f : fs) f.delete(); d.delete(); }
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> l = new ArrayList<>(); File[] ds = root.listFiles(); if (ds == null) return l;
        for (File d : ds) { File m = new File(d, "meta.json"); if (!m.exists()) continue; try { Map<String, Object> meta = (Map<String, Object>) Json.parse(new String(Files.readAllBytes(m.toPath()), StandardCharsets.UTF_8)); meta.put("id", d.getName()); l.add(meta); } catch (Exception e) {} }
        Collections.sort(l, new Comparator<Map<String, Object>>() { public int compare(Map<String, Object> a, Map<String, Object> b) { return String.valueOf(b.get("id")).compareTo(String.valueOf(a.get("id"))); } });
        return l;
    }
}

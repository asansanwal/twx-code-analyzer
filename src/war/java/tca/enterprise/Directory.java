package tca.enterprise;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import tca.util.Json;

/** Enterprise data kept as JSON files under {@code <dataDir>/enterprise/}: teams, policies, Process Center connections; per workspace: workspace.json and suppressions.json. */
public class Directory {
    final File root, teamsFile, policiesDir, connectionsFile, auditFile;
    public Directory(File dataDir) { root = new File(dataDir, "enterprise"); teamsFile = new File(root, "teams.json"); policiesDir = new File(root, "policies"); connectionsFile = new File(root, "connections.json"); auditFile = new File(root, "audit.log"); }

    // ---- generic JSON files -----------------------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    static Map<String, Object> readObject(File f) { if (!f.isFile()) return new LinkedHashMap<>(); try { Object o = Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)); return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>(); } catch (Exception e) { System.err.println(f + " ignored: " + e); return new LinkedHashMap<>(); } }
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> readList(File f) { List<Map<String, Object>> l = new ArrayList<>(); if (!f.isFile()) return l; try { Object o = Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)); if (o instanceof List) for (Object e : (List<Object>) o) if (e instanceof Map) l.add((Map<String, Object>) e); } catch (Exception e) { System.err.println(f + " ignored: " + e); } return l; }
    static synchronized void write(File f, Object v) throws IOException { f.getParentFile().mkdirs(); File tmp = new File(f.getPath() + ".tmp"); Files.write(tmp.toPath(), Json.writePretty(v).getBytes(StandardCharsets.UTF_8)); restrict(tmp); Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    /** Owner-only permissions where the file system supports them (accounts, sessions, tokens, connection passwords). */
    static void restrict(File f) { try { Files.setPosixFilePermissions(f.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); } catch (Exception e) { /* not a POSIX file system */ } }
    public static String safeId(String s) { if (s == null) return ""; String id = s.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", ""); return id.length() > 64 ? id.substring(0, 64) : id; }
    static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v == null ? "" : String.valueOf(v); }
    @SuppressWarnings("unchecked") static List<String> strings(Object v) { List<String> l = new ArrayList<>(); if (v instanceof List) for (Object o : (List<Object>) v) { String s = String.valueOf(o).trim(); if (!s.isEmpty()) l.add(s); } else if (v instanceof String) for (String s : ((String) v).split("[,;\\s]+")) if (!s.isEmpty()) l.add(s); return l; }

    // ---- teams ------------------------------------------------------------------------------------------------------
    public List<Map<String, Object>> teams() { return readList(teamsFile); }
    public Map<String, Object> team(String id) { for (Map<String, Object> t : teams()) if (str(t, "id").equals(id)) return t; return null; }
    public Map<String, Object> saveTeam(Map<String, Object> t) throws IOException {
        String id = safeId(str(t, "id").isEmpty() ? str(t, "name") : str(t, "id")); if (id.isEmpty()) throw new IllegalArgumentException("team name is required");
        Map<String, Object> clean = Json.obj("id", id, "name", str(t, "name").isEmpty() ? id : str(t, "name"), "description", str(t, "description"), "members", strings(t.get("members")), "admins", strings(t.get("admins")), "group", str(t, "group"));
        List<Map<String, Object>> all = teams(); boolean found = false; for (int i = 0; i < all.size(); i++) if (str(all.get(i), "id").equals(id)) { all.set(i, clean); found = true; } if (!found) all.add(clean);
        write(teamsFile, all); return clean;
    }
    public boolean deleteTeam(String id) throws IOException { List<Map<String, Object>> all = teams(); boolean removed = false; for (Iterator<Map<String, Object>> it = all.iterator(); it.hasNext();) if (str(it.next(), "id").equals(id)) { it.remove(); removed = true; } if (removed) write(teamsFile, all); return removed; }
    /** Teams the user belongs to (member, admin, or through a directory group); admins see every team. */
    public List<Map<String, Object>> teamsOf(String user, Set<String> groups, boolean admin) { List<Map<String, Object>> l = new ArrayList<>(); for (Map<String, Object> t : teams()) if (admin || isMember(t, user, groups)) l.add(t); return l; }
    public static boolean isMember(Map<String, Object> t, String user, Set<String> groups) { if (user == null) return false; if (strings(t.get("members")).contains(user) || strings(t.get("admins")).contains(user)) return true; String g = str(t, "group"); return !g.isEmpty() && groups != null && groups.contains(g); }
    public static boolean isTeamAdmin(Map<String, Object> t, String user) { return user != null && strings(t.get("admins")).contains(user); }

    // ---- policies ---------------------------------------------------------------------------------------------------
    public List<Map<String, Object>> policies() { List<Map<String, Object>> l = new ArrayList<>(); File[] fs = policiesDir.listFiles(); if (fs != null) { Arrays.sort(fs); for (File f : fs) if (f.getName().endsWith(".json")) { Map<String, Object> p = readObject(f); if (!p.isEmpty()) l.add(p); } } return l; }
    public Map<String, Object> policy(String id) { if (id == null || id.isEmpty() || !id.equals(safeId(id))) return null; Map<String, Object> p = readObject(new File(policiesDir, id + ".json")); return p.isEmpty() ? null : p; }
    public Map<String, Object> savePolicy(Map<String, Object> p, String by) throws IOException {
        String id = safeId(str(p, "id").isEmpty() ? str(p, "name") : str(p, "id")); if (id.isEmpty() || id.equals("default") || id.equals("custom")) throw new IllegalArgumentException("policy id '" + id + "' is reserved or empty");
        Map<String, Object> old = policy(id); int version = old == null ? 1 : ((Number) (old.get("version") == null ? 0 : old.get("version"))).intValue() + 1;
        Map<String, Object> clean = Json.obj("id", id, "name", str(p, "name").isEmpty() ? id : str(p, "name"), "description", str(p, "description"), "version", version, "updatedAt", now(), "updatedBy", by == null ? "" : by,
                "settings", p.get("settings") instanceof Map ? p.get("settings") : new LinkedHashMap<String, Object>(), "gates", p.get("gates") instanceof Map ? p.get("gates") : new LinkedHashMap<String, Object>());
        write(new File(policiesDir, id + ".json"), clean); return clean;
    }
    public boolean deletePolicy(String id) { if (id == null || !id.equals(safeId(id))) return false; return new File(policiesDir, id + ".json").delete(); }

    // ---- Process Center connections ---------------------------------------------------------------------------------
    public List<Map<String, Object>> connections() { return readList(connectionsFile); }
    public Map<String, Object> connection(String id) { for (Map<String, Object> c : connections()) if (str(c, "id").equals(id)) return c; return null; }
    public Map<String, Object> saveConnection(Map<String, Object> c) throws IOException { return saveConnectionIn(connectionsFile, c); }
    public boolean deleteConnection(String id) throws IOException { return deleteConnectionIn(connectionsFile, id); }
    /** Process Center definitions saved in a workspace (personal or team): the same shape as the shared ones, owned by the workspace. */
    public static List<Map<String, Object>> wsConnections(File wsRoot) { return readList(new File(wsRoot, "connections.json")); }
    public static Map<String, Object> saveWsConnection(File wsRoot, Map<String, Object> c) throws IOException { return saveConnectionIn(new File(wsRoot, "connections.json"), c); }
    public static boolean deleteWsConnection(File wsRoot, String id) throws IOException { return deleteConnectionIn(new File(wsRoot, "connections.json"), id); }
    static synchronized Map<String, Object> saveConnectionIn(File file, Map<String, Object> c) throws IOException {
        String id = safeId(str(c, "id").isEmpty() ? str(c, "name") : str(c, "id")); if (id.isEmpty()) throw new IllegalArgumentException("connection name is required"); String url = str(c, "url").trim(); while (url.endsWith("/")) url = url.substring(0, url.length() - 1); if (!url.startsWith("http")) throw new IllegalArgumentException("Process Center URL must start with http(s)://");
        List<Map<String, Object>> all = readList(file); Map<String, Object> old = null; for (Map<String, Object> o : all) if (str(o, "id").equals(id)) old = o;
        String pw = str(c, "password"); if (pw.isEmpty() && old != null) pw = str(old, "password");   // an empty password keeps the stored one
        String type = str(c, "type").equalsIgnoreCase("studio") ? "studio" : "pc", auth = str(c, "auth").toLowerCase(); if (type.equals("studio") && !auth.equals("bearer") && !auth.equals("basic") && !auth.equals("zen-apikey")) auth = "zen"; if (type.equals("pc")) auth = "basic";
        String apiKey = str(c, "apiKey"); if (apiKey.isEmpty() && old != null) apiKey = str(old, "apiKey"); String token = str(c, "token"); if (token.isEmpty() && old != null) token = str(old, "token");
        Map<String, Object> clean = Json.obj("id", id, "name", str(c, "name").isEmpty() ? id : str(c, "name"), "type", type, "url", url, "contextRoot", type.equals("studio") ? (c.get("contextRoot") == null ? "/bas" : str(c, "contextRoot")) : "", "auth", auth, "user", str(c, "user"), "password", pw, "apiKey", apiKey, "token", token, "designerUrl", str(c, "designerUrl"), "savedBy", str(c, "savedBy"), "savedAt", now());
        boolean found = false; for (int i = 0; i < all.size(); i++) if (str(all.get(i), "id").equals(id)) { all.set(i, clean); found = true; } if (!found) all.add(clean);
        write(file, all); return clean;
    }
    static synchronized boolean deleteConnectionIn(File file, String id) throws IOException { List<Map<String, Object>> all = readList(file); boolean removed = false; for (Iterator<Map<String, Object>> it = all.iterator(); it.hasNext();) if (str(it.next(), "id").equals(id)) { it.remove(); removed = true; } if (removed) { if (all.isEmpty()) file.delete(); else write(file, all); } return removed; }
    /** Connection without its password (for the UI). */
    public static Map<String, Object> publicConnection(Map<String, Object> c) { Map<String, Object> m = new LinkedHashMap<>(c); m.put("password", ""); m.put("hasPassword", !str(c, "password").isEmpty()); m.put("apiKey", ""); m.put("hasApiKey", !str(c, "apiKey").isEmpty()); m.put("token", ""); m.put("hasToken", !str(c, "token").isEmpty()); if (str(c, "type").isEmpty()) m.put("type", "pc"); return m; }

    // ---- per workspace ----------------------------------------------------------------------------------------------
    public static Map<String, Object> workspaceConfig(File wsRoot) { return readObject(new File(wsRoot, "workspace.json")); }
    public static void saveWorkspaceConfig(File wsRoot, Map<String, Object> cfg) throws IOException { write(new File(wsRoot, "workspace.json"), cfg); }
    public static Map<String, Object> suppressions(File wsRoot) { return readObject(new File(wsRoot, "suppressions.json")); }
    public static void saveSuppressions(File wsRoot, Map<String, Object> s) throws IOException { if (s.isEmpty()) new File(wsRoot, "suppressions.json").delete(); else write(new File(wsRoot, "suppressions.json"), s); }

    // ---- audit --------------------------------------------------------------------------------------------------------
    public synchronized void audit(Map<String, Object> entry) { try { root.mkdirs(); try (Writer w = new OutputStreamWriter(new FileOutputStream(auditFile, true), StandardCharsets.UTF_8)) { w.write(Json.write(entry)); w.write('\n'); } } catch (IOException e) { System.err.println("audit: " + e); } }
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> auditTail(int limit) { List<Map<String, Object>> l = new ArrayList<>(); if (!auditFile.isFile()) return l; try { List<String> lines = Files.readAllLines(auditFile.toPath(), StandardCharsets.UTF_8); for (int i = lines.size() - 1; i >= 0 && l.size() < limit; i--) { String s = lines.get(i).trim(); if (s.isEmpty()) continue; try { Object o = Json.parse(s); if (o instanceof Map) l.add((Map<String, Object>) o); } catch (Exception e) {} } } catch (IOException e) {} return l; }
    public static String now() { return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()); }
}

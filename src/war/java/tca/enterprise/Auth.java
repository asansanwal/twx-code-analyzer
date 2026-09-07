package tca.enterprise;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import tca.util.Json;

/**
 * Built-in accounts for deployments without container security: self-service sign-up, login, sessions.
 * Users live in {@code <dataDir>/enterprise/users.json} (passwords as salted PBKDF2-HMAC-SHA256 hashes), sessions in
 * {@code sessions.json} (random tokens, survive a restart). Administrators are designated in a properties file
 * ({@code admins.properties}: {@code admins=alice@example.com, bob@example.com} and / or one {@code user=admin} line per account).
 */
public class Auth {
    public static final String COOKIE = "tca_session";
    static final int ITERATIONS = 120000; static final long SESSION_DAYS = 30;
    final File usersFile, sessionsFile; final File adminsFile;
    final Map<String, Map<String, Object>> sessions = new HashMap<>();
    final Map<String, int[]> failures = new HashMap<>();   // user -> {count, minute}
    static final SecureRandom RANDOM = new SecureRandom();
    long adminsMtime = -1; Set<String> admins = new HashSet<>();

    public Auth(File dataDir, File adminsFile) { File root = new File(dataDir, "enterprise"); usersFile = new File(root, "users.json"); sessionsFile = new File(root, "sessions.json"); this.adminsFile = adminsFile; loadSessions(); }

    // ---- users --------------------------------------------------------------------------------------------------------
    public static String userId(String email) { return email == null ? "" : email.trim().toLowerCase(); }
    public synchronized List<Map<String, Object>> users() { return Directory.readList(usersFile); }
    public synchronized Map<String, Object> user(String id) { for (Map<String, Object> u : users()) if (Directory.str(u, "id").equals(id)) return u; return null; }
    public static Map<String, Object> publicUser(Map<String, Object> u) { Map<String, Object> m = new LinkedHashMap<>(u); m.remove("passwordHash"); return m; }
    public synchronized Map<String, Object> signup(String email, String name, String password) throws IOException {
        String id = userId(email); if (!id.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("a valid e-mail address is required");
        checkPassword(password); if (user(id) != null) throw new IllegalArgumentException("an account with this e-mail address already exists");
        Map<String, Object> u = Json.obj("id", id, "email", id, "name", name == null || name.trim().isEmpty() ? id.substring(0, id.indexOf('@')) : name.trim(), "passwordHash", hash(password), "createdAt", Directory.now(), "lastLogin", "", "disabled", false);
        List<Map<String, Object>> all = users(); all.add(u); Directory.write(usersFile, all); return u;
    }
    public synchronized Map<String, Object> login(String email, String password) throws IOException {
        String id = userId(email); int[] f = failures.get(id); int minute = (int) (System.currentTimeMillis() / 60000);
        if (f != null && f[0] >= 10 && minute - f[1] < 15) throw new IllegalArgumentException("too many failed attempts, try again in 15 minutes");
        Map<String, Object> u = user(id);
        if (u == null || Boolean.TRUE.equals(u.get("disabled")) || !verify(password, Directory.str(u, "passwordHash"))) { if (f == null || minute - f[1] >= 15) f = new int[] { 0, minute }; f[0]++; f[1] = minute; failures.put(id, f); throw new IllegalArgumentException("unknown account or wrong password"); }
        failures.remove(id); u.put("lastLogin", Directory.now()); saveUser(u); return u;
    }
    public synchronized void setPassword(String id, String password) throws IOException { Map<String, Object> u = user(id); if (u == null) throw new IllegalArgumentException("unknown account"); checkPassword(password); u.put("passwordHash", hash(password)); saveUser(u); }
    public synchronized void setDisabled(String id, boolean disabled) throws IOException { Map<String, Object> u = user(id); if (u == null) throw new IllegalArgumentException("unknown account"); u.put("disabled", disabled); saveUser(u); if (disabled) endSessionsOf(id); }
    public synchronized boolean deleteUser(String id) throws IOException { List<Map<String, Object>> all = users(); boolean removed = false; for (Iterator<Map<String, Object>> it = all.iterator(); it.hasNext();) if (Directory.str(it.next(), "id").equals(id)) { it.remove(); removed = true; } if (removed) { Directory.write(usersFile, all); endSessionsOf(id); } return removed; }
    void saveUser(Map<String, Object> u) throws IOException { List<Map<String, Object>> all = users(); for (int i = 0; i < all.size(); i++) if (Directory.str(all.get(i), "id").equals(Directory.str(u, "id"))) all.set(i, u); Directory.write(usersFile, all); }
    static void checkPassword(String p) { if (p == null || p.length() < 8) throw new IllegalArgumentException("the password needs at least 8 characters"); }

    // ---- passwords ----------------------------------------------------------------------------------------------------
    static String hash(String password) { byte[] salt = new byte[16]; RANDOM.nextBytes(salt); return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(pbkdf2(password, salt, ITERATIONS)); }
    static boolean verify(String password, String stored) { if (password == null || stored == null || !stored.startsWith("pbkdf2$")) return false; String[] p = stored.split("\\$"); if (p.length != 4) return false; byte[] salt = Base64.getDecoder().decode(p[2]), expected = Base64.getDecoder().decode(p[3]); byte[] actual = pbkdf2(password, salt, Integer.parseInt(p[1])); if (actual.length != expected.length) return false; int diff = 0; for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i]; return diff == 0; }
    static byte[] pbkdf2(String password, byte[] salt, int iterations) { try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded(); } catch (Exception e) { throw new IllegalStateException(e); } }

    // ---- sessions -------------------------------------------------------------------------------------------------------
    public synchronized String startSession(String userId) { byte[] b = new byte[24]; RANDOM.nextBytes(b); String t = Base64.getUrlEncoder().withoutPadding().encodeToString(b); sessions.put(t, Json.obj("user", userId, "created", System.currentTimeMillis(), "seen", System.currentTimeMillis())); saveSessions(); return t; }
    /** User id of a session token, null when unknown or expired. */
    public synchronized String sessionUser(String token) { if (token == null) return null; Map<String, Object> s = sessions.get(token); if (s == null) return null; long created = ((Number) s.get("created")).longValue(); if (System.currentTimeMillis() - created > SESSION_DAYS * 86400000L) { sessions.remove(token); saveSessions(); return null; } long seen = ((Number) s.get("seen")).longValue(); if (System.currentTimeMillis() - seen > 3600000L) { s.put("seen", System.currentTimeMillis()); saveSessions(); } return Directory.str(s, "user"); }
    public synchronized void endSession(String token) { if (token != null && sessions.remove(token) != null) saveSessions(); }
    synchronized void endSessionsOf(String userId) { boolean changed = false; for (Iterator<Map<String, Object>> it = sessions.values().iterator(); it.hasNext();) if (Directory.str(it.next(), "user").equals(userId)) { it.remove(); changed = true; } if (changed) saveSessions(); }
    void loadSessions() { for (Map.Entry<String, Object> e : Directory.readObject(sessionsFile).entrySet()) if (e.getValue() instanceof Map) { @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) e.getValue(); sessions.put(e.getKey(), m); } }
    void saveSessions() { try { Directory.write(sessionsFile, sessions); } catch (IOException e) { System.err.println("sessions: " + e); } }

    // ---- personal access tokens (CI / CD) -----------------------------------------------------------------------------
    final File tokensFile() { return new File(usersFile.getParentFile(), "tokens.json"); }
    public synchronized List<Map<String, Object>> tokens(String userId) { List<Map<String, Object>> l = new ArrayList<>(); for (Map<String, Object> t : Directory.readList(tokensFile())) if (userId == null || Directory.str(t, "user").equals(userId)) { Map<String, Object> m = new LinkedHashMap<>(t); m.remove("hash"); l.add(m); } return l; }
    /** Creates a token for the user; the clear text is returned once and only its SHA-256 hash is stored. */
    public synchronized Map<String, Object> createToken(String userId, String name, int days) throws IOException {
        byte[] b = new byte[24]; RANDOM.nextBytes(b); String clear = "tca_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b); String id = Long.toHexString(System.currentTimeMillis()) + Integer.toHexString(RANDOM.nextInt(0xffff));
        Map<String, Object> t = Json.obj("id", id, "user", userId, "name", name == null || name.trim().isEmpty() ? "token" : name.trim(), "hash", sha256(clear), "createdAt", Directory.now(), "expiresAt", days > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date(System.currentTimeMillis() + days * 86400000L)) : "", "lastUsed", "");
        List<Map<String, Object>> all = Directory.readList(tokensFile()); all.add(t); Directory.write(tokensFile(), all);
        Map<String, Object> out = new LinkedHashMap<>(t); out.remove("hash"); out.put("token", clear); return out;
    }
    public synchronized boolean deleteToken(String userId, String id) throws IOException { List<Map<String, Object>> all = Directory.readList(tokensFile()); boolean removed = false; for (Iterator<Map<String, Object>> it = all.iterator(); it.hasNext();) { Map<String, Object> t = it.next(); if (Directory.str(t, "id").equals(id) && (userId == null || Directory.str(t, "user").equals(userId))) { it.remove(); removed = true; } } if (removed) Directory.write(tokensFile(), all); return removed; }
    /** User id of a bearer token, null when unknown, expired or the account is disabled. */
    public synchronized String tokenUser(String clear) {
        if (clear == null || !clear.startsWith("tca_")) return null; String h = sha256(clear); List<Map<String, Object>> all = Directory.readList(tokensFile()); boolean changed = false; String user = null;
        for (Map<String, Object> t : all) if (h.equals(Directory.str(t, "hash"))) { String exp = Directory.str(t, "expiresAt"); if (!exp.isEmpty() && exp.compareTo(Directory.now()) < 0) return null; Map<String, Object> u = user(Directory.str(t, "user")); if (u != null && Boolean.TRUE.equals(u.get("disabled"))) return null; String seen = Directory.str(t, "lastUsed"); if (seen.isEmpty() || !seen.substring(0, 13).equals(Directory.now().substring(0, 13))) { t.put("lastUsed", Directory.now()); changed = true; } user = Directory.str(t, "user"); }
        if (changed) try { Directory.write(tokensFile(), all); } catch (IOException e) {}
        return user;
    }
    static String sha256(String s) { try { byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x & 0xff)); return b.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
    /** Removes every session and token of a user (account deletion). */
    public synchronized void forgetUser(String userId) throws IOException { endSessionsOf(userId); List<Map<String, Object>> all = Directory.readList(tokensFile()); boolean changed = false; for (Iterator<Map<String, Object>> it = all.iterator(); it.hasNext();) if (Directory.str(it.next(), "user").equals(userId)) { it.remove(); changed = true; } if (changed) Directory.write(tokensFile(), all); }

    // ---- administrators (properties file) ------------------------------------------------------------------------------
    public synchronized boolean isAdmin(String userId) {
        if (userId == null) return false; long m = adminsFile.isFile() ? adminsFile.lastModified() : 0;
        if (m != adminsMtime) { adminsMtime = m; admins = new HashSet<>(); if (adminsFile.isFile()) try (Reader r = new InputStreamReader(new FileInputStream(adminsFile), StandardCharsets.UTF_8)) { Properties p = new Properties(); p.load(r); for (String k : p.stringPropertyNames()) { String v = p.getProperty(k).trim(); if (k.trim().equals("admins")) { for (String a : v.split("[,;\\s]+")) if (!a.isEmpty()) admins.add(a.toLowerCase()); } else if (v.equalsIgnoreCase("admin") || v.equalsIgnoreCase("true")) admins.add(k.trim().toLowerCase()); } } catch (IOException e) { System.err.println("admins file: " + e); } }
        return admins.contains(userId.toLowerCase());
    }
    public File adminsFile() { return adminsFile; }
}

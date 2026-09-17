package tca.search;

import java.util.*;
import java.util.regex.*;
import tca.model.*;
import tca.rules.RuleContext;
import tca.util.Json;
import tca.util.TimedText;

/** TWX search: text or regular expression over artifact names, scripts, mappings, conditions, documentation and raw XML, with type filters. */
public final class Searcher {
    private Searcher() {}
    /** Longest accepted search expression. */
    public static final int MAX_QUERY = 200;
    /** Time budget of one search over one export. */
    public static final long BUDGET_MS = 5000;
    /**
     * A user-typed regular expression, rebuilt token by token from the constructions the search supports: literal text (quoted with
     * {@link Pattern#quote}), character classes over an allow-list of characters, {@code . * + ? {n,m} | ^ $}, the escapes {@code \w \W \s \S
     * \d \D \b \B \t \n}, plain and non-capturing groups and {@code (?i)}. Everything else is rejected with an IllegalArgumentException:
     * more than {@link #MAX_QUERY} characters, a quantifier on a group ({@code (a|b)*}, {@code (\w+\s?)+} - the constructions whose matching
     * time explodes), back references, look-arounds, named groups, other escapes, repeat counts above {@link #MAX_REPEAT}, unbalanced
     * groups or classes. The result consists only of quoted literals and tokens from this vocabulary.
     */
    public static String sanitizeRegex(String query) {
        if (query.length() > MAX_QUERY) throw new IllegalArgumentException("the search expression is longer than " + MAX_QUERY + " characters");
        StringBuilder out = new StringBuilder(), literal = new StringBuilder(); int depth = 0, n = query.length();
        for (int i = 0; i < n; i++) {
            char ch = query.charAt(i);
            switch (ch) {
                case '\\': { if (++i >= n) throw new IllegalArgumentException("the search expression ends with a backslash"); char e = query.charAt(i); int k = ESCAPES.indexOf(e);
                    if (k >= 0) { flush(out, literal); out.append('\\').append(ESCAPES.charAt(k)); } else if (Character.isLetterOrDigit(e)) throw new IllegalArgumentException("the escape \\" + e + " is not supported in the search expression (back references, named classes)"); else literal.append(e); break; }
                case '[': { flush(out, literal); out.append('['); if (i + 1 < n && query.charAt(i + 1) == '^') { out.append('^'); i++; }
                    boolean closed = false;
                    while (++i < n) { char c = query.charAt(i); if (c == ']') { closed = true; break; }
                        if (c == '\\') { if (++i >= n) break; char e = query.charAt(i); int k = ESCAPES.indexOf(e), q = CLASS_ESCAPES.indexOf(e); if (k >= 0) out.append('\\').append(ESCAPES.charAt(k)); else if (q >= 0) out.append('\\').append(CLASS_ESCAPES.charAt(q)); else throw new IllegalArgumentException("the escape \\" + e + " is not supported inside a character class"); continue; }
                        int k = CLASS_CHARS.indexOf(c); if (k < 0) throw new IllegalArgumentException("the character '" + c + "' is not supported inside a character class (escape it with a backslash)"); out.append(CLASS_CHARS.charAt(k)); }
                    if (!closed) throw new IllegalArgumentException("unclosed character class in the search expression"); out.append(']'); break; }
                case '(': { flush(out, literal); depth++; if (query.startsWith("(?:", i)) { out.append("(?:"); i += 2; } else if (query.startsWith("(?i)", i)) { out.append("(?i)"); i += 3; depth--; } else if (i + 1 < n && query.charAt(i + 1) == '?') throw new IllegalArgumentException("look-arounds and named groups are not supported in the search expression"); else out.append('('); break; }
                case ')': { flush(out, literal); if (--depth < 0) throw new IllegalArgumentException("unbalanced parentheses in the search expression"); if (i + 1 < n && "*+?{".indexOf(query.charAt(i + 1)) >= 0) throw new IllegalArgumentException("a repeated group (\"...)" + query.charAt(i + 1) + "\") is not supported in the search expression; repeat characters or classes instead"); out.append(')'); break; }
                case '{': { flush(out, literal); int close = query.indexOf('}', i); if (close < 0) throw new IllegalArgumentException("unclosed {n,m} in the search expression"); String[] parts = query.substring(i + 1, close).split(",", -1); if (parts.length > 2) throw new IllegalArgumentException("invalid repeat count in the search expression");
                    int lo = repeat(parts[0]), hi = parts.length == 1 ? lo : parts[1].isEmpty() ? -1 : repeat(parts[1]); if (hi >= 0 && hi < lo) throw new IllegalArgumentException("invalid repeat count in the search expression");
                    out.append('{').append(lo); if (parts.length == 2) { out.append(','); if (hi >= 0) out.append(hi); } out.append('}'); i = close; break; }
                case '.': flush(out, literal); out.append('.'); break;
                case '*': flush(out, literal); out.append('*'); break;
                case '+': flush(out, literal); out.append('+'); break;
                case '?': flush(out, literal); out.append('?'); break;
                case '|': flush(out, literal); out.append('|'); break;
                case '^': flush(out, literal); out.append('^'); break;
                case '$': flush(out, literal); out.append('$'); break;
                case ']': case '}': literal.append(ch); break;   // literal when unpaired
                default: literal.append(ch);
            }
        }
        flush(out, literal); if (depth != 0) throw new IllegalArgumentException("unbalanced parentheses in the search expression");
        String safe = out.toString();
        try { Pattern.compile(safe); } catch (PatternSyntaxException e) { throw new IllegalArgumentException("invalid regular expression: " + e.getDescription()); }
        return safe;
    }
    /** Largest repeat count in {@code {n,m}}. */
    public static final int MAX_REPEAT = 100;
    static final String ESCAPES = "wWsSdDbBtn", CLASS_ESCAPES = "[]\\-^", CLASS_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 _-^.,:;!?@#$%&*+=/<>'\"()|{}~`";
    static void flush(StringBuilder out, StringBuilder literal) { if (literal.length() > 0) { out.append(Pattern.quote(literal.toString())); literal.setLength(0); } }
    static int repeat(String s) { if (!s.matches("[0-9]{1,3}")) throw new IllegalArgumentException("invalid repeat count in the search expression"); int v = Integer.parseInt(s); if (v > MAX_REPEAT) throw new IllegalArgumentException("repeat counts above " + MAX_REPEAT + " are not supported in the search expression"); return v; }
    /** Search results; throws IllegalArgumentException for a rejected expression and {@link TimedText.Timeout} when the budget is used up. */
    public static List<Map<String, Object>> search(RuleContext c, String query, boolean regex, boolean caseSensitive, String scope, Set<String> types, boolean includeToolkits, int limit) {
        List<Map<String, Object>> out = new ArrayList<>(); if (query == null || query.trim().isEmpty()) return out;
        Pattern p = Pattern.compile(regex ? sanitizeRegex(query) : Pattern.quote(query), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        final TimedText clock = new TimedText("", BUDGET_MS, "the search");   // one budget for the whole search: every text is wrapped with the same deadline
        List<TwxPackage> pks = includeToolkits ? c.twx.allPackages() : Collections.singletonList(c.twx.app);
        for (TwxPackage pk : pks) for (TwxObject o : pk.objects.values()) {
            if (types != null && !types.isEmpty() && !types.contains(o.type)) continue;
            if (out.size() >= limit) return out;
            if (scope.equals("names") || scope.equals("all")) { if (p.matcher(clock.over(o.name)).find()) out.add(hit(o, "name", "", "", o.name)); }
            if (scope.equals("scripts") || scope.equals("all")) { if (o.type.equals("process") || o.type.equals("bpd") || o.type.equals("coachView")) for (Script s : c.scripts(o)) { Matcher m = p.matcher(clock.over(s.code)); if (m.find()) out.add(hit(o, "script", s.itemId, s.location, snippet(s.code, m.start(), m.end()))); } }
            if (scope.equals("xml") || scope.equals("all")) { if (!scope.equals("all") || (!o.type.equals("process") && !o.type.equals("bpd") && !o.type.equals("coachView"))) { Matcher m = p.matcher(clock.over(o.xml)); if (m.find()) out.add(hit(o, "xml", "", "raw definition", snippet(o.xml, m.start(), m.end()))); } }
            if (scope.equals("all") && (o.type.equals("process") || o.type.equals("bpd"))) { String doc = tca.util.Xml.text(o.xml, "documentation") + " " + tca.util.Xml.text(o.xml, "description"); Matcher m = p.matcher(clock.over(doc)); if (m.find()) out.add(hit(o, "documentation", "", "documentation", snippet(doc, m.start(), m.end()))); }
        }
        return out;
    }
    static Map<String, Object> hit(TwxObject o, String where, String itemId, String location, String snippet) { return Json.obj("objectId", o.id, "objectName", o.name, "objectType", o.type, "objectTypeLabel", o.typeLabel(), "package", o.pkg.label(), "acronym", o.pkg.acronym, "toolkit", o.pkg.toolkit, "where", where, "itemId", itemId, "location", location, "snippet", snippet, "path", o.path() + (location.isEmpty() ? "" : " / " + location)); }
    static String snippet(String s, int a, int b) { int from = Math.max(0, a - 80), to = Math.min(s.length(), b + 120); return (from > 0 ? "..." : "") + s.substring(from, to).replaceAll("\\s+", " ") + (to < s.length() ? "..." : ""); }
}

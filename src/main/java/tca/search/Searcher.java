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
     * A user-typed regular expression, rebuilt from the constructions the search supports before it is compiled: literals, character
     * classes, {@code . * + ? {n,m} | ^ $}, the escapes {@code \\w \\W \\s \\S \\d \\D \\b \\B \\t \\n} and escaped punctuation, plain and
     * non-capturing groups, {@code (?i)}. Rejected (IllegalArgumentException): more than {@link #MAX_QUERY} characters, a quantifier on a
     * group ({@code (a|b)*}, {@code (\\w+\\s?)+} - the constructions whose matching time explodes), back references, look-arounds,
     * named groups and other escapes, unbalanced groups or classes, and anything the Java regex compiler refuses.
     */
    public static String sanitizeRegex(String query) {
        if (query.length() > MAX_QUERY) throw new IllegalArgumentException("the search expression is longer than " + MAX_QUERY + " characters");
        StringBuilder out = new StringBuilder(); boolean inClass = false; int depth = 0;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= query.length()) throw new IllegalArgumentException("the search expression ends with a backslash");
                char e = query.charAt(++i);
                if (Character.isLetterOrDigit(e) && "wWsSdDbBtn".indexOf(e) < 0) throw new IllegalArgumentException("the escape \\" + e + " is not supported in the search expression (back references, named classes)");
                out.append('\\').append(e); continue;
            }
            if (inClass) { if (ch == ']') inClass = false; out.append(ch); continue; }
            if (ch == '[') { inClass = true; out.append(ch); continue; }
            if (ch == '(') { depth++; if (i + 1 < query.length() && query.charAt(i + 1) == '?' && !query.startsWith("(?:", i) && !query.startsWith("(?i)", i)) throw new IllegalArgumentException("look-arounds and named groups are not supported in the search expression"); }
            if (ch == ')') { if (--depth < 0) throw new IllegalArgumentException("unbalanced parentheses in the search expression"); if (i + 1 < query.length() && "*+?{".indexOf(query.charAt(i + 1)) >= 0) throw new IllegalArgumentException("a repeated group (\"...)" + query.charAt(i + 1) + "\") is not supported in the search expression; repeat characters or classes instead"); }
            out.append(ch);
        }
        if (inClass || depth != 0) throw new IllegalArgumentException("unbalanced brackets or parentheses in the search expression");
        String safe = out.toString();
        try { Pattern.compile(safe); } catch (PatternSyntaxException e) { throw new IllegalArgumentException("invalid regular expression: " + e.getDescription()); }
        return safe;
    }
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

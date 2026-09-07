package tca.search;

import java.util.*;
import java.util.regex.*;
import tca.model.*;
import tca.rules.RuleContext;
import tca.util.Json;

/** TWX search: text or regular expression over artifact names, scripts, mappings, conditions, documentation and raw XML, with type filters. */
public final class Searcher {
    private Searcher() {}
    public static List<Map<String, Object>> search(RuleContext c, String query, boolean regex, boolean caseSensitive, String scope, Set<String> types, boolean includeToolkits, int limit) {
        List<Map<String, Object>> out = new ArrayList<>(); if (query == null || query.trim().isEmpty()) return out;
        Pattern p = Pattern.compile(regex ? query : Pattern.quote(query), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        List<TwxPackage> pks = includeToolkits ? c.twx.allPackages() : Collections.singletonList(c.twx.app);
        for (TwxPackage pk : pks) for (TwxObject o : pk.objects.values()) {
            if (types != null && !types.isEmpty() && !types.contains(o.type)) continue;
            if (out.size() >= limit) return out;
            if (scope.equals("names") || scope.equals("all")) { if (p.matcher(o.name).find()) out.add(hit(o, "name", "", "", o.name)); }
            if (scope.equals("scripts") || scope.equals("all")) { if (o.type.equals("process") || o.type.equals("bpd") || o.type.equals("coachView")) for (Script s : c.scripts(o)) { Matcher m = p.matcher(s.code); if (m.find()) out.add(hit(o, "script", s.itemId, s.location, snippet(s.code, m.start(), m.end()))); } }
            if (scope.equals("xml") || scope.equals("all")) { if (!scope.equals("all") || (!o.type.equals("process") && !o.type.equals("bpd") && !o.type.equals("coachView"))) { Matcher m = p.matcher(o.xml); if (m.find()) out.add(hit(o, "xml", "", "raw definition", snippet(o.xml, m.start(), m.end()))); } }
            if (scope.equals("all") && (o.type.equals("process") || o.type.equals("bpd"))) { String doc = tca.util.Xml.text(o.xml, "documentation") + " " + tca.util.Xml.text(o.xml, "description"); Matcher m = p.matcher(doc); if (m.find()) out.add(hit(o, "documentation", "", "documentation", snippet(doc, m.start(), m.end()))); }
        }
        return out;
    }
    static Map<String, Object> hit(TwxObject o, String where, String itemId, String location, String snippet) { return Json.obj("objectId", o.id, "objectName", o.name, "objectType", o.type, "objectTypeLabel", o.typeLabel(), "package", o.pkg.label(), "acronym", o.pkg.acronym, "toolkit", o.pkg.toolkit, "where", where, "itemId", itemId, "location", location, "snippet", snippet, "path", o.path() + (location.isEmpty() ? "" : " / " + location)); }
    static String snippet(String s, int a, int b) { int from = Math.max(0, a - 80), to = Math.min(s.length(), b + 120); return (from > 0 ? "..." : "") + s.substring(from, to).replaceAll("\\s+", " ") + (to < s.length() ? "..." : ""); }
}

package tca.util;

import java.util.*;
import java.util.regex.*;

/** Small helpers for the TWX object XML (regex based on the well-known Process Designer export layout - much faster than DOM on 1000+ objects). */
public final class Xml {
    private Xml() {}
    private static final Map<String, Pattern> CACHE = new HashMap<>();
    private static Pattern tag(String name) { Pattern p = CACHE.get(name); if (p == null) { p = Pattern.compile("<" + Pattern.quote(name) + "(?: [^>]*)?/>|<" + Pattern.quote(name) + "(?: [^>]*[^/>])?>(.*?)</" + Pattern.quote(name) + ">", Pattern.DOTALL); CACHE.put(name, p); } return p; }

    /** Text of the first child element with this tag (unescaped), "" when absent or isNull. */
    public static String text(String xml, String name) { Matcher m = tag(name).matcher(xml); if (!m.find() || m.group(1) == null) return ""; return unescape(m.group(1)); }
    public static String textRaw(String xml, String name) { Matcher m = tag(name).matcher(xml); if (!m.find() || m.group(1) == null) return ""; return m.group(1); }
    /** All text values of this tag. */
    public static List<String> texts(String xml, String name) { List<String> out = new ArrayList<>(); Matcher m = tag(name).matcher(xml); while (m.find()) if (m.group(1) != null) out.add(unescape(m.group(1))); return out; }
    /** All element blocks (outer XML) with this tag - blocks must not nest the same tag. */
    public static List<String> blocks(String xml, String name) { List<String> out = new ArrayList<>(); Matcher m = tag(name).matcher(xml); while (m.find()) out.add(m.group(0)); return out; }
    /** Attribute value of the first element with this tag. */
    public static String attr(String xml, String name, String attr) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + "\\b[^>]*?\\b" + Pattern.quote(attr) + "=\"([^\"]*)\"").matcher(xml); return m.find() ? unescape(m.group(1)) : "";
    }
    public static String attrOf(String elementOpenTag, String attr) { Matcher m = Pattern.compile("\\b" + Pattern.quote(attr) + "=\"([^\"]*)\"").matcher(elementOpenTag); return m.find() ? unescape(m.group(1)) : ""; }
    public static boolean bool(String xml, String name) { return "true".equals(text(xml, name)); }
    public static int integer(String xml, String name, int def) { try { String t = text(xml, name); return t.isEmpty() ? def : Integer.parseInt(t.trim()); } catch (NumberFormatException e) { return def; } }

    /** Blocks of a tag that may nest itself (e.g. lane > flowObject): scans balanced open/close tags. */
    public static List<String> nestedBlocks(String xml, String name) {
        List<String> out = new ArrayList<>(); String open = "<" + name, close = "</" + name + ">"; int i = 0;
        while ((i = xml.indexOf(open, i)) >= 0) {
            char c = i + open.length() < xml.length() ? xml.charAt(i + open.length()) : ' '; if (c != ' ' && c != '>') { i += open.length(); continue; }
            int depth = 0, j = i;
            while (j < xml.length()) {
                int o = xml.indexOf(open, j), cl = xml.indexOf(close, j);
                if (cl < 0) { j = xml.length(); break; }
                if (o >= 0 && o < cl) { char cc = o + open.length() < xml.length() ? xml.charAt(o + open.length()) : ' '; if (cc == ' ' || cc == '>') { if (xml.indexOf("/>", o) > 0 && xml.indexOf("/>", o) < xml.indexOf(">", o)) { j = o + 1; continue; } depth++; } j = o + open.length(); }
                else { depth--; j = cl + close.length(); if (depth == 0) break; }
            }
            out.add(xml.substring(i, Math.min(j, xml.length()))); i = i + open.length();
        }
        return out;
    }

    public static String unescape(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 8) {
                    String ent = s.substring(i + 1, semi); String rep = null;
                    switch (ent) { case "amp": rep = "&"; break; case "lt": rep = "<"; break; case "gt": rep = ">"; break; case "quot": rep = "\""; break; case "apos": rep = "'"; break;
                        default: if (ent.startsWith("#x") || ent.startsWith("#X")) { try { rep = new String(Character.toChars(Integer.parseInt(ent.substring(2), 16))); } catch (Exception e) {} } else if (ent.startsWith("#")) { try { rep = new String(Character.toChars(Integer.parseInt(ent.substring(1)))); } catch (Exception e) {} } }
                    if (rep != null) { sb.append(rep); i = semi; continue; }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
    public static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}

package tca.util;

import java.util.*;

/** Minimal JSON writer/parser (no dependencies, Java 8). Values: Map (LinkedHashMap), List, String, Number, Boolean, null. */
public final class Json {
    private Json() {}

    public static String write(Object v) { StringBuilder sb = new StringBuilder(); write(v, sb, 0, false); return sb.toString(); }
    public static String writePretty(Object v) { StringBuilder sb = new StringBuilder(); write(v, sb, 0, true); return sb.toString(); }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder sb, int ind, boolean pretty) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { quote((String) v, sb); return; }
        if (v instanceof Number || v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v; sb.append('{'); boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) sb.append(','); first = false; nl(sb, ind + 1, pretty); quote(e.getKey(), sb); sb.append(pretty ? ": " : ":"); write(e.getValue(), sb, ind + 1, pretty);
            }
            if (!first) nl(sb, ind, pretty); sb.append('}'); return;
        }
        if (v instanceof Collection) {
            sb.append('['); boolean first = true;
            for (Object o : (Collection<?>) v) { if (!first) sb.append(','); first = false; nl(sb, ind + 1, pretty); write(o, sb, ind + 1, pretty); }
            if (!first) nl(sb, ind, pretty); sb.append(']'); return;
        }
        if (v instanceof Object[]) { write(Arrays.asList((Object[]) v), sb, ind, pretty); return; }
        quote(String.valueOf(v), sb);
    }
    private static void nl(StringBuilder sb, int ind, boolean pretty) { if (!pretty) return; sb.append('\n'); for (int i = 0; i < ind; i++) sb.append("  "); }
    public static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break; case '\\': sb.append("\\\\"); break; case '\n': sb.append("\\n"); break; case '\r': sb.append("\\r"); break; case '\t': sb.append("\\t"); break;
                default: if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---- parser ----
    public static Object parse(String s) { Parser p = new Parser(s); Object v = p.value(); p.ws(); if (p.i != s.length()) throw new IllegalArgumentException("trailing data at " + p.i); return v; }
    private static final class Parser {
        final String s; int i = 0;
        Parser(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            ws(); if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i);
            if (c == '{') { i++; Map<String, Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = str(); ws(); expect(':'); m.put(k, value()); ws(); char d = s.charAt(i++); if (d == '}') return m; if (d != ',') throw new IllegalArgumentException("expected , at " + i); } }
            if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws(); if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(value()); ws(); char d = s.charAt(i++); if (d == ']') return l; if (d != ',') throw new IllegalArgumentException("expected , at " + i); } }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; } if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; } if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String n = s.substring(st, i); if (n.isEmpty()) throw new IllegalArgumentException("bad json at " + i);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n); long lv = Long.parseLong(n); return lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE ? (Object) (int) lv : (Object) lv;
        }
        void expect(char c) { if (s.charAt(i) != c) throw new IllegalArgumentException("expected " + c + " at " + i); i++; }
        String str() {
            expect('"'); StringBuilder sb = new StringBuilder();
            while (true) { char c = s.charAt(i++); if (c == '"') return sb.toString(); if (c == '\\') { char e = s.charAt(i++);
                switch (e) { case 'n': sb.append('\n'); break; case 't': sb.append('\t'); break; case 'r': sb.append('\r'); break; case 'b': sb.append('\b'); break; case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: sb.append(e); } } else sb.append(c); }
        }
    }

    /** Convenience: ordered map builder. */
    public static Map<String, Object> obj(Object... kv) { Map<String, Object> m = new LinkedHashMap<>(); for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]); return m; }
}

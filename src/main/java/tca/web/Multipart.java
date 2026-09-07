package tca.web;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal multipart/form-data parser (single file upload). */
public final class Multipart {
    private Multipart() {}
    public static class Part { public String name = "", fileName = ""; public byte[] data; }
    public static Part firstFile(byte[] body, String contentType) {
        String boundary = null; for (String p : contentType.split(";")) { p = p.trim(); if (p.startsWith("boundary=")) boundary = p.substring(9).replace("\"", ""); }
        if (boundary == null) return null; byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int pos = indexOf(body, delim, 0); while (pos >= 0) {
            int start = pos + delim.length; if (start + 2 <= body.length && body[start] == '-' && body[start + 1] == '-') break;
            int hdrEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), start); if (hdrEnd < 0) break;
            String headers = new String(body, start, hdrEnd - start, StandardCharsets.ISO_8859_1); int dataStart = hdrEnd + 4; int next = indexOf(body, delim, dataStart); if (next < 0) break; int dataEnd = next - 2;
            if (headers.contains("filename=")) { Part p = new Part(); p.fileName = attr(headers, "filename"); p.name = attr(headers, "name"); p.data = Arrays.copyOfRange(body, dataStart, Math.max(dataStart, dataEnd)); return p; }
            pos = next;
        }
        return null;
    }
    static String attr(String h, String n) { int i = h.indexOf(n + "=\""); if (i < 0) return ""; int j = h.indexOf('"', i + n.length() + 2); return j < 0 ? "" : h.substring(i + n.length() + 2, j); }
    static int indexOf(byte[] a, byte[] b, int from) { outer: for (int i = Math.max(0, from); i <= a.length - b.length; i++) { for (int j = 0; j < b.length; j++) if (a[i + j] != b[j]) continue outer; return i; } return -1; }
}

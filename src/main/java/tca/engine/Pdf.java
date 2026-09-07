package tca.engine;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal PDF writer (no dependencies): A4 pages, Helvetica / Helvetica-Bold (WinAnsi), word-wrapped paragraphs and tables with
 *  ruled cells, automatic page breaks with repeated table headers, page numbers. Enough for printable reports; not a layout engine. */
public final class Pdf {
    final double pageW, pageH, margin = 40; final List<String> pages = new ArrayList<>(); StringBuilder page; double y; int pageNo;
    String footer = "";
    public Pdf(boolean landscape) { pageW = landscape ? 841.89 : 595.28; pageH = landscape ? 595.28 : 841.89; newPage(); }

    // ---- primitives ----
    void newPage() { if (page != null) closePage(); page = new StringBuilder(); pageNo++; y = pageH - margin; }
    void closePage() { if (!footer.isEmpty()) text(margin, 20, 8, false, footer + "   page " + pageNo, 0.45); pages.add(page.toString()); }
    static String enc(String s) { StringBuilder b = new StringBuilder(); for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '(' || c == ')' || c == '\\') b.append('\\').append(c); else if (c == '\n' || c == '\r' || c == '\t') b.append(' '); else if (c < 32) continue; else if (c < 127) b.append(c); else if (c >= 160 && c <= 255) b.append(c); else if (c == 0x2014 || c == 0x2013) b.append('-'); else if (c == 0x2018 || c == 0x2019) b.append('\''); else if (c == 0x201C || c == 0x201D) b.append('"'); else if (c == 0x2026) b.append("..."); else if (c == 0x2192) b.append("->"); else if (c == 0x2190) b.append("<-"); else b.append('?'); } return b.toString(); }
    void text(double x, double yy, double size, boolean bold, String s, double gray) { page.append(gray > 0 ? String.format(Locale.ROOT, "%.2f g ", gray) : "0 g ").append("BT /").append(bold ? "F2" : "F1").append(' ').append(fmt(size)).append(" Tf ").append(fmt(x)).append(' ').append(fmt(yy)).append(" Td (").append(enc(s)).append(") Tj ET\n"); }
    void line(double x1, double y1, double x2, double y2, double gray) { page.append(String.format(Locale.ROOT, "%.2f G 0.5 w ", gray)).append(fmt(x1)).append(' ').append(fmt(y1)).append(" m ").append(fmt(x2)).append(' ').append(fmt(y2)).append(" l S\n"); }
    void rect(double x, double yy, double w, double h, double gray) { page.append(String.format(Locale.ROOT, "%.2f g ", gray)).append(fmt(x)).append(' ').append(fmt(yy)).append(' ').append(fmt(w)).append(' ').append(fmt(h)).append(" re f\n"); }
    static String fmt(double d) { return String.format(Locale.ROOT, "%.2f", d); }
    /** Approximate Helvetica width (average glyph 0.5 em; upper case and digits a little wider, thin glyphs narrower). */
    static double width(String s, double size) { double w = 0; for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); w += (c == 'i' || c == 'l' || c == 'j' || c == 't' || c == 'f' || c == '.' || c == ',' || c == ':' || c == ';' || c == '\'' || c == '|' || c == 'I') ? 0.28 : (c == 'm' || c == 'w' || c == 'M' || c == 'W') ? 0.83 : Character.isUpperCase(c) ? 0.68 : (c == ' ') ? 0.28 : 0.53; } return w * size; }
    static List<String> wrap(String s, double size, double maxW) {
        List<String> out = new ArrayList<>(); if (s == null) s = "";
        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                while (width(word, size) > maxW && word.length() > 1) { int cut = Math.max(1, (int) (word.length() * maxW / width(word, size)) - 1); if (line.length() > 0) { out.add(line.toString()); line.setLength(0); } out.add(word.substring(0, cut)); word = word.substring(cut); }
                if (line.length() > 0 && width(line + " " + word, size) > maxW) { out.add(line.toString()); line.setLength(0); }
                if (line.length() > 0) line.append(' '); line.append(word);
            }
            out.add(line.toString());
        }
        return out;
    }
    void need(double h) { if (y - h < margin + 14) newPage(); }

    // ---- blocks ----
    public void heading(String s, double size) { need(size * 2.2); y -= size * 1.6; text(margin, y, size, true, s, 0); y -= size * 0.6; }
    public void paragraph(String s, double size, double gray) { for (String l : wrap(s, size, pageW - 2 * margin)) { need(size * 1.4); y -= size * 1.3; text(margin, y, size, false, l, gray); } y -= size * 0.5; }
    public void space(double h) { y -= h; }
    /** Table with fixed column widths (fractions of the usable width), header repeated on each page, cell text wrapped; a cell may carry a bold prefix (first line in bold when it starts with "**"). */
    public void table(String[] headers, double[] fractions, List<String[]> rows, double size) {
        double usable = pageW - 2 * margin; double[] w = new double[fractions.length]; for (int i = 0; i < w.length; i++) w[i] = usable * fractions[i];
        double pad = 3, lh = size * 1.25;
        header(headers, w, size, pad, lh);
        for (String[] row : rows) {
            List<List<String>> cells = new ArrayList<>(); int lines = 1;
            for (int i = 0; i < w.length; i++) { List<String> ls = wrap(i < row.length ? row[i] : "", size, w[i] - 2 * pad); cells.add(ls); lines = Math.max(lines, ls.size()); }
            double h = lines * lh + 2 * pad; if (y - h < margin + 14) { newPage(); header(headers, w, size, pad, lh); }
            double x = margin; double top = y;
            for (int i = 0; i < w.length; i++) { double ty = top - pad - size; boolean bold = false; String cell = i < row.length ? row[i] : ""; if (cell.startsWith("**")) bold = true; int n = 0; for (String l : cells.get(i)) { text(x + pad, ty, size, bold && n == 0, n == 0 && bold ? l.substring(2) : l, 0); ty -= lh; n++; } x += w[i]; }
            y -= h; line(margin, y, margin + usable, y, 0.75);
            x = margin; for (int i = 0; i <= w.length; i++) { line(x, top, x, y, 0.75); if (i < w.length) x += w[i]; }
        }
        y -= size;
    }
    void header(String[] headers, double[] w, double size, double pad, double lh) {
        double h = lh + 2 * pad; need(h + lh); rect(margin, y - h, pageW - 2 * margin, h, 0.92); double x = margin;
        for (int i = 0; i < w.length; i++) { text(x + pad, y - pad - size, size, true, headers[i], 0); x += w[i]; }
        line(margin, y, margin + (pageW - 2 * margin), y, 0.6); y -= h; line(margin, y, margin + (pageW - 2 * margin), y, 0.6);
    }

    // ---- file ----
    public byte[] build() {
        closePage(); page = null;
        List<byte[]> objs = new ArrayList<>(); // 1 catalog, 2 pages, 3 font, 4 font bold, 5.. page + content pairs
        int n = pages.size(); StringBuilder kids = new StringBuilder(); for (int i = 0; i < n; i++) kids.append(5 + 2 * i).append(" 0 R ");
        objs.add(b("<< /Type /Catalog /Pages 2 0 R >>")); objs.add(b("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>"));
        objs.add(b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")); objs.add(b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < n; i++) {
            byte[] content = pages.get(i).getBytes(StandardCharsets.ISO_8859_1);
            objs.add(b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + fmt(pageW) + " " + fmt(pageH) + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (6 + 2 * i) + " 0 R >>"));
            ByteArrayOutputStream c = new ByteArrayOutputStream(); c.write(b("<< /Length " + content.length + " >>\nstream\n"), 0, ("<< /Length " + content.length + " >>\nstream\n").length()); c.write(content, 0, content.length); byte[] e = b("\nendstream"); c.write(e, 0, e.length); objs.add(c.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] head = b("%PDF-1.4\n%âãÏÓ\n"); out.write(head, 0, head.length); long[] offsets = new long[objs.size() + 1];
        for (int i = 0; i < objs.size(); i++) { offsets[i + 1] = out.size(); byte[] h = b((i + 1) + " 0 obj\n"); out.write(h, 0, h.length); out.write(objs.get(i), 0, objs.get(i).length); byte[] t = b("\nendobj\n"); out.write(t, 0, t.length); }
        long xref = out.size(); StringBuilder x = new StringBuilder("xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n"); for (int i = 1; i <= objs.size(); i++) x.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        x.append("trailer\n<< /Size " + (objs.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"); byte[] xb = b(x.toString()); out.write(xb, 0, xb.length);
        return out.toByteArray();
    }
    static byte[] b(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
}

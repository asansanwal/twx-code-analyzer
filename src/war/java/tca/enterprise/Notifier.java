package tca.enterprise;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;
import tca.util.Json;

/** Outbound notifications: JSON webhooks (HTTP POST) and e-mail through a minimal SMTP client (plain, SSL or STARTTLS, optional AUTH LOGIN). No library. */
public class Notifier {
    public String smtpHost = "", smtpUser = "", smtpPassword = "", smtpFrom = "", smtpSecurity = "none"; public int smtpPort = 25;
    public boolean smtpConfigured() { return !smtpHost.isEmpty() && !smtpFrom.isEmpty(); }

    public void webhook(String url, Map<String, Object> event) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(10000); c.setReadTimeout(30000); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("User-Agent", "twx-code-analyzer");
        byte[] b = Json.write(event).getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length); try (OutputStream o = c.getOutputStream()) { o.write(b); }
        int code = c.getResponseCode(); c.disconnect(); if (code >= 300) throw new IOException("webhook " + url + " answered HTTP " + code);
    }

    public void mail(List<String> to, String subject, String text) throws IOException {
        if (!smtpConfigured()) throw new IOException("SMTP is not configured (smtpHost, smtpFrom)"); if (to.isEmpty()) return;
        Socket s = "ssl".equalsIgnoreCase(smtpSecurity) ? SSLSocketFactory.getDefault().createSocket() : new Socket(); s.connect(new InetSocketAddress(smtpHost, smtpPort), 10000); s.setSoTimeout(30000);
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)); Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
            expect(in, "220"); cmd(in, out, "EHLO twx-code-analyzer", "250");
            if ("starttls".equalsIgnoreCase(smtpSecurity)) { cmd(in, out, "STARTTLS", "220"); s = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(s, smtpHost, smtpPort, true); ((SSLSocket) s).startHandshake(); in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)); out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8); cmd(in, out, "EHLO twx-code-analyzer", "250"); }
            if (!smtpUser.isEmpty()) { cmd(in, out, "AUTH LOGIN", "334"); cmd(in, out, Base64.getEncoder().encodeToString(smtpUser.getBytes(StandardCharsets.UTF_8)), "334"); cmd(in, out, Base64.getEncoder().encodeToString(smtpPassword.getBytes(StandardCharsets.UTF_8)), "235"); }
            cmd(in, out, "MAIL FROM:<" + smtpFrom + ">", "250"); for (String t : to) cmd(in, out, "RCPT TO:<" + t.trim() + ">", "250"); cmd(in, out, "DATA", "354");
            String date = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(new Date());
            StringBuilder msg = new StringBuilder(); msg.append("From: ").append(smtpFrom).append("\r\nTo: ").append(join(to)).append("\r\nSubject: ").append(subject.replaceAll("[\\r\\n]", " ")).append("\r\nDate: ").append(date).append("\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n");
            for (String line : text.split("\n")) msg.append(line.startsWith(".") ? "." + line : line).append("\r\n"); msg.append(".");
            cmd(in, out, msg.toString(), "250"); cmd(in, out, "QUIT", "221");
        } finally { s.close(); }
    }
    static void cmd(BufferedReader in, Writer out, String line, String code) throws IOException { out.write(line + "\r\n"); out.flush(); expect(in, code); }
    static void expect(BufferedReader in, String code) throws IOException { String l; do { l = in.readLine(); if (l == null) throw new IOException("SMTP connection closed"); } while (l.length() >= 4 && l.charAt(3) == '-'); if (!l.startsWith(code)) throw new IOException("SMTP: expected " + code + ", got " + l); }
    static String join(List<String> l) { StringBuilder b = new StringBuilder(); for (String s : l) b.append(b.length() > 0 ? ", " : "").append(s.trim()); return b.toString(); }
}

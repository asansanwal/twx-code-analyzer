package tca.enterprise;

import java.io.IOException;
import java.util.*;
import tca.web.Http;

/** The identity-relevant part of a request, kept for work that runs after the request ended (queued analyses, notifications). */
public class Captured implements Http {
    public final String user; public final boolean admin; public final Set<String> groups; public final Map<String, String> cookies = new HashMap<>(); public final Map<String, String> headers = new HashMap<>();
    public Captured(String user, boolean admin, Set<String> groups) { this.user = user; this.admin = admin; this.groups = groups == null ? new HashSet<String>() : groups; }
    public String method() { return "JOB"; }
    public String path() { return ""; }
    public Map<String, String> query() { return new HashMap<>(); }
    public String header(String name) { return headers.get(name); }
    public String cookie(String name) { return cookies.get(name); }
    public void setHeader(String name, String value) {}
    public String remoteUser() { return user; }
    public boolean inRole(String role) { return false; }
    public byte[] body() throws IOException { return new byte[0]; }
    public void send(int code, String contentType, byte[] body, String disposition) throws IOException {}
}

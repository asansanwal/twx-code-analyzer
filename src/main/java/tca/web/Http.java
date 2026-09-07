package tca.web;

import java.io.IOException;
import java.util.Map;

/** One HTTP exchange as seen by the API, independent of the transport (JDK HttpServer in the desktop app, a servlet container for the WAR). */
public interface Http {
    String method();
    /** Path relative to the application root, e.g. "/api/rules" or "/index.html". */
    String path();
    Map<String, String> query();
    String header(String name);
    /** Value of a request cookie, null when absent. */
    String cookie(String name);
    /** Adds a response header (before send); used for Set-Cookie. */
    void setHeader(String name, String value);
    byte[] body() throws IOException;
    /** Sends the response; disposition (optional) becomes the Content-Disposition header (file downloads). */
    void send(int code, String contentType, byte[] body, String disposition) throws IOException;
}

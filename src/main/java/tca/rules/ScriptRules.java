package tca.rules;

import java.util.*;
import java.util.regex.*;
import tca.model.*;

/** JavaScript rules (server scripts, conditions, mappings, coach view handlers): security, deprecated APIs, performance, robustness, size.
 *  Pattern based (regex on the code with strings/comments stripped) plus a Rhino syntax check when the Rhino jar is on the classpath. */
public final class ScriptRules {
    private ScriptRules() {}
    static final String CAT = "Script";

    /** Regex rule over every script; the pattern is applied to the code with comments and string literals blanked out unless rawStrings is true. */
    static abstract class PatternRule extends Rule {
        final Pattern p; final boolean rawStrings;
        PatternRule(String id, String title, String cat, Severity sev, String desc, String rem, String ref, String regex, boolean rawStrings) { super(id, title, cat, sev, desc, rem, ref); p = Pattern.compile(regex, Pattern.MULTILINE); this.rawStrings = rawStrings; }
        boolean skip(Script s) { return s.kind.equals("template"); }
        public void check(RuleContext c, List<Finding> out) {
            for (Script s : c.allScripts()) { if (skip(s)) continue; String code = rawStrings ? s.code : strip(s.code); Matcher m = p.matcher(code); int n = 0; String ev = "";
                while (m.find() && n < 5) { n++; if (ev.isEmpty()) ev = line(s.code, code, m.start()); }
                if (n > 0) out.add(Finding.of(this, s.object, s.itemId, itemName(s), s.location, message(s, n, ev), ev)); }
        }
        String message(Script s, int n, String ev) { return title + " in " + s.location + (n > 1 ? " (" + n + " occurrences)" : ""); }
    }
    static String itemName(Script s) { int a = s.location.indexOf('\''), b = s.location.indexOf('\'', a + 1); return a >= 0 && b > a ? s.location.substring(a + 1, b) : ""; }
    /** Blank out comments and string literal contents (keeps positions so line numbers match). */
    static String strip(String code) {
        StringBuilder sb = new StringBuilder(code.length()); int i = 0, n = code.length();
        while (i < n) { char ch = code.charAt(i);
            if (ch == '/' && i + 1 < n && code.charAt(i + 1) == '/') { while (i < n && code.charAt(i) != '\n') { sb.append(' '); i++; } }
            else if (ch == '/' && i + 1 < n && code.charAt(i + 1) == '*') { while (i < n && !(code.charAt(i) == '*' && i + 1 < n && code.charAt(i + 1) == '/')) { sb.append(code.charAt(i) == '\n' ? '\n' : ' '); i++; } sb.append("  "); i += 2; }
            else if (ch == '"' || ch == '\'') { char q = ch; sb.append(q); i++; while (i < n && code.charAt(i) != q) { if (code.charAt(i) == '\\') { sb.append("  "); i += 2; continue; } sb.append(code.charAt(i) == '\n' ? '\n' : 'x'); i++; } if (i < n) { sb.append(q); i++; } }
            else { sb.append(ch); i++; } }
        return sb.toString();
    }
    static String line(String orig, String stripped, int pos) { int a = stripped.lastIndexOf('\n', pos) + 1, b = stripped.indexOf('\n', pos); if (b < 0) b = stripped.length(); String ln = orig.substring(a, Math.min(b, orig.length())).trim(); int no = 1; for (int i = 0; i < a; i++) if (stripped.charAt(i) == '\n') no++; return "line " + no + ": " + (ln.length() > 160 ? ln.substring(0, 160) + "..." : ln); }

    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new PatternRule("TCA-JS-001", "SQL built by string concatenation", "Security", Severity.MAJOR, "A SQL statement is assembled from variables with string concatenation. Any value that comes from a user or an external system can inject SQL (data theft, corruption). It also defeats statement caching.",
                "Use parameter markers (?) with the SQL Execute Statement parameters list (tw.local.parameters as SQLParameter[]), never concatenate values into the statement.", "IDA check-service-item-sql-injection-in-script", "(?i)(select|insert|update|delete|merge)\\b[^;\\n]*\\+\\s*tw\\.|\\+\\s*[\"'][^\"']*(?i:where|values|set)\\b", true) {
            boolean skip(Script s) { return s.object.type.equals("coachView") || s.kind.equals("template"); }
        }.impact(2));
        l.add(new Rule("TCA-JS-024", "Variable inserted into a SQL template", "Security", Severity.MAJOR, "A text-template script (Server Script in 'text' mode) builds a SQL statement and inserts variables with <#= tw.local.x #>. The inserted value is not escaped: user-controlled values allow SQL injection and even harmless values break the statement when they contain quotes.",
                "Use parameter markers (?) with the SQLParameter list of the SQL Execute Statement service; insert only trusted identifiers (table names from EPVs) through templates.", "IDA check-service-item-sql-injection-in-script") {
            public void check(RuleContext c, List<Finding> out) { Pattern sql = Pattern.compile("(?is)\\b(select|insert|update|delete|merge|call)\\b"), ins = Pattern.compile("<#=\\s*(tw\\.[A-Za-z0-9_.\\[\\]]+)[^#]*#>"); for (Script s : c.allScripts()) { if (!s.kind.equals("template") || !sql.matcher(s.code).find()) continue; Matcher m = ins.matcher(s.code); int n = 0; StringBuilder ev = new StringBuilder(); while (m.find()) { String v = m.group(1).toLowerCase(); if (v.contains("tablename") || v.contains("schema") || v.startsWith("tw.epv.") || v.startsWith("tw.env.")) continue; n++; if (ev.length() < 200) ev.append(m.group(1)).append(' '); } if (n > 0) out.add(Finding.of(this, s.object, s.itemId, itemName(s), s.location, n + " variable(s) inserted into a SQL template in " + s.location, ev.toString().trim())); } }
        }.impact(2));
        l.add(new PatternRule("TCA-JS-002", "Access to internal BPM database tables", "Security", Severity.CRITICAL, "The script references internal repository / runtime tables (LSW_*, BPM_*). Their structure is unsupported, changes between versions and direct access breaks migrations and support.",
                "Use the REST API, the JavaScript API (tw.system, TWProcessInstance ...) or the Performance Data Warehouse views instead of the internal tables.", "IDA check-service-item-contains-inner-table-in-script", "(?i)\\bLSW_[A-Z_]+|\\bBPM_[A-Z_]+_T\\b|\\bLSW\\.|\\bBPMDB\\.", true) {}.impact(2));
        l.add(new PatternRule("TCA-JS-003", "Thread.sleep in a script", "Performance", Severity.MAJOR, "java.lang.Thread.sleep blocks a server thread (and the JDBC connection/transaction) for the whole wait; under load this exhausts the thread pool.", "Use a timer event or an undercover agent for waits; for polling use a timer intermediate event with a loop.", "IDA check-service-item-contains-sleep", "Thread\\.sleep\\s*\\(|java\\.lang\\.Thread", false) {});
        l.add(new PatternRule("TCA-JS-004", "LiveConnect / Java class access from JavaScript", "Migration", Severity.MINOR, "The script uses LiveConnect (Packages.*, java.*, new java...) to call Java directly. LiveConnect is deprecated, unsupported on the newer engine and blocked in CP4BA containers.", "Replace with a Java integration service (external Java class through the Java Integration component) or a REST call.", "IDA check-service-javascript-live-connect", "\\bPackages\\.|\\bjava\\.(?:lang|util|io|net|sql)\\.|new\\s+java\\.", false) {});
        l.add(new PatternRule("TCA-JS-005", "eval / Function constructor", "Security", Severity.MAJOR, "eval() or new Function() executes arbitrary code built at runtime; with user data this is a code injection and it prevents any static verification.", "Replace eval with JSON.parse for data, explicit property access (obj[name]) or a switch on known values.", "", "\\beval\\s*\\(|new\\s+Function\\s*\\(", false) {}.impact(2));
        l.add(new PatternRule("TCA-JS-006", "Hard-coded credentials or secrets", "Security", Severity.CRITICAL, "A password, secret, token or API key is written into the script. Secrets in the TWX are visible to every developer and exported with every snapshot.", "Move the secret to an environment variable / EPV managed per environment, a server credential (Process Admin > Servers) or a secure vault, and reference it from the script.", "", "(?i)(password|passwd|pwd|secret|apikey|api_key|token|authorization)\\s*[:=]\\s*[\"'][^\"']{4,}[\"']|Basic\\s+[A-Za-z0-9+/=]{16,}", true) {}.impact(2));
        l.add(new PatternRule("TCA-JS-007", "Hard-coded server URL or host", "Configuration", Severity.MAJOR, "An http(s):// URL, hostname or IP address is hard-coded in the script; it breaks when the application is promoted to another environment.", "Put the URL into an environment variable or an EPV and read it with tw.env / tw.epv.", "", "https?://(?!www\\.w3\\.org|schemas\\.)[A-Za-z0-9.\\-]+(?::\\d+)?/|\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", true) { boolean skip(Script s) { return s.object.type.equals("coachView") && s.kind.equals("inline"); } });
        l.add(new PatternRule("TCA-JS-008", "Debug output left in the code", "Quality", Severity.MINOR, "log.info / console.log / alert statements left in scripts flood the logs (server) or interrupt the user (client).", "Remove debug statements or guard them with a log-level EPV; keep log.error for real errors.", "", "\\bconsole\\.(log|debug|info|warn)\\s*\\(|\\balert\\s*\\(|\\blog\\.info\\s*\\(|\\bSystem\\.out\\.print", false) {});
        l.add(new PatternRule("TCA-JS-009", "toString() / XML serialisation of business objects in mappings or scripts", "Performance", Severity.MAJOR, "Converting a complex object to a string (toString(), toXMLString(), XML serialisation) in a mapping or a script is expensive for large objects and lists and usually unnecessary.", "Map the object directly; use JSON.stringify only for small payloads; avoid tw.local.x.toString() in conditions.", "IDA check-service-item-contain-tostring-datamapping", "\\.toXMLString\\s*\\(|\\.toXML\\s*\\(|tw\\.local\\.[A-Za-z0-9_.\\[\\]]+\\.toString\\s*\\(\\)", false) {});
        l.add(new PatternRule("TCA-JS-010", "listLength used inside a loop condition", "Performance", Severity.MINOR, "Using list.listLength (or .length()) in the loop condition re-evaluates the TW list wrapper on every iteration; for large lists this is measurably slower.", "Cache the length in a local variable before the loop: var n = tw.local.items.listLength; for (var i = 0; i < n; i++) ...", "", "for\\s*\\([^;]*;[^;]*\\.(listLength|length\\(\\))\\s*[;<>]", false) {});
        l.add(new PatternRule("TCA-JS-011", "Synchronous XMLHttpRequest / blocking browser call", "Performance", Severity.MAJOR, "A synchronous XMLHttpRequest (async=false) or a blocking dialog freezes the coach UI and is deprecated by browsers.", "Use asynchronous requests (Service Call control, AJAX service via the coach framework) and callbacks/promises.", "", "\\.open\\s*\\([^)]*,\\s*false\\s*\\)|\\bconfirm\\s*\\(|\\bprompt\\s*\\(", false) {});
        l.add(new PatternRule("TCA-JS-012", "Global variable leak (assignment without var/let/const)", "Quality", Severity.MINOR, "A variable is assigned without a declaration and therefore becomes a global (server: shared per execution context; browser: window). Globals collide between coach views and hide bugs.", "Declare every variable (var/let/const) in the narrowest scope.", "", "^\\s*(?!tw\\.|this\\.|var |let |const |return|if|for|while|else|function|\\}|\\{|/)([A-Za-z_$][A-Za-z0-9_$]*)\\s*=[^=]", false) {
            boolean skip(Script s) { return !s.kind.equals("script") && !s.kind.equals("handler") && !s.kind.equals("inline"); }
            public void check(RuleContext c, List<Finding> out) {
                for (Script s : c.allScripts()) { if (skip(s)) continue; String code = strip(s.code); Set<String> declared = new HashSet<>(); Matcher d = Pattern.compile("\\b(?:var|let|const|function)\\s+([A-Za-z_$][A-Za-z0-9_$]*)|\\bvar\\s+[^;]*?,\\s*([A-Za-z_$][A-Za-z0-9_$]*)|function\\s*[A-Za-z_$]*\\s*\\(([^)]*)\\)").matcher(code);
                    while (d.find()) { if (d.group(1) != null) declared.add(d.group(1)); if (d.group(2) != null) declared.add(d.group(2)); if (d.group(3) != null) for (String a : d.group(3).split(",")) declared.add(a.trim()); }
                    Matcher m = p.matcher(code); int n = 0; String ev = ""; Set<String> names = new TreeSet<>();
                    while (m.find()) { String nm = m.group(1); if (declared.contains(nm) || nm.equals("event") || nm.equals("context")) continue; if (names.add(nm)) { n++; if (ev.isEmpty()) ev = line(s.code, code, m.start()); } }
                    if (n > 0) out.add(Finding.of(this, s.object, s.itemId, itemName(s), s.location, n + " undeclared variable(s) " + names + " in " + s.location, ev)); }
            }
        });
        l.add(new PatternRule("TCA-JS-013", "Empty catch block", "Quality", Severity.MAJOR, "An exception is caught and ignored; failures become invisible and later steps run with wrong data.", "Log the error (log.error) and set an error outcome, or rethrow (throw e) so the error event fires.", "", "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}", false) {});
        l.add(new PatternRule("TCA-JS-014", "Use of == null / == undefined comparisons with type coercion", "Quality", Severity.INFO, "Loose equality (==) with strings/numbers relies on type coercion and is a frequent source of subtle bugs (0 == '' is true).", "Use === / !== except for the deliberate == null idiom.", "", "[^=!<>]==[^=]\\s*(?:\"|'|\\d)|(?:\"|'|\\d)\\s*==[^=]", false) { String message(Script s, int n, String ev) { return "Loose equality (==) with a literal in " + s.location + (n > 1 ? " (" + n + " occurrences)" : ""); } });
        l.add(new PatternRule("TCA-JS-015", "tw.system.model / tw.system.executeServiceByName usage", "Quality", Severity.MINOR, "Dynamic service invocation by name (tw.system.executeServiceByName, tw.system.model.findProcessByName) is not visible as a dependency: 'where used' misses it, refactoring breaks it silently and it bypasses type checking.", "Attach the service directly (linked service / sub process step) so the dependency is explicit.", "", "tw\\.system\\.executeServiceByName|tw\\.system\\.model\\.find\\w+ByName|tw\\.system\\.startProcessByName", false) {});
        l.add(new PatternRule("TCA-JS-016", "Direct DOM manipulation with global selectors in a coach view", "UI", Severity.MINOR, "The coach view script queries the whole document (document.getElementById, $('#id'), document.querySelector without this.context.element). With several instances of the view on a page or in a table the wrong element is hit.", "Scope every DOM query to the view: this.context.element.querySelector(...) / $(this.context.element).find(...).", "", "document\\.getElementById\\s*\\(|document\\.querySelector(All)?\\s*\\(|\\$\\s*\\(\\s*[\"']#", false) { boolean skip(Script s) { return !s.object.type.equals("coachView"); } });
        l.add(new PatternRule("TCA-JS-017", "External script or resource loaded from the internet", "Security", Severity.MAJOR, "The coach view loads a script/style from an external host (CDN). Coach pages enforce a Content Security Policy (default-src 'self'), so the load is blocked, and it makes the UI depend on internet access.", "Bundle the library as a toolkit web asset and reference it as a view resource.", "", "https?://(cdnjs|cdn\\.|unpkg|jsdelivr|ajax\\.googleapis|code\\.jquery|maxcdn|fonts\\.googleapis)[^\"'\\s]*", true) { boolean skip(Script s) { return !s.object.type.equals("coachView"); } });
        l.add(new PatternRule("TCA-JS-018", "Deprecated / removed JavaScript API", "Migration", Severity.MINOR, "The script uses an API that is deprecated or removed in BAW (tw.system.coachValidation, heritage coach APIs, escape/unescape, __proto__, arguments.callee, with statement).", "Replace with the supported alternative (tw.system.coachValidation -> client-side validation; encodeURIComponent; Object.getPrototypeOf; named functions).", "", "tw\\.system\\.coachValidation|\\bunescape\\s*\\(|\\bescape\\s*\\(|__proto__|arguments\\.callee|^\\s*with\\s*\\(", false) {});
        l.add(new PatternRule("TCA-JS-019", "Date built from string parsing", "Quality", Severity.INFO, "new Date('...') / Date.parse with non-ISO strings behaves differently per browser and per server locale.", "Use tw.local dates (TWDate), new Date(year, month, day) or ISO 8601 strings.", "", "new\\s+Date\\s*\\(\\s*[\"'][^\"']*[\"']\\s*\\)|Date\\.parse\\s*\\(", true) {});
        l.add(new Rule("TCA-JS-020", "Script too long", "Quality", Severity.MAJOR, "A script step / handler has more than 100 lines (300 = critical). Long scripts are hard to test and review; the logic usually belongs in reusable service flows or toolkit functions.", "Split the script into smaller steps or services; move reusable functions to a toolkit web asset (client) or a general system service (server).", "IDA check-service-javascript-length") {
            public void check(RuleContext c, List<Finding> out) { int med = c.threshold("scriptLinesMedium", 100), high = c.threshold("scriptLinesHigh", 300); for (Script s : c.allScripts()) { int n = s.lines(); if (n > med) { Finding f = Finding.of(this, s.object, s.itemId, itemName(s), s.location, s.location + " has " + n + " lines", ""); if (n > high) { f.severity = Severity.CRITICAL.name(); f.score = Severity.CRITICAL.weight; } out.add(f); } } }
        });
        l.add(new Rule("TCA-JS-021", "JavaScript syntax error", CAT, Severity.CRITICAL, "The script does not parse as JavaScript. It fails at runtime the first time the step runs.", "Fix the syntax (unbalanced braces/quotes, missing operators). Server scripts must be ES5 (Rhino): no arrow functions, let/const, template strings.", "") {
            public void check(RuleContext c, List<Finding> out) { if (!Parse.available()) return; for (Script s : c.allScripts()) { if (!s.kind.equals("script") && !s.kind.equals("handler") && !s.kind.equals("inline")) continue; String err = Parse.firstError(s.code, s.object.type.equals("coachView") ? 200 : 180); if (err != null) out.add(Finding.of(this, s.object, s.itemId, itemName(s), s.location, "Syntax error in " + s.location + ": " + err, "")); } }
        }.impact(2));
        l.add(new Rule("TCA-JS-022", "ES6+ syntax in a server-side script", "Migration", Severity.MAJOR, "Server scripts run on the Rhino engine (ECMAScript 5 in BPM 8.x / BAW 18-20). Arrow functions, let/const, template literals, classes and spread fail at runtime on those versions.", "Use ES5 syntax in server scripts (var, function, string concatenation); keep ES6 for client-side coach view code only.", "") {
            public void check(RuleContext c, List<Finding> out) { Pattern p = Pattern.compile("=>|\\blet\\s+[A-Za-z_$]|\\bconst\\s+[A-Za-z_$]|`|\\bclass\\s+[A-Z]|\\.\\.\\.[A-Za-z_$]"); for (Script s : c.allScripts()) { if (s.object.type.equals("coachView") || !(s.kind.equals("script") || s.kind.equals("condition") || s.kind.equals("mapping") || s.kind.equals("expression"))) continue; Matcher m = p.matcher(strip(s.code)); if (m.find()) out.add(Finding.of(this, s.object, s.itemId, itemName(s), s.location, "ES6 syntax (" + m.group() + ") in server script " + s.location, line(s.code, strip(s.code), m.start()))); } }
        });
        l.add(new Rule("TCA-JS-023", "Deeply nested code", "Quality", Severity.MINOR, "The script nests blocks more than 5 levels deep; deeply nested logic is error prone and hard to test.", "Extract nested blocks into functions / separate steps, use early returns.", "") {
            public void check(RuleContext c, List<Finding> out) { for (Script s : c.allScripts()) { int depth = 0, max = 0; String code = strip(s.code); for (int i = 0; i < code.length(); i++) { char ch = code.charAt(i); if (ch == '{') { depth++; max = Math.max(max, depth); } else if (ch == '}') depth--; } if (max > 5) out.add(Finding.of(this, s.object, s.itemId, itemName(s), s.location, "Nesting depth " + max + " in " + s.location, "")); } }
        });
        return l;
    }

    /** Optional Rhino based syntax check (org.mozilla.javascript on the classpath). */
    static final class Parse {
        static Boolean avail;
        static boolean available() { if (avail == null) { try { Class.forName("org.mozilla.javascript.Parser"); avail = true; } catch (Throwable t) { avail = false; } } return avail; }
        static String syntaxError(String code, int version) {
            try {
                org.mozilla.javascript.CompilerEnvirons env = new org.mozilla.javascript.CompilerEnvirons(); env.setRecoverFromErrors(true); env.setLanguageVersion(version); env.setStrictMode(false); env.setAllowSharpComments(true);
                final StringBuilder err = new StringBuilder();
                env.setErrorReporter(new org.mozilla.javascript.ErrorReporter() {
                    public void warning(String m, String src, int line, String ls, int off) {}
                    public void error(String m, String src, int line, String ls, int off) { if (err.length() == 0) err.append("line ").append(line).append(": ").append(m); }
                    public org.mozilla.javascript.EvaluatorException runtimeError(String m, String src, int line, String ls, int off) { return new org.mozilla.javascript.EvaluatorException(m); }
                });
                new org.mozilla.javascript.Parser(env).parse("function __tca__(event, context) {\n" + code + "\n}", "script", 0); return err.length() == 0 ? null : err.toString();
            } catch (Throwable t) { String m = t.getMessage(); return m == null ? "syntax error" : m.replaceAll("\\s+", " "); }
        }
        static String firstError(String code, int version) {
            try {
                org.mozilla.javascript.CompilerEnvirons env = new org.mozilla.javascript.CompilerEnvirons(); env.setRecoverFromErrors(true); env.setLanguageVersion(version); env.setStrictMode(false);
                final StringBuilder err = new StringBuilder();
                env.setErrorReporter(new org.mozilla.javascript.ErrorReporter() {
                    public void warning(String m, String src, int line, String ls, int off) {}
                    public void error(String m, String src, int line, String ls, int off) { if (err.length() == 0) err.append("line ").append(Math.max(1, line)).append(": ").append(m).append(ls != null && !ls.trim().isEmpty() ? " -> " + ls.trim() : ""); }
                    public org.mozilla.javascript.EvaluatorException runtimeError(String m, String src, int line, String ls, int off) { return new org.mozilla.javascript.EvaluatorException(m); }
                });
                try { new org.mozilla.javascript.Parser(env).parse("function __tca__(event, context) {\n" + code + "\n}", "script", 0); } catch (Throwable t) { if (err.length() == 0) err.append(String.valueOf(t.getMessage())); }
                return err.length() == 0 ? null : err.toString();
            } catch (Throwable t) { return String.valueOf(t.getMessage()); }
        }
    }
}

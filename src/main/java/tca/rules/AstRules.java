package tca.rules;

import java.util.*;
import java.util.regex.*;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;
import tca.model.*;
import tca.util.Xml;

/** JavaScript rules that need the syntax tree (Rhino parser): scope-aware identifier resolution, undeclared process variables,
 *  straight-line constant propagation (division by zero, null access), loops without exit, eval of process data, parseInt radix ...
 *  Every script is parsed once per analysis (cached in the rule context); scripts with syntax errors are skipped here (TCA-JS-021 reports them). */
public final class AstRules {
    private AstRules() {}
    static final String PREFIX = "function __tca__(event, context) {\n";
    static final Set<String> SERVER_GLOBALS = set("tw log Object Array Function String Number Boolean Date Math RegExp Error TypeError RangeError EvalError ReferenceError SyntaxError URIError NaN Infinity undefined parseInt parseFloat isNaN isFinite decodeURI decodeURIComponent encodeURI encodeURIComponent escape unescape eval JSON arguments Packages java javax com org net importPackage importClass JavaImporter JavaAdapter"
            + " TWDate TWSearch TWSearchColumn TWSearchCondition TWSearchOrdering TWProcessInstance TWProcess TWProcessApp TWProcessAppSnapshot TWTask TWUser TWRole TWParticipantGroup TWTeam TWDocument TWManagedFile TWLink TWEvent TWHolidaySchedule TWTimeSchedule TWTimePeriod TWWorkSchedule TWTimerInstance TWObject TWMap TWDocumentFolder TWServer TWEnvironment"
            + " BPMRESTRequest BPMRESTResponse XMLDocument XMLElement XMLNodeList XMLNodelist Serializer Map Record IndexedMap JSONObject SLAViolationRecord TWUserLocalePreferences Step ConditionalActivity Integer Decimal Time URL ANY XML XMLList Namespace QName"
            + " console Set WeakMap WeakSet Promise Symbol Iterator StopIteration ArrayBuffer DataView Uint8Array Int8Array Uint16Array Int16Array Uint32Array Int32Array Float32Array Float64Array globalThis");
    static final Set<String> CLIENT_GLOBALS = set("window document navigator location history screen alert confirm prompt setTimeout clearTimeout setInterval clearInterval requestAnimationFrame cancelAnimationFrame require define dojo dijit dojox bpmext com jQuery $ _ page self parent top frames"
            + " Event CustomEvent XMLHttpRequest fetch FormData Blob File FileReader URL URLSearchParams localStorage sessionStorage HTMLElement Element Node NodeList Image Audio Option atob btoa performance getComputedStyle MutationObserver IntersectionObserver ResizeObserver Intl crypto DOMParser XMLSerializer TextEncoder TextDecoder queueMicrotask structuredClone"
            + " resetDataSyncronizationVariables initializeDataSyncronizationVariables bpmExt Handlebars moment d3 Chart");
    static Set<String> set(String s) { return new HashSet<>(Arrays.asList(s.trim().split("\\s+"))); }
    static boolean isServer(Script s) { return !s.object.type.equals("coachView") && (s.kind.equals("script") || s.kind.equals("assignment") || s.kind.equals("condition") || s.kind.equals("mapping") || s.kind.equals("expression")); }
    static boolean isClient(Script s) { return s.object.type.equals("coachView") && (s.kind.equals("handler") || s.kind.equals("inline")); }

    /** Parsed script: the function body of the wrapper, line starts for positions, declarations. */
    static final class Parsed {
        final Script script; final FunctionNode fn; final int[] lineStarts;
        Parsed(Script s, FunctionNode f) { script = s; fn = f; List<Integer> ls = new ArrayList<>(); ls.add(0); for (int i = 0; i < s.code.length(); i++) if (s.code.charAt(i) == '\n') ls.add(i + 1); lineStarts = new int[ls.size()]; for (int i = 0; i < lineStarts.length; i++) lineStarts[i] = ls.get(i); }
        int offset(AstNode n) { return n.getAbsolutePosition() - PREFIX.length(); }
        int line(AstNode n) { int off = offset(n); int lo = 0, hi = lineStarts.length - 1; while (lo < hi) { int mid = (lo + hi + 1) / 2; if (lineStarts[mid] <= off) lo = mid; else hi = mid - 1; } return lo + 1; }
        int column(AstNode n) { int off = offset(n); return off - lineStarts[line(n) - 1] + 1; }
        String sourceLine(AstNode n) { int l = line(n) - 1; if (l < 0 || l >= lineStarts.length) return ""; int a = lineStarts[l], b = l + 1 < lineStarts.length ? lineStarts[l + 1] - 1 : script.code.length(); String t = script.code.substring(a, Math.max(a, b)).trim(); return t.length() > 160 ? t.substring(0, 160) + "..." : t; }
        Finding finding(Rule r, AstNode n, String message) { return Finding.of(r, script.object, script.itemId, ScriptRules.itemName(script), script.location, message, "").at(line(n), column(n), sourceLine(n)); }
    }

    /** Parses every server / client script once; null entries = syntax error (skipped). */
    @SuppressWarnings("unchecked")
    static Map<Script, Parsed> parsed(RuleContext c) {
        Map<Script, Parsed> m = (Map<Script, Parsed>) c.cache.get("ast");
        if (m != null) return m;
        m = new IdentityHashMap<>(); c.cache.put("ast", m);
        if (!ScriptRules.Parse.available()) return m;
        for (Script s : c.allScripts()) {
            if (!isServer(s) && !isClient(s)) continue;
            if (s.code.trim().isEmpty()) continue;
            Parsed p = parse(s); c.scriptsParsed++; if (p == null) c.scriptsWithSyntaxErrors++; m.put(s, p);
        }
        return m;
    }
    static Parsed parse(Script s) {
        try {
            org.mozilla.javascript.CompilerEnvirons env = new org.mozilla.javascript.CompilerEnvirons(); env.setLanguageVersion(s.object.type.equals("coachView") ? 200 : 180); env.setStrictMode(false); env.setRecoverFromErrors(false);
            final boolean[] err = { false };
            env.setErrorReporter(new org.mozilla.javascript.ErrorReporter() {
                public void warning(String m, String src, int line, String ls, int off) {}
                public void error(String m, String src, int line, String ls, int off) { err[0] = true; }
                public org.mozilla.javascript.EvaluatorException runtimeError(String m, String src, int line, String ls, int off) { return new org.mozilla.javascript.EvaluatorException(m); }
            });
            AstRoot root = new org.mozilla.javascript.Parser(env).parse(PREFIX + s.code + "\n}", "script", 0);
            if (err[0]) return null;
            for (org.mozilla.javascript.Node n : root) if (n instanceof FunctionNode) return new Parsed(s, (FunctionNode) n);
            return null;
        } catch (Throwable t) { return null; }
    }
    /** Top-level function / var names declared in a script (visible to the other scripts of the same artifact in the same execution scope). */
    static Set<String> topLevelNames(Parsed p) { Set<String> s = new HashSet<>(); if (p == null) return s; for (org.mozilla.javascript.Node n : p.fn.getBody()) { if (n instanceof FunctionNode && ((FunctionNode) n).getFunctionName() != null) s.add(((FunctionNode) n).getName()); if (n instanceof VariableDeclaration) for (VariableInitializer v : ((VariableDeclaration) n).getVariables()) if (v.getTarget() instanceof Name) s.add(((Name) v.getTarget()).getIdentifier()); } return s; }
    /** Names defined by the JavaScript server files of the application and its toolkits (functions and top-level vars of managed *.js assets). */
    @SuppressWarnings("unchecked")
    static Set<String> serverFileNames(RuleContext c) {
        Set<String> s = (Set<String>) c.cache.get("serverFileNames"); if (s != null) return s;
        s = new HashSet<>(); c.cache.put("serverFileNames", s); Pattern p = Pattern.compile("(?m)^\\s*(?:function\\s+([A-Za-z_$][\\w$]*)|var\\s+([A-Za-z_$][\\w$]*))");
        for (TwxPackage pk : c.twx.allPackages()) for (Map.Entry<String, String> e : pk.jsFiles.entrySet()) { TwxObject a = pk.objects.get(e.getKey()); if (a == null || !Xml.text(a.xml, "assetTypeCode").equals("J")) continue; Matcher m = p.matcher(ScriptRules.strip(e.getValue())); while (m.find()) s.add(m.group(1) != null ? m.group(1) : m.group(2)); }
        return s;
    }
    /** Global names defined by each web (.js) managed asset, keyed by asset id: top-level function / var, x = function, window.x =, plus the file-name stem
     *  (minified libraries expose one global named like the file: numeral.min.js -> numeral, jquery-3.6.0.min.js -> jquery). */
    @SuppressWarnings("unchecked")
    static Map<String, Set<String>> webAssetNames(RuleContext c) {
        Map<String, Set<String>> m = (Map<String, Set<String>>) c.cache.get("webAssetNames"); if (m != null) return m;
        m = new HashMap<>(); c.cache.put("webAssetNames", m);
        Pattern p = Pattern.compile("(?m)^\\s*(?:function\\s+([A-Za-z_$][\\w$]*)|var\\s+([A-Za-z_$][\\w$]*)|([A-Za-z_$][\\w$]*)\\s*=\\s*function\\b)|(?:window|globalThis|self)\\.([A-Za-z_$][\\w$]*)\\s*=");
        for (TwxPackage pk : c.twx.allPackages()) for (Map.Entry<String, String> e : pk.jsFiles.entrySet()) { TwxObject a = pk.objects.get(e.getKey()); if (a == null || Xml.text(a.xml, "assetTypeCode").equals("J")) continue;
            Set<String> s = new HashSet<>(); String stem = a.name.replaceAll("(?i)\\.js$", "").replaceAll("[.-].*$", ""); if (!stem.isEmpty()) s.add(stem);
            Matcher mm = p.matcher(e.getValue()); while (mm.find()) for (int g = 1; g <= 4; g++) if (mm.group(g) != null) s.add(mm.group(g));
            m.put(a.id, s); }
        return m;
    }
    /** Web asset id from a coach view resource reference (assetUuid "<project uuid>/61.<id>" or a file path). */
    static String assetId(String resource) { int i = resource.lastIndexOf('/'); return i >= 0 ? resource.substring(i + 1) : resource; }
    static AstNode unwrap(AstNode n) { while (n instanceof ParenthesizedExpression) n = ((ParenthesizedExpression) n).getExpression(); return n; }
    static boolean isZero(AstNode n) { n = unwrap(n); return n instanceof NumberLiteral && ((NumberLiteral) n).getNumber() == 0; }
    static boolean isNullish(AstNode n) { n = unwrap(n); return (n instanceof KeywordLiteral && n.getType() == Token.NULL) || (n instanceof Name && ((Name) n).getIdentifier().equals("undefined")) || (n instanceof UnaryExpression && ((UnaryExpression) n).getOperator() == Token.VOID); }
    static boolean isTrue(AstNode n) { n = unwrap(n); return (n instanceof KeywordLiteral && n.getType() == Token.TRUE) || (n instanceof NumberLiteral && ((NumberLiteral) n).getNumber() != 0) || n == null || n instanceof EmptyExpression; }
    static String key(AstNode n) { n = unwrap(n); if (n instanceof Name) return ((Name) n).getIdentifier(); if (n instanceof PropertyGet) { String t = key(((PropertyGet) n).getTarget()); return t == null ? null : t + "." + ((PropertyGet) n).getProperty().getIdentifier(); } if (n instanceof KeywordLiteral && n.getType() == Token.THIS) return "this"; return null; }
    static boolean mentions(AstNode n, final String prefix) { final boolean[] r = { false }; n.visit(new NodeVisitor() { public boolean visit(AstNode x) { String k = key(x); if (k != null && k.startsWith(prefix)) r[0] = true; return !r[0]; } }); return r[0]; }
    /** Is the Name node a reference (read of a variable) rather than a declaration, property name, label or object key? */
    static boolean isReference(Name n) {
        AstNode p = n.getParent(); if (p == null) return false;
        if (p instanceof PropertyGet && ((PropertyGet) p).getProperty() == n) return false;
        if (p instanceof ObjectProperty && ((ObjectProperty) p).getLeft() == n) return false;
        if (p instanceof FunctionNode) return false;                                    // function name or parameter
        if (p instanceof VariableInitializer && ((VariableInitializer) p).getTarget() == n) return false;
        if (p instanceof CatchClause || p instanceof BreakStatement || p instanceof ContinueStatement || p instanceof Label || p instanceof LabeledStatement) return false;
        if (p instanceof Assignment && ((Assignment) p).getLeft() == n && ((Assignment) p).getOperator() == Token.ASSIGN) return false;   // write: TCA-JS-012
        if (p instanceof UnaryExpression && ((UnaryExpression) p).getOperator() == Token.TYPEOF) return false;
        if (p instanceof ForInLoop && ((ForInLoop) p).getIterator() == n) return false;   // for (x in obj) without var: implicit global write, TCA-JS-012
        return true;
    }
    /** Declared by an enclosing catch (e) clause? (Rhino does not register the catch variable in the scope chain.) */
    static boolean caught(Name n) { String id = n.getIdentifier(); for (AstNode p = n.getParent(); p != null; p = p.getParent()) { if (p instanceof FunctionNode) return false; if (p instanceof CatchClause && ((CatchClause) p).getVarName() != null && id.equals(((CatchClause) p).getVarName().getIdentifier())) return true; } return false; }
    static boolean resolved(Name n) { Scope s = n.getEnclosingScope(); return (s != null && s.getDefiningScope(n.getIdentifier()) != null) || caught(n); }

    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-JS-040", "Undefined identifier in a server-side script", "Script", Severity.MAJOR, "The script reads a variable or function that is declared nowhere: not in the script, not a BAW JavaScript API object (tw, log, TWDate ...), not a function of a server file of the application or its toolkits. Rhino throws a ReferenceError the first time the statement runs.",
                "Declare the variable (var) or use the intended tw.local / tw.system member; move shared functions into a JavaScript server file (managed asset) or a general system service.", "") {
            public void check(RuleContext c, List<Finding> out) {
                Map<Script, Parsed> ps = parsed(c); Set<String> files = serverFileNames(c); Map<String, Set<String>> perObject = new HashMap<>();
                for (Map.Entry<Script, Parsed> e : ps.entrySet()) if (isServer(e.getKey())) { Set<String> s = perObject.get(e.getKey().object.id); if (s == null) { s = new HashSet<>(); perObject.put(e.getKey().object.id, s); } s.addAll(topLevelNames(e.getValue())); }
                for (Map.Entry<Script, Parsed> e : ps.entrySet()) {
                    final Parsed p = e.getValue(); if (p == null || !isServer(e.getKey())) continue;
                    final Set<String> known = perObject.get(e.getKey().object.id); final Map<String, Name> undefined = new LinkedHashMap<>();
                    p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof Name) { Name n = (Name) x; String id = n.getIdentifier(); if (isReference(n) && !resolved(n) && !SERVER_GLOBALS.contains(id) && !files.contains(id) && !known.contains(id) && !undefined.containsKey(id)) undefined.put(id, n); } return true; } });
                    for (Map.Entry<String, Name> u : undefined.entrySet()) out.add(p.finding(this, u.getValue(), "'" + u.getKey() + "' is used but never declared in " + e.getKey().location));
                }
            }
        });
        l.add(new Rule("TCA-UI-020", "Unresolved identifier in a coach view script", "UI", Severity.MINOR, "A coach view handler or inline script uses a name that is declared neither in the view's scripts, nor as an AMD dependency argument, nor as a common browser / BPM coach global. It is either a typo or a global provided by an included web asset (fragile: it depends on the load order of the assets).",
                "Declare the name in the view's inline script, add the library as an AMD dependency (functionArgument) or read it from this.context / window explicitly.", "") {
            public void check(RuleContext c, List<Finding> out) {
                Map<Script, Parsed> ps = parsed(c); Map<String, Set<String>> perView = new HashMap<>();
                for (Map.Entry<Script, Parsed> e : ps.entrySet()) if (isClient(e.getKey())) { TwxObject o = e.getKey().object; Set<String> s = perView.get(o.id); if (s == null) { s = new HashSet<>(); perView.put(o.id, s); for (String b : Xml.blocks(o.xml, "amdDependency")) { String arg = Xml.text(b, "functionArgument"); if (!arg.isEmpty()) s.add(arg); } for (String b : Xml.blocks(o.xml, "configOption")) s.add(Xml.text(b, "name")); for (String r : c.coachView(o).resources) { Set<String> w = webAssetNames(c).get(assetId(r)); if (w != null) s.addAll(w); } } s.addAll(topLevelNames(e.getValue())); }
                Map<String, String> anyWeb = new HashMap<>();   // name -> web asset file that defines it (for names the view does not include)
                for (Map.Entry<String, Set<String>> w : webAssetNames(c).entrySet()) { TwxObject a = c.twx.find(w.getKey()); if (a != null) for (String n : w.getValue()) anyWeb.put(n, a.name); }
                for (Map.Entry<Script, Parsed> e : ps.entrySet()) {
                    final Parsed p = e.getValue(); if (p == null || !isClient(e.getKey())) continue;
                    final Set<String> known = perView.get(e.getKey().object.id); final Map<String, Name> undefined = new LinkedHashMap<>();
                    p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof Name) { Name n = (Name) x; String id = n.getIdentifier(); if (isReference(n) && !resolved(n) && !SERVER_GLOBALS.contains(id) && !CLIENT_GLOBALS.contains(id) && !known.contains(id) && !undefined.containsKey(id)) undefined.put(id, n); } return true; } });
                    if (undefined.isEmpty()) continue;
                    StringBuilder hint = new StringBuilder(); for (String n : undefined.keySet()) if (anyWeb.containsKey(n)) hint.append(hint.length() == 0 ? " (" : ", ").append(n).append(" is defined in web asset ").append(anyWeb.get(n)).append(" which this view does not include"); if (hint.length() > 0) hint.append(")");
                    out.add(p.finding(this, undefined.values().iterator().next(), undefined.size() + " unresolved name(s) " + undefined.keySet() + " in " + e.getKey().location + hint));
                }
            }
        }.review());
        l.add(new Rule("TCA-JS-041", "Undeclared process variable (tw.local.x)", "Script", Severity.MAJOR, "The script, condition, mapping or template uses tw.local.<name> but the service / process declares no parameter or private variable with that name. At runtime the value is undefined (reads) or the assignment fails.",
                "Declare the variable in the Variables tab of the service / process, or correct the name (variables of a called service are not visible in the caller).", "") {
            public void check(RuleContext c, List<Finding> out) {
                Pattern p = Pattern.compile("\\btw\\.local\\.([A-Za-z_$][\\w$]*)|\\btw\\.local\\[\\s*[\"']([A-Za-z_$][\\w$]*)[\"']\\s*\\]");
                for (Script s : c.allScripts()) {
                    if (s.object.type.equals("coachView")) continue; Set<String> declared = c.declaredVariables(s.object); if (declared.isEmpty() && !(s.object.type.equals("process") || s.object.type.equals("bpd"))) continue;
                    String code = s.kind.equals("template") ? s.code : ScriptRules.strip(s.code); Matcher m = p.matcher(code); Map<String, Integer> missing = new LinkedHashMap<>();
                    while (m.find()) { String name = m.group(1) != null ? m.group(1) : m.group(2); if (!declared.contains(name) && !missing.containsKey(name)) missing.put(name, m.start()); }
                    for (Map.Entry<String, Integer> e : missing.entrySet()) out.add(Finding.of(this, s.object, s.itemId, ScriptRules.itemName(s), s.location, "tw.local." + e.getKey() + " is not declared in " + s.object.typeLabel().toLowerCase() + " '" + s.object.name + "' (used in " + s.location + ")", ScriptRules.line(s.code, code, e.getValue())));
                }
            }
        });
        l.add(new Rule("TCA-JS-042", "Definite division by zero", "Script", Severity.CRITICAL, "An expression divides by the literal 0 or by a variable that straight-line code just set to 0. The result is Infinity or NaN and propagates silently into business data.",
                "Check the divisor before dividing (if (n === 0) ...), or fix the constant.", "") {
            public void check(RuleContext c, List<Finding> out) { for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null) continue; final Map<String, String> values = new HashMap<>();
                for (org.mozilla.javascript.Node st : p.fn.getBody()) { final AstNode stmt = (AstNode) st; track(stmt, values);
                    stmt.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof FunctionNode) return false; AstNode div = null; if (x instanceof InfixExpression && !(x instanceof Assignment) && ((InfixExpression) x).getOperator() == Token.DIV) div = ((InfixExpression) x).getRight(); if (x instanceof Assignment && ((Assignment) x).getOperator() == Token.ASSIGN_DIV) div = ((Assignment) x).getRight();
                        if (div != null && (isZero(div) || "0".equals(values.get(key(div))))) out.add(p.finding(TCA_JS_042(), x, "Division by zero" + (isZero(div) ? "" : " ('" + key(div) + "' was set to 0)") + " in " + p.script.location)); return true; } }); }
            } }
            Rule TCA_JS_042() { return this; }
        });
        l.add(new Rule("TCA-JS-043", "Definite null / undefined property access", "Script", Severity.CRITICAL, "A property or element is read from the literal null / undefined, or from a variable that straight-line code just set to null or undefined. The statement throws a TypeError every time it runs.",
                "Initialise the variable before using its members, or guard the access (if (x) ...).", "") {
            public void check(RuleContext c, List<Finding> out) { for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null) continue; final Map<String, String> values = new HashMap<>();
                for (org.mozilla.javascript.Node st : p.fn.getBody()) { final AstNode stmt = (AstNode) st; track(stmt, values);
                    stmt.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof FunctionNode) return false; AstNode target = x instanceof PropertyGet ? ((PropertyGet) x).getTarget() : x instanceof ElementGet ? ((ElementGet) x).getTarget() : null;
                        if (target != null) { String k = key(target); boolean lit = isNullish(target), var = k != null && values.containsKey(k) && !"0".equals(values.get(k)); if (lit || var) out.add(p.finding(self(), x, "Property access on " + (lit ? "null / undefined" : "'" + k + "' which was set to " + values.get(k)) + " in " + p.script.location)); } return true; } }); }
            } }
            Rule self() { return this; }
        });
        l.add(new Rule("TCA-JS-044", "eval / Function constructor executed on process data", "Security", Severity.CRITICAL, "eval() or new Function() runs code built from tw.local values (user input, data from other systems). Anyone who controls that data executes arbitrary JavaScript on the server (or in the browser).",
                "Never evaluate data as code: use JSON.parse for JSON strings, explicit property access (obj[name]) or a switch on the allowed values.", "") {
            public void check(RuleContext c, List<Finding> out) { for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null) continue;
                p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof FunctionCall) { FunctionCall f = (FunctionCall) x; String t = key(f.getTarget()); if ("eval".equals(t) || (x instanceof NewExpression && "Function".equals(t))) for (AstNode a : f.getArguments()) if (mentions(a, "tw.local")) { out.add(p.finding(self(), x, t + " executed on tw.local data in " + p.script.location)); break; } } return true; } }); } }
            Rule self() { return this; }
        }.impact(2));
        l.add(new Rule("TCA-JS-045", "Loop with no demonstrable exit", "Script", Severity.MAJOR, "A while(true) / for(;;) / do..while(true) loop contains no break, return or throw that leaves it. Unless an exception happens the step never ends (the transaction times out and the thread is blocked).",
                "Add an explicit exit condition (loop counter with a maximum, break when the work is done).", "") {
            public void check(RuleContext c, List<Finding> out) { for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null) continue;
                p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { AstNode cond = null, body = null; if (x instanceof WhileLoop) { cond = ((WhileLoop) x).getCondition(); body = ((WhileLoop) x).getBody(); } else if (x instanceof DoLoop) { cond = ((DoLoop) x).getCondition(); body = ((DoLoop) x).getBody(); } else if (x instanceof ForLoop) { cond = ((ForLoop) x).getCondition(); body = ((ForLoop) x).getBody(); }
                    if (body != null && isTrue(cond) && !hasExit(body, (Loop) x)) out.add(p.finding(self(), x, "Endless loop without break / return / throw in " + p.script.location)); return true; } }); } }
            Rule self() { return this; }
        });
        l.add(new Rule("TCA-JS-046", "Active debugger statement", "Quality", Severity.MAJOR, "A debugger statement is left in the code. In the browser it freezes the coach whenever developer tools are open; on the server it is dead weight that documents unfinished debugging.", "Remove the debugger statement.", "") {
            public void check(RuleContext c, List<Finding> out) { Pattern p = Pattern.compile("(?m)^\\s*debugger\\s*;?|\\bdebugger\\s*;"); for (Script s : c.allScripts()) { if (s.kind.equals("template")) continue; String code = ScriptRules.strip(s.code); Matcher m = p.matcher(code); if (m.find()) out.add(Finding.of(this, s.object, s.itemId, ScriptRules.itemName(s), s.location, "debugger statement in " + s.location, ScriptRules.line(s.code, code, m.start()))); } }
        });
        l.add(new Rule("TCA-JS-047", "parseInt without an explicit radix", "Quality", Severity.MINOR, "parseInt(value) without the radix argument interprets strings with a leading 0 as octal on older engines (Rhino, old browsers): parseInt('08') gives 0.",
                "Always pass the radix: parseInt(value, 10).", "") {
            public void check(RuleContext c, List<Finding> out) { for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null) continue; final int[] n = { 0 }; final AstNode[] first = { null };
                p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof FunctionCall && !(x instanceof NewExpression)) { FunctionCall f = (FunctionCall) x; if (f.getTarget() instanceof Name && ((Name) f.getTarget()).getIdentifier().equals("parseInt") && !resolved((Name) f.getTarget()) && f.getArguments().size() < 2) { n[0]++; if (first[0] == null) first[0] = x; } } return true; } });
                if (n[0] > 0) out.add(p.finding(self(), first[0], "parseInt without radix in " + p.script.location + (n[0] > 1 ? " (" + n[0] + " occurrences)" : ""))); } }
            Rule self() { return this; }
        });
        l.add(new Rule("TCA-JS-048", "Hard-coded business constant", "Quality", Severity.INFO, "The script compares or assigns short upper-case string codes (status codes such as 'A', 'NY', 'USD'). Business constants scattered over scripts are hard to change consistently and hide the meaning of the code.",
                "Keep such codes in an exposed process value (EPV) or a documented constants server file and reference them by name.", "") {
            public void check(RuleContext c, List<Finding> out) { for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null) continue; final Set<String> vals = new TreeSet<>(); final AstNode[] first = { null }; final int[] n = { 0 };
                p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { if (x instanceof StringLiteral) { String v = ((StringLiteral) x).getValue(); AstNode par = x.getParent(); boolean key = par instanceof ObjectProperty && ((ObjectProperty) par).getLeft() == x; if (!key && v.matches("[A-Z]{1,3}")) { n[0]++; vals.add(v); if (first[0] == null) first[0] = x; } } return true; } });
                if (n[0] > 0) out.add(p.finding(self(), first[0], n[0] + " hard-coded code literal(s) " + vals + " in " + p.script.location)); } }
            Rule self() { return this; }
        }.review());
        l.add(new Rule("TCA-JS-049", "Possible dynamic SQL construction", "Security", Severity.MINOR, "A variable named like a SQL statement (sql, query, stmt ...) is built by string concatenation, or a query / execute method receives a concatenated string. If any concatenated part comes from user or external data the statement is injectable.",
                "Use parameter markers with the SQL services (SQLParameter list) or a prepared statement; concatenate only trusted identifiers.", "") {
            public void check(RuleContext c, List<Finding> out) { final Pattern var = Pattern.compile("(?i)(sql|query|stmt|statement)"), method = Pattern.compile("(?i)^(execute\\w*|query|runQuery|select|update|insert|delete)$"); for (Map.Entry<Script, Parsed> e : parsed(c).entrySet()) { final Parsed p = e.getValue(); if (p == null || p.script.object.type.equals("coachView")) continue; final AstNode[] first = { null }; final int[] n = { 0 };
                p.fn.visit(new NodeVisitor() { public boolean visit(AstNode x) { boolean hit = false;
                    if (x instanceof VariableInitializer) { VariableInitializer v = (VariableInitializer) x; hit = v.getTarget() instanceof Name && var.matcher(((Name) v.getTarget()).getIdentifier()).find() && isConcat(v.getInitializer()); }
                    else if (x instanceof Assignment) { Assignment a = (Assignment) x; String k = key(a.getLeft()); hit = k != null && var.matcher(k).find() && (a.getOperator() == Token.ASSIGN_ADD || isConcat(a.getRight())); }
                    else if (x instanceof FunctionCall && ((FunctionCall) x).getTarget() instanceof PropertyGet) { FunctionCall f = (FunctionCall) x; hit = method.matcher(((PropertyGet) f.getTarget()).getProperty().getIdentifier()).find() && !f.getArguments().isEmpty() && isConcat(f.getArguments().get(0)); }
                    if (hit) { n[0]++; if (first[0] == null) first[0] = x; } return true; } });
                if (n[0] > 0) out.add(p.finding(self(), first[0], "SQL / query string built by concatenation in " + p.script.location + (n[0] > 1 ? " (" + n[0] + " places)" : ""))); } }
            Rule self() { return this; }
        }.review());
        return l;
    }
    static boolean isConcat(AstNode n) { n = unwrap(n); return n instanceof InfixExpression && !(n instanceof Assignment) && ((InfixExpression) n).getOperator() == Token.ADD && (containsString(((InfixExpression) n).getLeft()) || containsString(((InfixExpression) n).getRight())); }
    static boolean containsString(AstNode n) { n = unwrap(n); if (n instanceof StringLiteral) return true; if (n instanceof InfixExpression && !(n instanceof Assignment) && ((InfixExpression) n).getOperator() == Token.ADD) return containsString(((InfixExpression) n).getLeft()) || containsString(((InfixExpression) n).getRight()); return false; }
    /** Straight-line constant tracking over the top-level statements: var x = 0 / x = null are remembered, any other statement kind resets the knowledge. */
    static void track(AstNode stmt, Map<String, String> values) {
        if (stmt instanceof VariableDeclaration) { for (VariableInitializer v : ((VariableDeclaration) stmt).getVariables()) if (v.getTarget() instanceof Name) remember(((Name) v.getTarget()).getIdentifier(), v.getInitializer(), values); return; }
        if (stmt instanceof ExpressionStatement) { AstNode x = unwrap(((ExpressionStatement) stmt).getExpression()); if (x instanceof Assignment) { Assignment a = (Assignment) x; String k = key(a.getLeft()); if (k != null) { if (a.getOperator() == Token.ASSIGN) remember(k, a.getRight(), values); else values.remove(k); } return; } if (x instanceof FunctionCall) return; }
        if (stmt instanceof ReturnStatement || stmt instanceof ThrowStatement || stmt instanceof FunctionNode) return;
        values.clear();
    }
    static void remember(String k, AstNode init, Map<String, String> values) { if (init == null) { values.remove(k); return; } /* var x; is a hoisted declaration assigned later, not a known undefined */ if (isZero(init)) values.put(k, "0"); else if (isNullish(init)) values.put(k, unwrap(init) instanceof KeywordLiteral ? "null" : "undefined"); else values.remove(k); }
    /** break (unlabelled, not swallowed by a nested loop / switch), labelled break out of the loop, return or throw (not inside a nested function). */
    static boolean hasExit(AstNode body, final Loop loop) {
        final boolean[] r = { false };
        body.visit(new NodeVisitor() { public boolean visit(AstNode x) {
            if (r[0]) return false;
            if (x instanceof FunctionNode) return false;
            if (x instanceof ReturnStatement || x instanceof ThrowStatement) { r[0] = true; return false; }
            if (x instanceof BreakStatement) { BreakStatement b = (BreakStatement) x; if (b.getBreakLabel() != null) { String lbl = b.getBreakLabel().getIdentifier(); AstNode p = loop.getParent(); while (p != null) { if (p instanceof LabeledStatement && ((LabeledStatement) p).getLabelByName(lbl) != null) { r[0] = true; break; } p = p.getParent(); } return false; }
                for (AstNode p = b.getParent(); p != null && p != loop; p = p.getParent()) if (p instanceof Loop || p instanceof SwitchStatement) return false; r[0] = true; return false; }
            return true; } });
        return r[0];
    }
}

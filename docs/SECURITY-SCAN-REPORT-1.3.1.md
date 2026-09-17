# Security scan report - TWX Code Analyzer 1.3.1

Date: 2026-09-17. Subject: the source tree, the built artifacts (engine jar, `twx-code-analyzer-jakarta.war`) and a running deployment of the web application (enterprise mode, Tomcat 10.1.60, JDK 17) of version 1.3.1. Purpose: evidence for onboarding reviews. Every finding below is listed with its disposition; nothing was suppressed in the tools' configuration.

## Tools

| Class | Tool | Version | What was scanned |
|---|---|---|---|
| SAST | GitHub CodeQL (default suite, `java-kotlin`, `javascript`) | 2.27.0 | repository on every push to `master` |
| SAST | Semgrep (`p/java`, `p/javascript`, `p/security-audit`, `p/owasp-top-ten`, `p/secrets`) | 1.177.0 | source tree, Dockerfile |
| SAST | SpotBugs + FindSecBugs, effort max, all priorities | 4.10.4 + 1.14.0 | compiled classes of the jar and of the WAR (`tca.*`) |
| SCA | Trivy (vuln, secret, misconfig) | 0.74.0 | source tree, Dockerfile, extracted WAR |
| SCA | Retire.js | 5.7.0 | bundled browser libraries (`static/vendor`, Swagger UI) |
| Secrets | gitleaks | 8.30.1 | full git history |
| DAST | OWASP ZAP - spider, OpenAPI import, full active scan, passive scan | 2.17.0 | running WAR, authenticated (session cookie + Origin), 24 600 requests, all 60 active rules completed except DOM XSS (needs a browser) and Log4Shell (needs an OAST service) |

Not run: OWASP Dependency-Check (needs an NVD API key; its jar and JavaScript coverage is the same as Trivy + Retire.js here), commercial scanners (Checkmarx, Fortify, SonarQube, Snyk, Burp). The project is plain `javac` with one runtime dependency (Rhino, shaded) and three compile-time servlet API jars, so any of them can be pointed at the tree without a build system.

## Summary

| Tool | Findings on 1.3 / first pass | After the fixes in 1.3.1 |
|---|---|---|
| CodeQL | 43 (31 path injection, 9 polynomial ReDoS, regex injection, trust-all TrustManager, open redirect) | **0** |
| Semgrep | 5 (Docker root user, `SSLContext "TLS"`, 2 × `java.util.Random`, unencrypted SMTP socket) | 1 accepted (SMTP socket, see below) |
| SpotBugs / FindSecBugs | 91 security-category matches | 46 pattern matches, all dispositioned below (no taint-confirmed defect) |
| Trivy | Dockerfile: root user (HIGH), no HEALTHCHECK (LOW); no vulnerable package | **0** |
| Retire.js | Swagger UI 5.17.14 bundling DOMPurify 3.1.4 with 20 CVEs (medium / low) | **0** (Swagger UI 5.33.0, DOMPurify 3.4.13) |
| gitleaks | 0 | 0 |
| ZAP | 1 Medium (no CSP), 2 Low (500 with error text on malformed input), 4 Medium heuristics (500 on over-long ids) | 3 Medium `CSP: style-src unsafe-inline` (accepted, see below), 66 Low "timestamp disclosure" (false positives: numeric constants inside the minified Swagger UI bundle), rest informational |

## Fixed in 1.3.1

CodeQL findings and their fixes are described in [SECURITY-REVIEW-WAR.md](SECURITY-REVIEW-WAR.md) (addendum) and [CHANGELOG.md](CHANGELOG.md). Fixes driven by the other scanners:

| Finding | Tool | Fix |
|---|---|---|
| Swagger UI 5.17.14 bundles DOMPurify 3.1.4 (CVE-2025-26791 and 19 later CVEs, all XSS / mXSS in sanitiser bypasses) | Retire.js | Swagger UI upgraded to 5.33.0 (DOMPurify 3.4.13); the Swagger page is only reachable by signed-in users and renders the application's own OpenAPI document |
| Container runs as root, no health check | Trivy, Semgrep | Dockerfile: dedicated system user (uid 10001), `USER`, `HEALTHCHECK` |
| `SSLContext.getInstance("TLS")` | Semgrep | `TLSv1.3` (1.3 + 1.2), `TLSv1.2` on old runtimes |
| `java.util.Random` for report and job ids | Semgrep, FindSecBugs | `SecureRandom` (ids are workspace-scoped and not secrets, but now unpredictable as well) |
| No `Content-Security-Policy` | ZAP | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'self'; object-src 'none'; base-uri 'self'; form-action 'self'` on every response of the servlet and of the desktop server; the three inline `onclick` handlers and the Swagger bootstrap script were moved to script files so that `script-src 'self'` holds |
| HTTP 500 with exception text for a non-TWX upload and for a malformed suppression key | ZAP (Application Error Disclosure), FindSecBugs | 400 with a plain reason; unexpected exceptions answer a generic message, the stack trace stays in the server log; `FileSystemException` messages (they contain server paths) are never returned |
| Report id longer than the file-system limit produced a 500 whose message contained the data directory path | ZAP (Buffer Overflow heuristic) | ids are limited to 64 characters (400 otherwise); `SafePath` refuses any component above 200 characters |
| Regular expressions of rules outside `PatternRule` ran without the time budget (REDOS pattern in the loop detector) | FindSecBugs | every rule pattern over script text now runs over `TimedText` (2 s per script) |
| `toLowerCase()` / `toUpperCase()` without a locale (38 places) | FindSecBugs IMPROPER_UNICODE | `Locale.ROOT` everywhere (identifiers such as `ID` would not lower-case to `id` on a Turkish-locale JVM) |
| OpenAPI document declared arrays without `items` | ZAP OpenAPI import | fixed |

## Accepted or by design (with rationale)

| Finding | Tool | Rationale |
|---|---|---|
| `CSP: style-src 'unsafe-inline'` (Medium) | ZAP | The generated HTML sets widths and colours through `style` attributes (progress bars, severity bars, Chart.js canvases) and Bootstrap needs inline styles. Scripts are fully covered (`script-src 'self'`, no inline script, no `eval`); inline *styles* cannot execute code. Removing them would mean rewriting the UI's rendering for no security gain |
| Unencrypted SMTP socket | Semgrep, FindSecBugs | `smtpSecurity` is an operator setting: `ssl` (implicit TLS), `starttls`, or `none` for a relay on localhost. Documented in WEB-APPLICATION.md; the default configuration sends no mail |
| Server-side request forgery patterns (`URLCONNECTION_SSRF_FD`, 4) | FindSecBugs | Repository (Process Center / Studio) and webhook URLs are user-supplied by design. Mitigations: `http(s)` only, link-local and cloud-metadata addresses refused, `outboundHosts` allow-list, `outboundPrivate=false` refuses loopback / private / site-local addresses (default on demo servers); only signed-in users and, for webhooks, workspace managers can set them |
| `PATH_TRAVERSAL_IN` (23) | FindSecBugs | Pattern match on every `new File(String)`: command-line arguments of the CLI (`tca.Main`, by definition user paths), the data directory from the configuration, and the request-derived names which all go through `SafePath.child` (normalised, one component, must stay under its base) or `Facade.safeName`. CodeQL's taint analysis, which models these sanitisers, reports 0 |
| `HARD_CODE_PASSWORD` (1) | FindSecBugs | A local variable named `password` that receives the value of a request field (`Directory.saveConnectionIn`); no literal |
| `INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE` (3) | FindSecBugs | `printStackTrace` to the server log (2) and the message of an `IOException` returned to the caller (1): repository answers ("Process Center login failed", "unexpected export prepare response") must reach the user; file-system exceptions are excluded (see above) |
| `HTTP_RESPONSE_SPLITTING`, `COOKIE_USAGE`, `SERVLET_HEADER`, `SERVLET_QUERY_STRING` | FindSecBugs | The servlet adapter reads headers, the query string and cookies (that is its job) and sets headers whose values are server-generated tokens (hex / base64url) or constants; the servlet container rejects CR / LF in header values |
| `POTENTIAL_XML_INJECTION` (3) | FindSecBugs | String building of regular expressions from constant tag names in `Xml`, not XML documents |
| `REDOS` (2) | FindSecBugs | Static patterns with an optional group: the file-name pattern runs on file names, the loop pattern under the 2 s budget |
| `USO_UNSAFE_INHERITABLE_OBJECT_SYNCHRONIZATION` (4) | FindSecBugs | `Facade` synchronises on its own final fields; the class is final and embedded in-process |
| Timestamp disclosure (66, Low) | ZAP | Ten-digit numeric constants inside the minified Swagger UI bundle, not timestamps |
| User Agent Fuzzer, Authentication Request Identified, Session Management Response Identified | ZAP | Informational: the scanner recognised the login endpoint and the session cookie |

## Operator responsibilities (unchanged)

TLS termination and HTTPS-only access in front of the WAR, protection of the data directory, trusted-header authentication only behind a proxy that sets or strips the header, `outboundHosts` / `outboundPrivate` according to the network. See [SECURITY-REVIEW-WAR.md](SECURITY-REVIEW-WAR.md) and [WEB-APPLICATION.md](WEB-APPLICATION.md).

## Reproducing the scans

```
# SAST
semgrep scan --config p/java --config p/javascript --config p/security-audit --config p/owasp-top-ten --config p/secrets --exclude 'build/**' --exclude 'dist/**' --exclude 'src/war/webapp/swagger/**' --exclude 'static/vendor/**' .
spotbugs -textui -effort:max -low -xml:withMessages -output spotbugs.xml -auxclasspath lib/rhino-1.7.15.jar:lib/jakarta.servlet-api-6.0.0.jar:build/twx-code-analyzer.jar -onlyAnalyze 'tca.-' build/classes build/war/WEB-INF/classes   # with findsecbugs-plugin.jar in spotbugs/plugin
# SCA and secrets
trivy fs --scanners vuln,secret,misconfig --skip-dirs build,dist .
retire --path src/war/webapp --path static
gitleaks git --redact .
# DAST: deploy the jakarta WAR, sign in, then in ZAP add a Replacer rule for the Cookie header (tca_session=...) and one for Origin (the API's CSRF check),
# import api/openapi.json with the deployment's absolute URL as server, spider and run the full active scan with the context excluding
# /api/auth/logout, /api/auth/account, /api/auth/purge, /api/auth/users, /api/auth/password, /api/auth/tokens (they would end the scan's own session or account).
```

CodeQL runs automatically on GitHub (Security > Code scanning) for every push.

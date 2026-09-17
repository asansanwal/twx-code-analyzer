# Security review - web application (WAR) 1.3

**Addendum 1.3.1 (2026-09-17)** - GitHub code scanning (CodeQL, default suite) on the repository reported 43 alerts on 1.3; every one was fixed in code rather than dismissed. Details in the rows marked *1.3.1* below and in the change log. The same day the tree and a running deployment were scanned with Semgrep, SpotBugs + FindSecBugs, Trivy, Retire.js, gitleaks and OWASP ZAP: [SECURITY-SCAN-REPORT-1.3.1.md](SECURITY-SCAN-REPORT-1.3.1.md) lists every finding with its fix or its rationale.

Scope: the enterprise layer of the WAR (`tca.enterprise`, `AnalyzerServlet`, `enterprise.js`, bundled Swagger UI) and the shared API it builds on. Method: code review of every endpoint against the threats below, then automated checks in the functional suites (marked *tested*). Date: 2026-09-07.

## Threats and mitigations

| Threat | Finding during the review | Mitigation in 1.3 | Status |
|---|---|---|---|
| Password storage | Built-in accounts needed hashing | PBKDF2-HMAC-SHA256, 120 000 iterations, 16-byte random salt, constant-time comparison | done |
| Credential stuffing / brute force | No lockout in the first draft | Ten failed logins lock the account for 15 minutes; the same error message for unknown accounts and wrong passwords | done, tested |
| Session theft | | Random 192-bit session tokens, `HttpOnly`, `SameSite=Lax`, `Secure` behind HTTPS (`X-Forwarded-Proto`), 30-day lifetime, ended on logout, disable and account deletion | done, tested |
| Cross-site request forgery | Cookie-authenticated state changes could be triggered cross-site on browsers without SameSite | `Origin` header must match the request host for every non-GET API call (bearer clients send no Origin) | done, tested |
| Token leakage | | Personal access tokens stored as SHA-256 hashes, shown once, optional expiry, revocable, revoked with the account; token identity never falls back to anonymous (401) | done, tested |
| Authorisation gaps | Every mutating endpoint was checked for its guard | Admin-only: policies, teams, shared definitions, accounts, audit. Workspace manager: workspace configuration, suppressions, saved definitions. Team scope validated against membership. Foreign report / job ids answer 404. Anonymous demo visitors cannot change settings | done, tested |
| Server-side request forgery | Webhook URLs, Process Center and Studio URLs are user-supplied and are called by the server | Only `http(s)` with a host; link-local and cloud metadata addresses refused; `outboundHosts` allow-list; `outboundPrivate=false` refuses loopback, private and site-local addresses (default on demo servers); validation at save time and before every call | done, tested |
| Impersonation through trusted headers | `userHeader` accepts any value | Documented: enable only behind a proxy that sets or strips the header; off by default | documented |
| Denial of service by large bodies | Uploads were read fully into memory | `maxUploadMb` (default 512) checked on `Content-Length` before reading and on the file after reading; UI checks before uploading; queue limit (`maxQueue`, 429); per-workspace quota; retention of anonymous workspaces | done, tested |
| Path traversal | Ids are used in file paths | Workspace keys, team / policy / connection ids and report ids are sanitised or validated with strict patterns before touching the file system. *1.3.1*: additionally every such name resolves through `tca.util.SafePath.child` (one path component, normalised, must stay directly under its base directory) - a containment check rather than a pattern, in `Store`, `Api`, `EnterpriseApi` and `Directory` (31 CodeQL `java/path-injection` alerts) | done, tested |
| Machine-in-the-middle on repository connections (*1.3.1*) | On an SSL handshake failure the Process Center, Studio and export clients retried the request trusting any certificate and any host name; a network attacker on the path to a repository could have captured credentials and served a tampered export (CodeQL `java/insecure-trustmanager`) | The trust-all path is removed. The JVM trust store decides, or a definition / request carries the server certificate (PEM) and exactly that certificate is trusted (standard trust manager over a one-entry key store, host name verifier accepts only the pinned certificate). A failed handshake answers 502 with guidance | done, tested |
| Regular expression injection (*1.3.1*) | The search API compiled a user-typed regular expression as given (CodeQL `java/regex-injection`) | `Searcher.sanitizeRegex` rebuilds the expression token by token: literal text through `Pattern.quote`, character classes over an allow-list, `. * + ? {n,m} | ^ $`, `\w \s \d \b \t \n`, plain / non-capturing groups and `(?i)` from a fixed vocabulary - the compiled string contains nothing else. Limits: 200 characters, repeat counts up to 100, no quantifier on a group, no back references, look-arounds or named groups, balanced (400 with the reason otherwise); the whole search runs under a 5 s budget (`TimedText`) | done, tested |
| Regular expression denial of service on uploads (*1.3.1*) | Package and BPD parsing and several rule patterns backtracked polynomially on crafted text (CodeQL `java/polynomial-redos`; 180 KB of `merge merge ...` held a worker for minutes on TCA-JS-001) | package.xml and BPD flows are parsed with linear scans (`Xml.openTag` / `Xml.elements`); every rule pattern runs over `TimedText` with a 2 s budget per script - a script that exhausts it gets an INFO "Rule skipped" finding and the analysis continues; TCA-JS-013 tightened | done, tested |
| Open redirect (*1.3.1*) | The trailing-slash redirect of the root echoed the request URI (CodeQL `java/unvalidated-url-redirection`) | The target is built from the context and servlet paths | done |
| Cross-site scripting | The UI builds HTML from server data | Every user-controlled value passes through the escaper; messages set through `textContent` | reviewed |
| Clickjacking / content sniffing / script injection | | `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `Cache-Control: no-store` on API answers; *1.3.1*: `Content-Security-Policy` (same origin only, no inline scripts, no plug-ins, `frame-ancestors 'self'`) on every response of the WAR and of the desktop server | done, tested |
| Information disclosure | | Passwords, API keys and tokens never returned by the API (`hasPassword` flags instead); user list, audit and metrics of workspaces admin-only; health and Prometheus metrics carry no user data | done, tested |
| Secrets at rest | Repository passwords, API keys and SMTP credentials must be usable, so they are stored in clear | Owner-only file permissions on every enterprise file; documented reliance on file-system protection and platform encryption at rest | documented |
| Account enumeration | Sign-up reveals whether an e-mail address exists | Accepted (usability); login does not reveal it | accepted |
| Audit | | Analyses, deletions, settings and policy changes, accepted findings, sign-ups, logins, account and team administration, repository imports; file plus JSON log lines | done, tested |

## Not covered by the application (operator responsibilities)

* TLS termination and HTTPS-only access (the demo uses nginx with a Let's Encrypt certificate).
* Protection of the data directory (permissions, backup, encryption at rest) and of the SMTP / repository credentials in the configuration.
* Network reachability from the server to repositories and webhook targets (`outboundHosts`, `outboundPrivate`).
* E-mail verification of sign-ups and password complexity rules beyond the eight-character minimum; a corporate deployment should prefer container security or single sign-on (`auth=container`).
* Rate limiting of sign-ups and of the public endpoints at the reverse proxy.

## Dependencies

Runtime: Mozilla Rhino (shaded, MPL 2.0). Browser: Bootstrap, Font Awesome Free, Chart.js, Swagger UI 5.33.0 (Apache 2.0), all bundled, no external requests. Compile-time only: the servlet API jars.

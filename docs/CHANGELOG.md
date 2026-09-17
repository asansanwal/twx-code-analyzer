# Change log

## 1.3.1 (2026-09-17) - security fixes

Fixes for the 43 alerts GitHub code scanning (CodeQL) reported on 1.3; all deliveries rebuilt, behaviour otherwise unchanged (findings identical on the reference exports). Details: [SECURITY-REVIEW-WAR.md](SECURITY-REVIEW-WAR.md).

* **Repository TLS**: the "retry trusting any certificate" fallback of the Process Center, Studio and export clients is gone (it allowed a machine-in-the-middle). The JVM trust store decides, or a definition / request carries the server certificate (`certificate`, PEM) which is then the only certificate trusted for that connection. New "Server certificate" field on the Process Center page and in Administration > Connections; `certificate` in the connection, `pc/apps`, `pc/import`, `pc/evaluate` bodies and in the facade `export` operation. A failed handshake answers 502 with guidance.
* **Search**: a regular expression is rebuilt from a supported grammar before it is compiled (length limit, no repeated groups, no back references, look-arounds or named groups; 400 with the reason) and the search runs under a time budget.
* **Analysis**: package.xml and BPD flows are parsed with linear scans; every pattern rule runs with a time budget per script, a script that exhausts it gets an INFO "Rule skipped" finding instead of stalling the analysis. TCA-JS-013 no longer backtracks.
* **Files**: report ids, workspace tokens, account and team keys and policy ids resolve through one containment check (`SafePath`) in addition to the existing pattern validation. Bad ids answer 400.
* **Servlet**: the root redirect is built from the deployment paths, not from the request URI.

## 1.3 (2026-09-07) - web application (WAR) enterprise edition

Only the WAR delivery changed in behaviour; the desktop app, the command line and the embedding facade are unchanged (the shared code gained extension points that stay dormant there). Details: [WEB-APPLICATION.md](WEB-APPLICATION.md), security notes: [SECURITY-REVIEW-WAR.md](SECURITY-REVIEW-WAR.md), test results: [TEST-REPORT-1.3.md](TEST-REPORT-1.3.md).

* Identity: container security or trusted headers, built-in accounts (sign-up, login, sessions, password change, personal access tokens), administrators from a properties file, anonymous demo workspaces.
* Workspaces and teams: personal workspaces kept across sessions with purge and account deletion, team workspaces with members and team admins, workspace switcher.
* Central rule policies with versions and quality gates; verdict on every report; accepted findings (baseline) with reason and author, re-evaluation of stored reports.
* Analysis queue with workers, progress and jobs API; storage quota, upload size limit, queue limit, optional discarding of uploads, retention of anonymous workspaces.
* Process Center and Business Automation Studio integration: listing of applications, tracks and snapshots, import by id, name or track tip, one-call evaluation for pipelines, definitions saved per workspace or shared by administrators.
* Notifications (webhook, e-mail), dashboard with health trends, remediation view in fix order with designer links, executive one-page PDF, audit trail, health and Prometheus metrics.
* OpenAPI description with bundled Swagger UI, complete in-application help, demo mode setting, security hardening (CSRF origin check, outbound URL restrictions, security headers, owner-only file permissions).
* Shared UI: the artifact tree shows finding counts as separate, severity-coloured badges (also in the desktop app).

## 1.2 (2026-09-07)

* Rule settings: every rule can be disabled or given another severity and impact (1..3); the severity weights and the thresholds are editable. Settings are stored by the server in the data directory (`settings.json`) and apply to every analysis; the desktop app no longer keeps thresholds in the browser. Settings page with filter and bulk actions, Export (JSON file), Import (full or partial file, validated with warnings) and Reset to defaults. Reports record the settings they were produced with; the Rules page shows the effective values. Command line: `analyze --settings file.json --toolkits`, `settings template.json`. Facade: `settings`, `saveSettings`, `resetSettings`, `analyze {settings}`. Engine: `tca.rules.RuleSettings`, `Report.settings`, `GET/PUT/DELETE /api/settings`, `GET /api/info`.
* Deployable web application (added delivery, the desktop package and the embedding are unchanged): `twx-code-analyzer.war` (Servlet 4, `javax.servlet`) and `twx-code-analyzer-jakarta.war` (Servlet 6, `jakarta.servlet`), built by `build.sh`, tested on Tomcat 9 and 10.1; the UI uses relative URLs and runs at any context root. `tca.web.Api` is the JSON API shared by the desktop server and the servlet. Dockerfile for the desktop jar. `serve` options `--host`, `--workspaces`, `--retention-days`, `--read-only-settings`.
* Workspaces for servers: each browser gets a private workspace (cookie `tca_ws`); its analyses and settings are invisible to other browsers, foreign report ids answer 404. Permanent deletion of an analysis (uploaded file, report, metadata) from History, the home page and a new Delete button on the report. Optional retention purges old analyses hourly. Read-only settings mode for demo servers.
* Live demo at https://twxca.com (Tomcat 10.1 behind nginx, workspaces, 30-day retention, read-only settings).
* Fixes: the delete buttons on the home page were inert; stored reports are re-read from disk for PDF exports and comparisons instead of being re-analysed with default settings.

## 1.1 (2026-09-07)

* Syntax-tree rules on the Rhino AST (TCA-JS-040 to TCA-JS-049, TCA-UI-020), rule confidence (needs review filter), line / column / snippet on findings.
* Toolkit Usage with evidence tiers (used, possible, none), duplicate snapshots merged, toolkit-to-toolkit references, HTML / PDF / CSV exports.
* Report coverage, diagnostics and product version; richer artifact detail (variables, steps, nested schema, asset content, raw XML); services grouped by kind; CSV / HTML / PDF exports of the findings; dependency-free PDF writer; Settings page with thresholds.
* Parser fixes: BPD private variables, embedded subprocesses, web assets by asset uuid.

## 1.0 (2026-09-04)

* First release: TWX loader with bundled toolkits, 140 rules (legacy analyzer rules re-implemented statically, IBM checkstyle and best practices, JavaScript pattern rules), ranked findings with artifact path and remediation, history and snapshot comparison, service and process diagrams, TWX search, local web UI and command line, JSON facade for BAW embedding, Linux and Windows desktop packages.

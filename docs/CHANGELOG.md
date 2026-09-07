# Change log

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

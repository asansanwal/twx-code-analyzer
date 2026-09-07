# Web application (WAR) - enterprise edition

This document covers the deployable web application only. The desktop app (`twx-code-analyzer.jar` + `run.sh` / `run.bat`), the command line and the embedding facade are unchanged by everything described here; their documentation is the README, [DEPLOYMENT.md](DEPLOYMENT.md) (container deployment of the WAR itself) and [EMBEDDING.md](EMBEDDING.md). The in-application help (`help/index.html`, the Help entry of the navigation bar) is the user guide of the same features; this file is the operator's and integrator's reference.

## What the WAR adds (version 1.3)

| Area | Feature |
|---|---|
| Identity | Container security (remote user, roles), trusted headers set by a reverse proxy or single sign-on, built-in accounts with sign-up, login, sessions, password change, personal access tokens; anonymous demo workspaces |
| Workspaces | Personal workspace per user (kept until the user deletes it), team workspaces with members and team admins, workspace switcher, `X-TCA-Workspace` header for API clients |
| Governance | Central rule policies (rules, severities, impacts, weights, thresholds, quality gate) with versions, selected per workspace; every report records the policy and version |
| Quality gates | Limits on health, findings by severity, weighted score, per-rule counts; verdict passed / failed / no-gate on every report; HTTP 422 endpoints for pipelines |
| Baseline | Accepted findings with reason, author and date; excluded from score, health, verdict, dashboard and executive summary; applied to every report of the workspace; reopen at any time |
| Analysis queue | Worker pool, queue position, progress (rule n of m), jobs API, queue limit |
| Repositories | Process Center (basic authentication, console export) and Business Automation Studio (Zen token, API key, bearer, basic; context root); listing of applications, tracks and snapshots (tips marked); import by snapshot id, by application + snapshot name or by track tip; definitions saved per workspace (with credentials) or shared by administrators |
| Pipelines | One-call evaluation of a repository snapshot (`pc/evaluate`), synchronous upload with verdict (`analyze?gate=1`), asynchronous upload + jobs + verdict, personal access tokens, OpenAPI document and bundled Swagger UI |
| Notifications | Webhook (JSON POST) and e-mail (built-in SMTP client: plain, SSL, STARTTLS, AUTH LOGIN) on completed analyses and failed gates |
| Insight | Dashboard (health trend per application, worst applications, most frequent rules), remediation view in fix order with designer links, executive one-page PDF |
| Operations | Audit trail (file + JSON log lines), `api/health`, Prometheus `api/metrics`, storage quota per workspace, upload size limit, optional discarding of uploads after analysis, retention of anonymous workspaces |
| Demo mode | One setting that reproduces the public demo: anonymous temporary workspaces, read-only rule settings for anonymous visitors, sign-up and login available, private-network outbound calls refused |
| Help | Complete in-application user guide with contextual links |

## Architecture

```
WAR
├── AnalyzerServlet (javax or jakarta)  - config, security headers, static content
├── tca.enterprise.EnterpriseApi        - extends tca.web.Api (the shared JSON API): identity, workspaces, policies, gates, suppressions, jobs, repositories, notifications, dashboard, audit, metrics
│   ├── Auth                            - built-in accounts, sessions, tokens, admins.properties
│   ├── Directory                       - teams.json, policies/, connections.json, audit.log, per-workspace workspace.json / suppressions.json / connections.json
│   ├── Jobs                            - worker pool, one Analyzer per worker
│   ├── ProcessCenterClient / Studio    - repository listing and export
│   ├── Notifier                        - webhook + SMTP
│   ├── ExecutivePdf, OpenApi
│   └── Captured                        - identity kept for background work
├── enterprise.js                       - pages and hooks registered on window.TCA (loaded only when /api/info says enterprise)
├── help/index.html                     - user guide
└── swagger/                            - Swagger UI (Apache 2.0) rendering /api/openapi.json
```

The shared code (`tca.web.Api`, `static/app.js`) only gained extension points: identity on the `Http` abstraction, a progress listener on the analyzer, overridable hooks in the API (`store`, `settingsFor`, `canChangeSettings`, `beforeAnalyze`, `decorate`, `afterAnalyze`, `afterDelete`, `settingsChanged`, `info`) and a route / tab / hook registry in the browser. With `enterprise=false` the WAR behaves exactly like version 1.2.

## Data layout

```
<dataDir>/
  enterprise/
    admins.properties        administrators (admins=a@x.com, b@y.com   or   a@x.com=admin)
    users.json               built-in accounts (PBKDF2-HMAC-SHA256 hashes, 120 000 iterations, 16-byte salt)
    sessions.json            sessions (random 192-bit tokens, 30 days)
    tokens.json              personal access tokens (SHA-256 hashes, expiry, last use)
    teams.json               teams (members, admins, directory group)
    policies/<id>.json       rule policies (settings, gates, version, author)
    connections.json         shared repository definitions (URL, user, password / API key / token, designer link template)
    audit.log                one JSON line per audited action
  users/<user>/              personal workspaces: <id>/upload.twx + report.json + meta.json, settings.json, workspace.json, suppressions.json, connections.json
  teams/<team>/              team workspaces (same layout)
  ws/<token>/                anonymous workspaces (cookie tca_ws), purged by retentionDays
```

Files under `enterprise/` and every JSON written by the application get owner-only permissions where the file system supports it. Passwords of repository definitions are stored in clear (they are needed to log in to the repository): protect the data directory with file-system permissions and platform encryption at rest.

## Configuration

Every option is a servlet context parameter (`web.xml`), a JVM property (`-Dtca.<name>`) or an environment variable (`TCA_<NAME>`); the Administration > Server page shows the values in force.

| Option | Default | Meaning |
|---|---|---|
| `dataDir` | `<java.io.tmpdir>/twx-code-analyzer` | Data directory (see above) |
| `enterprise` | `true` | `false` = plain 1.2 web application |
| `demo` | `false` | Demo server: implies `anonymous=true`, read-only settings for anonymous visitors, `outboundPrivate=false`, no teams for non-administrators |
| `auth` | `builtin` | `builtin` (sign-up / login), `container` (remote user or trusted header), `none` |
| `anonymous` | `= demo` | Allow anonymous cookie workspaces; `false` = sign-in required (401) |
| `signup` | `true` | Self-service registration (builtin) |
| `userHeader`, `groupsHeader` | | Trusted identity headers (for example `X-Forwarded-User`, `X-Forwarded-Groups`). Enable only behind a proxy that sets or strips them |
| `adminRole` | `tca-admin` | Container role of administrators |
| `adminGroup`, `admins`, `adminsFile` | | Directory group, comma list, properties file (default `<dataDir>/enterprise/admins.properties`, re-read when it changes) |
| `personalSettings` | `true` | Users may edit rule settings in their personal workspace (`false` = policies only) |
| `processCenter`, `pcInlineCredentials` | `true` | Repository integration; whether users may enter their own URL and credentials |
| `outboundHosts` | any | Comma-separated host suffixes the server may call (repositories, webhooks) |
| `outboundPrivate` | `true` (`false` in demo) | Whether loopback, private and site-local addresses may be called; link-local / metadata addresses are always refused |
| `notifications` | `true` | Notifications feature |
| `smtpHost`, `smtpPort`, `smtpUser`, `smtpPassword`, `smtpFrom`, `smtpSecurity` | | SMTP server (`none`, `ssl`, `starttls`) |
| `workers` | `2` | Analyses run at the same time (about 1 GB of heap each for a 40 MB export) |
| `maxQueue` | `20` | Queued analyses before new ones are refused (429) |
| `quotaMb` | `0` | Storage quota per workspace (0 = none) |
| `maxUploadMb` | `512` | Largest accepted upload (413; the UI checks before uploading) |
| `keepUploads` | `true` | `false` = delete the uploaded file after the analysis (report only; no diagrams / search for that report) |
| `retentionDays` | `0` | Purge anonymous workspaces older than N days; user and team workspaces are never purged |
| `designerUrl` | | Default designer link template (`{branchId}`, `{snapshotId}`, `{projectId}`, `{objectId}`) |
| `baseUrl` | | Public URL used for links in notifications |
| `audit` | `true` | Audit trail |
| `title` | `TWX Code Analyzer` | Name in the navigation bar and the API documentation |
| `settingsReadOnly` | `false` | Read-only rule settings for everybody (1.2 option, still honoured) |

## Identity and authorisation

Resolution order per request: container remote user, trusted header, bearer token (`Authorization: Bearer tca_...`), built-in session cookie, anonymous (only when allowed). Administrators: container role, `admins` list, the properties file, or the `adminGroup` in the groups header.

| Action | Who |
|---|---|
| Analyze, browse, export, compare, delete analyses of the workspace | every member of the workspace (anonymous: own browser) |
| Select the policy, define gates and notifications, accept / reopen findings, save repository definitions | workspace manager: owner of a personal workspace, team admin, administrator; anonymous visitors on a demo server may accept findings but not change settings |
| Edit rule settings | workspace manager when the policy is `custom` and `personalSettings` allows it |
| Policies, teams, shared repository definitions, accounts, audit trail, server page | administrators |

Foreign report ids answer 404, jobs are visible per workspace only, team scope is validated against membership.

## API

`api/openapi.json` (OpenAPI 3.0.3) describes every endpoint; the bundled Swagger UI at `swagger/index.html` renders it and can call the endpoints with the browser session. Authentication for clients: personal access token as `Authorization: Bearer <token>` (Account page, `POST api/auth/tokens`), the session cookie, or the container's mechanism. Team workspace for API clients: header `X-TCA-Workspace: team:<id>`.

Pipeline recipes:

```
# 1. one call: export a Process Center / Studio snapshot, analyze, verdict (HTTP 422 = quality gate failed)
curl -sf --max-time 1800 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"url":"https://pc:9443","user":"ci","password":"...","app":"MYAPP","snapshot":"1.4.0"}' "$BASE/api/enterprise/pc/evaluate"
# stored definition: {"connection":"prod-pc","app":"MYAPP","snapshot":"1.4.0"};  track tip: {"connection":"prod-pc","app":"MYAPP","track":"Main"}
# Studio: {"type":"studio","url":"https://cpd.example.com","contextRoot":"/bas","auth":"zen-apikey","user":"ci","apiKey":"...","app":"MYAPP","snapshot":"1.4.0"}

# 2. one call with an export file
curl -sf -H "Authorization: Bearer $TOKEN" -F file=@MyApp.twx "$BASE/api/analyze?gate=1"

# 3. asynchronous: queue, poll, verdict
JOB=$(curl -sf -H "Authorization: Bearer $TOKEN" -F file=@MyApp.twx "$BASE/api/analyze?async=1" | jq -r .id)
... poll "$BASE/api/jobs/$JOB" until status = done ...
curl -sf -H "Authorization: Bearer $TOKEN" "$BASE/api/report/$REPORT/verdict"
```

The verdict document: `verdict` (passed | failed | no-gate), `checks[]` (name, label, limit, actual, passed), `policy`, `summary`, `app`, `source`, `report`. Monitoring: `api/health` (JSON liveness), `api/metrics` (Prometheus text format: analyses, failures, analysis time, uploads, requests, queue, stored reports, storage bytes, workspaces, uptime).

## Repositories

* **Process Center**: URL of the Process Center, user name and password (basic authentication for the listing, form login and the console's export servlet for the export, self-signed certificates accepted after a failed handshake). Tested against the lab Process Center.
* **Business Automation Studio**: platform (Zen) URL, context root of Workflow Authoring (default `/bas`), authentication `zen` (user + password), `zen-apikey` (user + API key), `bearer` (token) or `basic`. The platform token comes from `/icp4d-api/v1/authorize` (fallback `/v1/preauth/validateAuth`); listing and export use the same repository API and servlet as the Process Center with the `Authorization` header. Validated against a simulator that mirrors the documented platform behaviour; no live Cloud Pak was available for this release.
* Definitions can be saved per workspace (URL, user, password / API key / token; deletable by the user) or shared by administrators; values entered on the page or in a request override a definition for that request.
* The listing returns applications with `tracks[]` (id, name, isDefault, snapshots) and a flat `snapshots[]`; snapshot `tip` marks the current working version of a track (exportable).

## Security review summary

Findings of the review made for this release and their mitigations are in [SECURITY-REVIEW-WAR.md](SECURITY-REVIEW-WAR.md). In short: salted PBKDF2 passwords, HttpOnly / SameSite / Secure cookies, login lockout, hashed tokens with expiry, origin check against cross-site requests, authorisation on every mutating endpoint, 404 for foreign resources, outbound URL restrictions (scheme, link-local and metadata addresses, optional host allow-list, private ranges on public servers), upload size and queue limits, quota, security headers, owner-only file permissions, audit trail. Residual points that stay with the operator: HTTPS termination, protection of the data directory, trusted headers only behind a proxy, no e-mail verification of sign-ups, SMTP credentials in the configuration.

## Testing

Two automated suites run against Tomcat 10.1 (Jakarta WAR, enterprise mode) and Tomcat 9 (javax WAR, demo mode): 155 API and browser checks (accounts, tokens, header identity, policies, teams, queued analyses, verdicts, suppressions, notifications through a webhook receiver and an SMTP sink, quota, audit, users administration, purge and account deletion, Process Center listing / import / evaluation against the lab, Studio through the simulator, security checks, every page of the browser UI including the administration and Swagger) and 27 demo-mode checks. The scripts and the results of the last run are recorded in [TEST-REPORT-1.3.md](TEST-REPORT-1.3.md).

## Public demo (twxca.com)

Runs the Jakarta WAR on Tomcat 10.1 behind nginx with `demo=true`, `auth=builtin`, `signup=true`, `anonymous=true`, `quotaMb=1024`, `retentionDays=30`, `workers=2`, `baseUrl=https://twxca.com/`, no SMTP. Administrators are listed in `/var/lib/twxca/enterprise/admins.properties`.

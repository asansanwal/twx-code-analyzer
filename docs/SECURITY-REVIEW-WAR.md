# Security review - web application (WAR) 1.3

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
| Path traversal | Ids are used in file paths | Workspace keys, team / policy / connection ids and report ids are sanitised or validated with strict patterns before touching the file system | done |
| Cross-site scripting | The UI builds HTML from server data | Every user-controlled value passes through the escaper; messages set through `textContent` | reviewed |
| Clickjacking / content sniffing | | `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `Cache-Control: no-store` on API answers | done |
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

Runtime: Mozilla Rhino (shaded, MPL 2.0). Browser: Bootstrap, Font Awesome Free, Chart.js, Swagger UI 5.17.14 (Apache 2.0), all bundled, no external requests. Compile-time only: the servlet API jars.

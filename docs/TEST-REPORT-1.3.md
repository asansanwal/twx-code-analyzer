# Functional test report - web application 1.3 (2026-09-07)

Two automated suites (Python: urllib for the API, Playwright for the browser) ran against the final build.

| Suite | Target | Result |
|---|---|---|
| Enterprise mode | `twx-code-analyzer-jakarta.war` on Tomcat 10.1, context root `/twx-code-analyzer`; options `enterprise=true auth=builtin anonymous=true workers=2 quotaMb=60 userHeader=X-Test-User retentionDays=30 designerUrl=...`, SMTP to a local sink, webhook to a local receiver; Process Center = lab 192.168.1.242; Studio = simulator | 155 / 155 passed |
| Demo mode | `twx-code-analyzer.war` (javax) on Tomcat 9; options `demo=true retentionDays=30 quotaMb=1024 maxUploadMb=20 workers=2` | 27 / 27 passed |

Failures: none

## Enterprise mode checks

* info anonymous enterprise
* health
* metrics prometheus
* openapi document
* static swagger/index.html
* static help/index.html
* static enterprise.js
* static swagger/swagger-ui-bundle.js
* signup admin
* admin recognised from properties file
* signup alice
* signup bob
* duplicate signup refused
* short password refused
* wrong password refused
* login alice new browser
* session persists across browsers (same personal workspace)
* token created
* bearer token identity
* bad token 401
* token list without secret
* trusted header identity
* non-admin cannot create policy
* admin creates policy
* policy update increments version
* second policy
* policies listed for users
* policy detail with document
* admin creates team
* non-admin cannot create team
* bob sees his team
* bob switches to team workspace (not manager)
* alice is team admin
* non-member falls back to personal
* async analyze accepted
* jobs listed
* job done
* report has policy custom, no gate, key on findings
* verdict no-gate 200
* summary endpoint
* bob cannot read alice report
* anonymous cannot read alice report
* alice selects strict policy
* verdict failed 422 after restamp
* workspace shows policy
* effective rules follow the policy
* settings not editable while a policy is selected
* new analysis stamped with policy version and weights
* critical weight 150 applied
* lenient policy passes
* workspace gate with custom policy
* team member without admin cannot accept
* alice accepts a finding
* finding marked accepted and summary recomputed
* history reflects accepted count
* remediation groups exclude accepted, designer link
* reopen finding
* reopened: summary back
* suppressions empty
* team analysis by alice
* bob sees the team report
* team admin accepts in team workspace
* bob sees the team acceptance
* personal workspace unaffected by team acceptance
* sync upload with gate=1 answers the verdict (422 with the custom gate)
* executive pdf
* findings pdf
* compare
* dashboard totals and trend
* notify config saved
* webhook received
* mail sent through smtp
* quota exceeded 413
* workspace storage reported
* csrf: foreign origin refused
* csrf: same origin accepted
* ssrf: metadata address refused for webhooks
* ssrf: non-http scheme refused
* ssrf: metadata address refused for Process Center
* security headers present
* login: unknown account same error as wrong password
* login lockout after 10 failures
* audit admin only
* audit trail entries
* admin lists users
* non-admin cannot list users
* disabled user session ends
* disabled user cannot log in
* re-enabled with reset password
* password change
* purge deletes analyses
* purge removed files on disk
* account deletion needs password
* account deleted
* account workspace removed
* deleted account cannot log in
* tokens of deleted account revoked
* admin deletes account
* team deleted with workspace
* studio: zen user/password lists apps with tracks (toolkits excluded from import)
* studio: zen api key
* studio: bearer token
* studio: wrong password refused with a clear error
* studio: bad token refused
* studio: workspace definition saved
* studio: saved definition lists apps
* studio: evaluate the tip of a track through the export servlet
* studio: designer link from the definition
* studio: queued import by app name + snapshot
* studio: import of an application without snapshots 404
* pc connection stored
* pc apps listed with tracks
* pc apps with inline credentials
* pc wrong credentials rejected
* pc import queued
* pc import analysed
* pc report source recorded
* pc designer link from connection
* workspace pc definition saved with password
* definitions list: workspace first, then shared
* workspace definition lists apps with the stored password
* anonymous sees shared definitions only (no passwords)
* workspace definition deleted
* pc evaluate by url + app + snapshot name (one call verdict)
* pc evaluate unknown snapshot 404
* pc evaluate tip of the default track
* ui nav enterprise (dashboard, help, API link)
* ui signup and dashboard
* ui queued analysis reaches report
* ui report header has gate badge, policy, executive pdf
* ui tree severity badges
* ui remediation tab
* ui accept finding from detail
* ui overview accepted tile
* ui settings policy select and read-only editor
* ui gate failed after policy change
* ui dashboard rows and sparkline
* ui token creation
* ui help page
* ui process center page
* ui admin page refused for user
* ui logout
* ui admin login shows Administration
* ui admin policies
* ui admin teams
* ui admin users
* ui admin connections
* ui admin audit
* ui admin server
* ui policy editor loads rules
* ui team created
* ui users list
* ui swagger renders operations
* ui no javascript errors
* ui no failed requests

## Demo mode checks

* demo: anonymous info
* demo: anonymous cannot change settings
* demo: anonymous cannot configure workspace
* demo: anonymous sees built-in policy only
* demo: anonymous queued analysis
* demo: anonymous analysis done
* demo: anonymous report uses built-in defaults
* demo: anonymous may accept findings in own workspace
* demo: anonymous dashboard
* demo: audit hidden
* demo: upload limit refused early (connection closed before the body was read)
* demo: signup works
* demo: signed-in user has full personal workspace
* demo: signed-in user edits rules
* demo: signed-in user sets a gate
* demo: signed-in non-admin cannot create policies
* demo: outbound to loopback / private addresses refused
* demo: private webhook refused
* demo: anonymous still anonymous
* demo: admin from properties file
* demo: admin creates policy
* demo: user sees the policy
* demo ui: dashboard shows demo notice with sign-up link
* demo ui: anonymous settings read-only
* demo ui: privacy line mentions workspace and retention
* demo ui: signed-in user can edit settings
* demo ui: no javascript errors

Test inputs: a 16 MB process application export of the workspace for uploads; the lab Process Center for listing, import and one-call evaluation (including the tip of a track); a Studio simulator (Zen authorize, listing behind `/bas`, export servlet with bearer token) for the Studio path. Scripts: `ent_test.py`, `demo_test.py`, `studio_sim.py`, `hook_server.py`, `smtp_sink.py` (session scratchpad; not part of the repository).

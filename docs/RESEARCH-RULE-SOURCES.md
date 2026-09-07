# TWX Code Analysis - rule sources (research log)

Collected 2026-09-04. Public sources used to augment the rules taken from the legacy database-backed code analyzer.

## IBM Deployment Accelerator for BAW (IDA) - built-in checkstyle rules
Source: https://sdc-china.github.io/IDA-doc/checkstyle/checkstyle-checkstyle-rules-description.html (IBM, open documentation). 80+ rules with severities; the ones
applicable to a static TWX analysis (no Case/CP4BA solution package parts) are mapped to TWX Code Analysis rule ids in `analyzer/rules/*.py` (column "TCA").

Thresholds (defaults): toolkits 25, advanced integration services 25, AJAX services 25, decision services 25, general system services 50, integration services 25,
human services 50, service flows 50, web services 25, BPDs 15, reports 15, UCAs 25, SLAs 15, business objects 50, environment variables 25, exposed process
variables 25, coaches per human service 25 (IDA also flags > 5 coaches per human service), nested CSHS 25, input/output variables 15, nested toolkit levels 4,
toolkit size 50 MB, JavaScript lines 100 (medium) / 300 (high), searchable fields 25, BO attributes 50, coach controls per coach 500.

| IDA rule | Severity | Check |
|---|---|---|
| check-app-too-many-toolkit | MAJOR | toolkit count above threshold |
| check-app-with-mismatch-version-toolkits | CRITICAL | same toolkit referenced in different versions |
| check-app-with-too-many-* (AIS, ajax, bpd, BO, decision, env var set, EPV, general system, human service, integration, report, service flow, SLA, UCA, web service) | MINOR | artifact counts above thresholds |
| check-toolkit-nested-level | MAJOR | toolkit dependency depth > 4 |
| check-app-toolkit-size | MAJOR | toolkit > 50 MB |
| check-businessobject-documentation / -properties-documentation | MINOR | BO / property without documentation |
| check-shared-business-object | MAJOR | shared BO (performance) |
| check-too-big-businessobject-used | MAJOR | BO with > 50 attributes |
| check-coachcontrol-with-no-binding-value | MINOR | coach control without binding |
| check-coachview-reach-endpoint | MAJOR | coach not wired to an end node |
| check-ad-hoc-start-event, check-bpd-milestones-deprecated, check-bpds-deprecated, check-bpd-simulation-configurations-deprecated, check-bpd-icon-settings-deprecated, check-heritage-coaches-deprecated, check-heritage-services-deprecated, check-historical-analysis-scenarios-deprecated, check-key-performance-indicators-deprecated, check-external-implementations-deprecated, check-ibm-case-manager-integration-services-deprecated, check-deprecated-task-routing-policy, check-coachview-using-deprecated-coach-views, check-service-using-deprecated-coach-views, check-inline-web-service-configuration | MINOR | deprecated artifacts / settings (migration) |
| check-bpd-component-contains-inner-table-in-data-mapping / -in-script, check-service-item-contains-inner-table-* | CRITICAL | access to internal BPM tables (LSW_*) in scripts / mappings |
| check-bpd-javascript-live-connect, check-service-javascript-live-connect | MINOR | LiveConnect (Packages.*, java.*) usage |
| check-auto-tracking-enable | MAJOR | auto tracking enabled |
| check-bpd-activity-contain-tostring-datamapping, check-service-item-contain-tostring-datamapping | MAJOR | toString()/XML serialisation in data mappings |
| check-bpd-activity-narrative / -subject | MINOR | activity without narrative / subject |
| check-bpd-component-javascript-contains-sleep, check-service-item-contains-sleep | MAJOR | java.lang.Thread.sleep in scripts |
| check-bpd-component-need-exception-handle | CRITICAL | activities without error handling |
| check-bpd-component-sql-injection-in-data-mapping / -in-script, check-service-item-sql-injection-* | MAJOR | SQL built by string concatenation |
| check-bpd-contains-infinite-loop, check-service-item-contains-infinite-loop | MAJOR | loops without exit |
| check-bpd-documentation, check-service-documentation, check-participant-documentation, check-webservice(-operation)-documentation, check-*-variables-documentation | MINOR | missing documentation |
| check-bpd-duplicated-EPVs, check-service-duplicated-EPVs, check-service-duplicated-resource-bundles | MAJOR | duplicated EPV / resource bundle references |
| check-bpd-error-not-fully-implemented, check-service-error-not-fully-implemented | MAJOR | error events without implementation |
| check-bpd-event-end-contains-script, check-bpd-event-start-contains-script, check-service-event-end-contains-script | MAJOR | scripts on start/end events |
| check-bpd-event-end-delete-instance-data | MINOR | terminate event keeps instance data |
| check-bpd-gateway-condition | CRITICAL | incomplete gateway conditions (no default path) |
| check-bpd-humanservice-in-system-lane | MAJOR | human service in a system lane |
| check-bpd-implementation-javascript-coding-style, check-service-item-*-javascript-coding-style, check-page-javascript-coding-style | MINOR | JavaScript style |
| check-bpd-message-event | MAJOR | message event without UCA |
| check-bpd-not-fully-implemented, check-bpd-phantom-steps, check-service-not-fully-implemented, check-service-item-not-implemented | MAJOR | unimplemented activities / items |
| check-bpd-searchable-field | CRITICAL | > 25 searchable fields |
| check-bpd-step-incorrectly-referenced, check-service-step-incorrectly-referenced | MAJOR | unlinked / dangling steps |
| check-bpd-unused-variables, check-service-unused-variables | MAJOR | unused variables |
| check-delete-completed-task | CRITICAL | system tasks not deleted on completion |
| check-lane-participant-not-default, check-participant-all-users | MAJOR | lanes / teams bound to All Users |
| check-MIL-system-activity | MAJOR | multi-instance loop in a system lane |
| check-sequential-activities-in-lane | MAJOR | "string of pearls" (many sequential activities in one lane) |
| check-app-unused-service | MAJOR | unused services |
| check-coach-with-too-many-coach-view | CRITICAL | > 500 controls in one coach |
| check-humanservice-with-too-many-coach | MAJOR | > 5 coaches in a human service |
| check-service-exception-loop-item | CRITICAL | exception handler looping back to the failing item |
| check-service-javascript-length / -pro | MAJOR / CRITICAL | script > 100 / > 300 lines |
| check-service-SOPE-directly-follow-postpone-object | MAJOR | Stay-on-page event directly after Postpone |
| check-service-with-too-many-nested-cshs | MAJOR | > 25 nested client-side human services |
| check-*-variables-naming-conversion | MAJOR | variable naming convention |
| check-service-with-too-many-inputvariables / -outputvariables | MAJOR | > 15 input / output variables |

Case solution / case type rules (CP4BA) are not applicable to a TWX (they need the case solution package).

## Redbooks
- IBM Business Process Management Design Guide (SG24-8282) - chapter 5 "Design considerations and patterns".
- IBM BPM V8.5 Performance Tuning and Best Practices (SG24-8216) - chapters 2/3 architecture and development best practices.
- IBM BPM Operations Guide (SG24-8356) - anti-patterns (testing on Process Center, no think time, unrealistic data, no load test).

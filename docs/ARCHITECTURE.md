# TWX Code Analyzer - architecture

## Goals
Static analysis of a TWX export without any BPM database: a legacy code analyzer on Process Center queried the repository tables; this tool reads everything from the TWX zip. One engine, several deliveries: Linux / Windows desktop app (local web UI), a web application behind a reverse proxy, and an embedding facade for a BAW / CP4BA process application (Java 8 bytecode, no dependencies except the shaded Rhino parser).

## Modules (package `tca`)
| Package | Content |
|---|---|
| `tca.model` | `TwxModel` (application + bundled toolkits, loader diagnostics), `TwxPackage` (identity, buildVersion, dependencies, objects, files, JavaScript assets), `TwxObject` (raw XML + references), parsed models: `ServiceModel` (steps, links, variables), `BpdModel` (lanes, flow objects, flows, boundary events, variables), `CoachViewModel`, `BusinessObjectModel`, `Script` (snippet + location) |
| `tca.parse` | `TwxLoader` (zip -> model, toolkits recursively, embedded subprocess naming), `ServiceParser`, `BpdParser`, `CoachViewParser`, `BusinessObjectParser`, `Scripts` (all JavaScript / template / condition / mapping snippets of an object) |
| `tca.rules` | `Rule` (id, title, category, severity, description, remediation, reference, impact, confidence), `Finding` (full artifact path, line / column / snippet), `RuleContext` (parsed models cached, reverse reference index, declared variables, thresholds, per-analysis cache), rule sets: `AppRules`, `DependencyRules`, `ServiceRules`, `BpdRules`, `ScriptRules` (pattern rules on comment/string-stripped code, Rhino syntax check, SQL templates), `AstRules` (syntax-tree rules: scope resolution, undeclared variables, constant tracking, loops, eval, SQL construction), `CoachRules`, `BusinessObjectRules`, `QualityRules`, `LegacyRules`; `RuleSet` registry |
| `tca.engine` | `Analyzer` (runs the rules, ranks findings, fills coverage / diagnostics / version / toolkit usage), `Report`, `ToolkitUsage` (evidence-tiered usage per toolkit), `ObjectViews` (artifact tree and detail: variables, elements, nested schema, assets), `Diff` (object diff + findings delta), `Pdf` (dependency-free PDF writer), `PdfReport` (findings and toolkit usage PDFs, finding filter) |
| `tca.diagram` | `DiagramBuilder`: service flow / BPD -> nodes (Process Designer coordinates), edges, lanes for the SVG renderer in the UI |
| `tca.search` | `Searcher`: text / regex search over names, scripts, documentation and raw XML with type filters |
| `tca.web` | `WebServer` (JDK `com.sun.net.httpserver`, JSON API + static files, PDF endpoints), `Multipart`, `Store` (history as files under `data/<id>/`) |
| `tca.baw` | `Facade` (JSON in / JSON out entry point for embedding), `ProcessCenterExport` (snapshot export through the Process Center servlet, on request) |
| `tca.util` | `Json` (writer/parser), `Xml` (regex helpers for the export layout) |
| `tca.Main` | CLI: `analyze <twx> [out.json]`, `inventory <twx>`, `rules [out.md]`, `serve [port] [dataDir] [--no-browser]`, `baw <op> [argsJson] [folder]` |

## TWX facts used by the parsers
* `META-INF/package.xml`: root `buildVersion` / `buildId` (product that produced the export), `<target>` (project, branch, snapshot with `originalCreationDate`), `<dependencies>` (toolkit snapshots), `<objects>` (id, versionId, name, type). `toolkits/*.zip` = bundled toolkit exports (same layout). `files/<assetId>/<name>` = managed asset content.
* Service (`process`, id `1.*`): `<processType>` (1 decision service, 2 Ajax service, 3 heritage human service, 4 integration service, 6 general system service, 7 advanced integration service, 10 client-side human service, 11 external service, 12 service flow, 13 deployment service flow), `<item>` steps with `<tWComponentName>` (Script, SubProcess, ExitPoint, CoachNG, Switch, StayOnPage, Postpone, connectors), `<layoutData x y>`, `<TWComponent>` (script + `<scriptTypeId>` 2 = JavaScript, 128 = text template with `<#= expr #>` inserts; `<attachedProcessRef>`; `<parameterMapping>`), `<link>` (from/to item, endStateId, condition), `<processParameter>` (parameterType 1 input, 2 output) / `<processVariable>` (private).
* Process (`bpd`, id `25.*`): `<bpdParameter>` (outer declarations) and, inside the `<BPD>` element, `<inputParameter>` / `<outputParameter>` / `<privateVariable>` with `<name>` and `<classId>`; `<lane>` (name, systemLane, attachedParticipant) > `<flowObject componentType="Activity|Gateway|Event">` (position, component details, `<attachedActivityId>`, `<attachedEvent>` boundary events), `<flow id>` (name, condition `<expression>`); an activity with `<embeddedProcessId>` embeds a subprocess BPD that shares the parent's variables and is exported with its GUID as name.
* Coach view (`64.*`): `<layout>` (escaped CoachDesignerNG XML), `<bindingType>`, `<configOption>`, Behavior handlers (`loadJsFunction` ...), `<inlineScript>` (JS/CSS/HTML), `<amdDependency>` (module + functionArgument), `<resource>` (`filePath` or `assetUuid` = `<project uuid>/61.<asset id>`), `<previewAdvHtml/Js>`.
* Business object (`twClass`, `12.*`): `<definition><property>` (classRef, arrayProperty), `<shared>`.
* Managed asset (`61.*`): `<assetTypeCode>` J = server file (functions callable from server scripts), W = web file (included by coach views), D = design file; `<mimeType>`, `<length>`.

## Syntax-tree rules
Every server script (step script, template, condition, mapping, default value) and coach view script (handler, inline) is parsed once per analysis with the Rhino parser, wrapped in a function so that `event` / `context` parameters and `return` statements are legal; the result is cached in the rule context. Scripts with syntax errors are reported by TCA-JS-021, counted in the report coverage and skipped by the other syntax-tree rules. Identifier resolution uses Rhino's scope chain plus: catch variables, BAW server API objects (`tw`, `log`, `TWDate`, `BPMRESTRequest` ...), functions and top-level variables of the JavaScript server files of the application and its toolkits (`assetTypeCode` J), browser and coach globals, AMD dependency arguments, top-level names of the view's other scripts, and the globals of the web files (`assetTypeCode` W) the view includes (top-level functions and variables, `window.x =`, and the file-name stem for minified libraries). Names defined by a web file the view does not include are reported with that hint. `tw.local.<name>` is checked against the parameters and private variables of the service or process; embedded subprocesses inherit the embedding process's variables. Constant tracking is straight-line only (top-level statements, reset by any control flow, not entering nested functions).

## Toolkit usage
`ToolkitUsage.compute` merges duplicate snapshots by project id (fallback acronym, then name), then classifies each toolkit artifact: *used* when an application artifact references its id (reverse reference index), *possible* when an application script contains `new tw.object.<Name>()` and exactly one toolkit declares that business object name (and the application none), otherwise *none*. Ambiguous constructor names become diagnostics. References from other toolkits are collected per logical toolkit.

## Ranking and health
score = severity weight (Critical 100, Major 40, Minor 10, Info 2) x impact (1..3). Findings are sorted by score, severity, artifact. Health = 100 x exp(-density / 40) with density = total score / application objects (100 = clean; a legacy application with ~65 points per object scores ~20). Rules with confidence `medium` produce "needs review" findings; they count like the others but can be filtered out.

## Report layout
`id, fileName, fileSize, analyzedAt, engineVersion, durationMs, app {name, acronym, snapshot, snapshotId, projectId, branchId, buildVersion, bawVersion}, inventory {objects, toolkits, scripts, scriptLines, types, toolkitList}, summary {findings, score, bySeverity, byCategory, healthScore}, coverage {scripts, scriptsParsed, scriptsWithSyntaxErrors, skipped[], status}, diagnostics[], toolkitUsage {summary, toolkits[], diagnostics[]}, rules[], findings[]`.

## Diagrams
The UI draws SVG from the coordinates stored in the TWX (Process Designer layout): services use 100x50 boxes, 50x50 decision diamonds, 40x40 end points; processes use 95x70 activities, 32x32 gateways, 24x24 events, lanes stacked by their heights. Nodes with findings are outlined in red; the finding detail's "Show in diagram" button opens the object with the step selected.

## PDF
`tca.engine.Pdf` writes PDF 1.4 directly (Helvetica / Helvetica-Bold with WinAnsi encoding, word wrapping with an approximate glyph width table, ruled tables with repeated headers across page breaks, page footer). `PdfReport` renders the findings report (summary, rules in the selection, ranked findings) and the toolkit usage report; both are available from the web app and the facade.

## Deliveries
* Desktop (Linux/Windows): `build/twx-code-analyzer.jar` + `static/` + `run.sh` / `run.bat` (+ optional `jre/` from jlink). The app listens on 127.0.0.1 only and opens the browser.
* Web application: same jar served behind a reverse proxy (multi-user history = one data directory per user).
* Embedding: `tca.baw.Facade` (see `EMBEDDING.md`). The BAW process application that packages the engine is a separate delivery.

# Embedding the engine

The analyzer is a plain Java library (Java 8 bytecode, one shaded dependency) and can be embedded in other deliveries: a batch job, another web application, or an IBM BAW / CP4BA process application that runs the jar as a managed server file. Two entry points exist.

## Java API

```java
TwxModel model = TwxLoader.load(new File("MyApp.twx"));          // or load(byte[])
RuleSettings rs = RuleSettings.fromJson(settingsJson)             // optional customisation (settings file format), or new RuleSettings() for the defaults
        .prune(RuleSet.all());                                    // drops unknown rules and default values, fills rs.warnings
rs.includeToolkits = true;
Map<String, Object> settings = rs.toAnalyzerSettings();           // includeToolkits + flat thresholds + the RuleSettings object
Report report = new Analyzer().analyze(model, settings);         // disabled rules skipped, custom severity / impact / weights applied; report.settings records them
Map<String, Object> json = report.toJson();                       // inventory, summary, coverage, diagnostics, settings, toolkitUsage, rules, findings
Map<String, Object> doc = rs.document(RuleSet.all());             // full settings document (export file: every rule with defaults and effective values)
RuleContext ctx = new RuleContext(model, settings, true);
ObjectViews.objects(ctx, true);                                   // artifact tree
ObjectViews.objectDetail(ctx, model.find(objectId), true);        // detail with variables, elements, schema, raw XML
ObjectViews.diagram(ctx, model.find(objectId));                   // nodes, edges, lanes
Searcher.search(ctx, "tw.local.sql", false, false, "all", types, true, 500);
ToolkitUsage.compute(ctx);
PdfReport.findings(report, PdfReport.filterFindings(report, params), PdfReport.describe(params));
PdfReport.toolkitUsage(report, selectedToolkits);
Diff.objects(modelA, modelB); Diff.findings(reportA.findings, reportB.findings);
```

All results are `Map` / `List` structures serialisable with `tca.util.Json`.

## JSON facade (`tca.baw.Facade`)

One method, JSON in and JSON out, designed for environments that can only call a static Java method with string arguments (for example a BAW Java integration service):

```java
String result = new tca.baw.Facade().call(op, argsJson, folder);
```

`folder` is a server directory holding the TWX files; stored reports are cached under `<folder>/.tca/`. Errors come back as `{"error": "..."}`.

| Operation | Arguments | Result |
|---|---|---|
| `ping` | | version, folder, Java version |
| `files` | | TWX files in the folder (name, size, modified) |
| `analyze` | `fileName`, `includeToolkits`, `settings` (optional one-off overrides, object or JSON text) | runs the rules with the stored settings, stores the report, returns the summary with `reportKey` and `customized` |
| `settings` | | full rule settings document (weights, thresholds, every rule with default and effective values) - the export file |
| `saveSettings` | `settings` (document, full or partial, object or JSON text) | validates and stores the settings in `<folder>/.tca/settings.json`; returns `saved`, `warnings` (ignored entries), `settings` |
| `resetSettings` | | back to the built-in defaults |
| `report` | `reportKey` | the stored report JSON |
| `objects` | `fileName`, `toolkits` | artifact tree |
| `object` | `fileName`, `objectId`, `xml` | artifact detail (variables, elements, schema, scripts, references) and diagram |
| `search` | `fileName`, `q`, `regex`, `case`, `scope`, `types`, `toolkits`, `limit` | search hits |
| `compare` | `fileA`, `reportA`, `fileB`, `reportB` | object differences and findings delta |
| `toolkitUsage` | `fileName` | toolkit usage (also part of every stored report) |
| `pdf` | `reportKey`, `sev`, `cat`, `rule`, `type`, `conf`, `q`, `sort` | findings PDF as `{fileName, base64}` (same filter as the web UI) |
| `toolkitUsagePdf` | `reportKey`, `keys` (`k1|k2`, empty = all) | toolkit usage PDF as `{fileName, base64}` |
| `rules` | | rule catalogue with the effective severity, impact, enabled and customized flags |
| `export` | `url`, `user`, `password`, `snapshotId`, `fileName` | exports a Process Center snapshot into the folder (form login + ImportExportServlet); the only network call of the engine, made on explicit request |
| `deleteFile` | `fileName` | removes a TWX file and its stored reports |

Models are cached (last three files, invalidated when the file changes). Analyses are serialised; a 40 MB export analyzes in about 10 seconds with a 1 GB heap.

## Notes for a BAW / CP4BA delivery

* Put the jar on the server as a managed server file and call `Facade.call` from a Java integration step; expose the calling service flow to coaches through an Ajax-enabled service and pass the operation and arguments as JSON.
* The PDF operations return base64; download in the browser with a Blob and an anchor element.
* Rule settings: `settings` returns the document to show in an editor or to offer as a download (export); `saveSettings` takes the edited document or an uploaded file (import); the settings live next to the report cache in `<folder>/.tca/` and apply to every analysis of that folder.
* Keep TWX files out of the process application: they live in the server folder (`folder`), which also holds the report cache.
* The process application that packages this engine for BAW is a separate delivery and is not part of this repository.

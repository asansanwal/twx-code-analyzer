# Embedding the engine

The analyzer is a plain Java library (Java 8 bytecode, one shaded dependency) and can be embedded in other deliveries: a batch job, another web application, or an IBM BAW / CP4BA process application that runs the jar as a managed server file. Two entry points exist.

## Java API

```java
TwxModel model = TwxLoader.load(new File("MyApp.twx"));          // or load(byte[])
Report report = new Analyzer().analyze(model, settings);         // settings: includeToolkits (Boolean), thresholds (Integer per name)
Map<String, Object> json = report.toJson();                       // inventory, summary, coverage, diagnostics, toolkitUsage, rules, findings
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
| `analyze` | `fileName`, `includeToolkits` | runs the rules, stores the report, returns the summary with `reportKey` |
| `report` | `reportKey` | the stored report JSON |
| `objects` | `fileName`, `toolkits` | artifact tree |
| `object` | `fileName`, `objectId`, `xml` | artifact detail (variables, elements, schema, scripts, references) and diagram |
| `search` | `fileName`, `q`, `regex`, `case`, `scope`, `types`, `toolkits`, `limit` | search hits |
| `compare` | `fileA`, `reportA`, `fileB`, `reportB` | object differences and findings delta |
| `toolkitUsage` | `fileName` | toolkit usage (also part of every stored report) |
| `pdf` | `reportKey`, `sev`, `cat`, `rule`, `type`, `conf`, `q`, `sort` | findings PDF as `{fileName, base64}` (same filter as the web UI) |
| `toolkitUsagePdf` | `reportKey`, `keys` (`k1|k2`, empty = all) | toolkit usage PDF as `{fileName, base64}` |
| `rules` | | rule catalogue |
| `export` | `url`, `user`, `password`, `snapshotId`, `fileName` | exports a Process Center snapshot into the folder (form login + ImportExportServlet); the only network call of the engine, made on explicit request |
| `deleteFile` | `fileName` | removes a TWX file and its stored reports |

Models are cached (last three files, invalidated when the file changes). Analyses are serialised; a 40 MB export analyzes in about 10 seconds with a 1 GB heap.

## Notes for a BAW / CP4BA delivery

* Put the jar on the server as a managed server file and call `Facade.call` from a Java integration step; expose the calling service flow to coaches through an Ajax-enabled service and pass the operation and arguments as JSON.
* The PDF operations return base64; download in the browser with a Blob and an anchor element.
* Keep TWX files out of the process application: they live in the server folder (`folder`), which also holds the report cache.
* The process application that packages this engine for BAW is a separate delivery and is not part of this repository.

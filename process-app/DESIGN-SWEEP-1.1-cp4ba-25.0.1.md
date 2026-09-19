# Design-time sweep: TWX-Code-Analyzer-1.1.twx on https://<cp4ba-route>/bas

Profile cp4ba25, branch `2063.302e8f24-5f1f-5fa3-ad80-d801cbe3ced1`, 2026-09-12: 13/16 artifacts OK.

| # | Artifact | Kind | Opened | Rendered | Labels seen | Validation | Dialogs | JS errors | Failed requests | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | TWX Code Analyzer | process/10 | opened (item) | yes | 1/2 (missing Client-Side Script) | 1 |  /  | 0 | 0 | OK |
| 2 | style | coachView | opened (item) | yes | 0/0 | 1 |  /  | 1 | 1 | CHECK |
| 3 | Initialize | process/12 | opened (item) | yes | 1/1 | 1 |  /  | 0 | 0 | OK |
| 4 | App Header | coachView | opened (item) | yes | 3/5 (missing HeaderRow, Root) | 1 |  /  | 0 | 0 | OK |
| 5 | Analyzer Java Service | process/4 | row not found | no | 0/0 |  | - | 3 | 1 | CHECK |
| 6 | Analyzer Service | process/12 | opened (item) | yes | 2/2 | 1 |  /  | 0 | 0 | OK |
| 7 | TWX Analysis | bpd | opened (item) | yes | 2/2 | 1 |  /  | 0 | 0 | OK |
| 8 | Analyze | coachView | opened (item) | yes | 3/12 (missing Actions, Actions2, BtnAnalyzeselectedfile, BtnDeletefile, BtnExportsnapshotfromProcessCenter, BtnFilter) | 1 |  /  | 1 | 1 | CHECK |
| 9 | Findings | coachView | opened (item) | yes | 7/12 (missing BtnApply, BtnExportCSV, BtnExportHTML, BtnExportPDF, Filters) | 1 |  /  | 0 | 0 | OK |
| 10 | Overview | coachView | opened (item) | yes | 4/12 (missing Categories, Categories_name, Categories_value, Inventory_name, Inventory_value, Root) | 1 |  /  | 0 | 0 | OK |
| 11 | Objects & Diagrams | coachView | opened (item) | yes | 10/12 (missing BtnRefresh, ObjFindings) | 1 |  /  | 0 | 0 | OK |
| 12 | Toolkit Usage | coachView | opened (item) | yes | 4/12 (missing Actions, BtnExportCSV, BtnExportHTMLselected, BtnExportPDFselected, Root, Rows) | 1 |  /  | 0 | 0 | OK |
| 13 | TWX Search | coachView | opened (item) | yes | 5/12 (missing BtnExportCSV, BtnSearch, Query, Root, Rows, Rows_path) | 1 |  /  | 0 | 0 | OK |
| 14 | History | coachView | opened (item) | yes | 3/12 (missing Actions, BtnComparethetwoselected, BtnDelete, BtnExportCSV, BtnOpen, BtnRefresh) | 1 |  /  | 0 | 0 | OK |
| 15 | Compare | coachView | opened (item) | yes | 7/12 (missing BtnCompare, BtnLoadanalyses, FixedFindings, FixedFindings_message, FixedFindings_path) | 1 |  /  | 0 | 0 | OK |
| 16 | Rules | coachView | opened (item) | yes | 7/12 (missing BtnExportCSV, Root, Rows, Rows_category, Rows_id) | 1 |  /  | 0 | 0 | OK |

Project-wide validation counter of the designer's bottom bar during the sweep: 1 (open the warning icon in the designer for the messages).

Details of every CHECK row:

* **style** (coachView): status opened (item); dialogs ['', '']; JS ['console: _775']; failed ['404 https://<cp4ba-route>/bas/rest/bpm/wle/pd/v1/managedAsset/61.26865295-8511-4538-b8f1-4cb99c203a92/contentInZip?c {"status":"error","Data":{"status":"error","exceptionType":"com.ibm.bpmsdk.rest.exception.api.ArtifactNotFoundException","errorNumber":"CWMHW0326E","errorMessage":"CWMHW0326E: The artifact \\"61.268652']; canvas {'coachViews': 258, 'svgNodes': 3775, 'svgText': ['Start', 'The', 'Dashboard', 'End', 'Load', 'Configuration', 'Error2'], 'canvases': 0, 'previews': 73, 'canvasItems': 5}; screenshot `tz_twxca_shots/002_style.png`
* **Analyzer Java Service** (process/4): status row not found; dialogs []; JS ['console: _775', 'console: _775', 'console: _775']; failed ['500 https://<cp4ba-route>/bas/WebPD/jsp/bootstrap.jsp?containerRef=2063.302e8f24-5f1f-5fa3-ad80-d801cbe3ced1 Error 500 - Internal Server Error']; canvas {'coachViews': 0, 'svgNodes': 0, 'svgText': [], 'canvases': 0, 'previews': 0, 'canvasItems': 0}; screenshot `tz_twxca_shots/005_Analyzer_Java_Service.png`
* **Analyze** (coachView): status opened (item); dialogs ['', '']; JS ['console: _775']; failed ['404 https://<cp4ba-route>/bas/rest/bpm/wle/pd/v1/managedAsset/61.26865295-8511-4538-b8f1-4cb99c203a92/contentInZip?c {"status":"error","Data":{"status":"error","exceptionType":"com.ibm.bpmsdk.rest.exception.api.ArtifactNotFoundException","errorNumber":"CWMHW0326E","errorMessage":"CWMHW0326E: The artifact \\"61.268652']; canvas {'coachViews': 47, 'svgNodes': 5489, 'svgText': ['System', 'Start', 'End', 'Folder', 'Call engine', 'System', 'Start', 'Analyze', 'Retention', 'End'], 'canvases': 0, 'previews': 13, 'canvasItems': 53}; screenshot `tz_twxca2_shots/004_Analyze.png`

# Design-time sweep: TWX-Code-Analyzer-1.1.twx on https://<process-center-host>:9443

Profile pc862, branch `2063.302e8f24-5f1f-5fa3-ad80-d801cbe3ced1`, 2026-09-11: 15/16 artifacts OK.

| # | Artifact | Kind | Opened | Rendered | Labels seen | Validation | Dialogs | JS errors | Failed requests | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | TWX Code Analyzer | process/10 | opened (row) | yes | 1/2 (missing Client-Side Script) | 0 | - | 1 | 0 | OK |
| 2 | style | coachView | opened (row) | yes | 0/0 | 0 | - | 0 | 0 | OK |
| 3 | Initialize | process/12 | opened (row) | yes | 1/1 | 0 | - | 0 | 0 | OK |
| 4 | App Header | coachView | opened (row) | yes | 3/5 (missing HeaderRow, Root) | 0 | - | 0 | 0 | OK |
| 5 | Analyzer Java Service | process/4 | opened (row) | yes | 0/0 | 0 | - | 0 | 0 | OK |
| 6 | Analyzer Service | process/12 | opened (row) | yes | 2/2 | 0 | - | 0 | 0 | OK |
| 7 | TWX Analysis | bpd | opened (row) | yes | 2/2 | 0 | - | 0 | 0 | OK |
| 8 | Analyze | coachView | opened (row) | yes | 3/12 (missing Actions, Actions2, BtnAnalyzeselectedfile, BtnDeletefile, BtnExportsnapshotfromProcessCenter, BtnFilter) | 0 | - | 0 | 0 | OK |
| 9 | Findings | coachView | opened (row) | yes | 7/12 (missing BtnApply, BtnExportCSV, BtnExportHTML, BtnExportPDF, Filters) | 0 | - | 0 | 0 | OK |
| 10 | Overview | coachView | opened (row) | yes | 4/12 (missing Categories, Categories_name, Categories_value, Inventory_name, Inventory_value, Root) | 0 | - | 0 | 0 | OK |
| 11 | Objects & Diagrams | coachView | opened (row) | yes | 7/12 (missing Bar, BtnRefresh, Details, NodeInfo, ObjFindings) | 0 | - | 0 | 0 | OK |
| 12 | Toolkit Usage | coachView | row not in popup (0,0,0) | yes | 3/12 (missing Actions, BtnExportCSV, BtnExportHTMLselected, BtnExportPDFselected, Detail, Root) | 0 | - | 0 | 0 | CHECK |
| 13 | TWX Search | coachView | opened (row) | yes | 4/12 (missing Bar, BtnExportCSV, BtnSearch, Query, Root, Rows) | 0 | - | 0 | 0 | OK |
| 14 | History | coachView | opened (row) | yes | 3/12 (missing Actions, BtnComparethetwoselected, BtnDelete, BtnExportCSV, BtnOpen, BtnRefresh) | 0 | - | 0 | 0 | OK |
| 15 | Compare | coachView | opened (row) | yes | 6/12 (missing Bar, BtnCompare, BtnLoadanalyses, FixedFindings, FixedFindings_message, FixedFindings_path) | 0 | - | 0 | 0 | OK |
| 16 | Rules | coachView | opened (row) | yes | 6/12 (missing Bar, BtnExportCSV, Root, Rows, Rows_category, Rows_id) | 0 | - | 0 | 0 | OK |

Project-wide validation counter of the designer's bottom bar during the sweep: 0 (open the warning icon in the designer for the messages).

Details of every CHECK row:

* **Toolkit Usage** (coachView): status row not in popup (0,0,0); dialogs []; JS []; failed []; canvas {'coachViews': 107, 'svgNodes': 10281, 'svgText': ['Start', 'The', 'Dashboard', 'End', 'Load', 'Configuration', 'Error2', 'System', 'Start', 'End', 'Read', 'environment', 'System', 'An error', 'occurred while', 'trying to', 'convert the...', 'Start', 'Call engine', 'End', 'System', 'Start', 'End', 'Folder', 'Call engine', 'System', 'Start', 'Analyze', 'Retention', 'End'], 'canvases': 0, 'previews': 24}; screenshot `twxca_shots/012_Toolkit_Usage.png`

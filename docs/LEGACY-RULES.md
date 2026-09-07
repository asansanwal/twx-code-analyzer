# Legacy code analyzer rule catalogue (176 rules) and their static-analysis status

Source: rule table of the legacy code-analyzer application (DB based). Score 1..10 and severity 1 (critical) .. 3 (minor) are the legacy weights; "TCA rule" is the TWX Code Analyzer rule that covers the check statically (see docs/RULES.md); "static" says whether the check is possible from a TWX alone.

| Legacy id | Title | Score | Severity | Category | Artifact type | TCA rule / status |
|---|---|---|---|---|---|---|
| 1 | Code Quality Score of Application |  |  |  | 0 |  health score (report summary) |
| 2 | String Concatenation | 3 | Minor | JS | 3011 |  TCA-JS-001 |
| 3 | Non-Parametrized queries | 6 | Critical | SQL | 3011 |  TCA-JS-001, TCA-JS-024 |
| 4 | Javascript Minification | 2 | Minor | JS | 61 |  TCA-APP-043 |
| 5 | CSS Minification | 2 | Minor | Static | 61 |  TCA-APP-043 |
| 6 | Image Optimization | 2 | Minor | Static | 61 |  not static (image optimisation) - APP-043 size only |
| 7 | Large Number of Steps before Coach | 7 | Critical | HS | 1 |  TCA-SVC-031 |
| 8 | Loops within Loops | 3 | Minor | JS | 3011 |  TCA-JS-030 |
| 9 | Repeated JS Functions | 1 | Minor | JS | 3011 |  TCA-JS-035 |
| 10 | Large Managed Assets | 2 | Minor | Static | 61 |  TCA-APP-043 |
| 11 | Heavily Loaded Coaches | 7 | Critical | HS | 1 |  TCA-UI-001 |
| 12 | Decision Services in Loop | 5 | Major | INTG | 1 |  TCA-SVC-030 |
| 13 | System Activity as first step of Multi-Instance Loop | 8 | Critical | BPD | 25 |  TCA-BPD-008 |
| 14 | Direct access to Product DB | 8 | Critical | INTG | 3011 |  TCA-JS-002 |
| 15 | Thread.sleep Usage | 3 | Minor | JS | 3011 |  TCA-JS-003 |
| 16 | Live Connect Usage | 2 | Minor | JS | 3011 |  TCA-JS-004 |
| 17 | Too many Toolkits | 3 | Minor | TK | 2069 |  TCA-APP-001 |
| 18 | Large Toolkits | 2 | Minor | TK | 2069 |  TCA-APP-004 |
| 19 | Too many steps in a service flow | 5 | Major | INTG | 1 |  TCA-QA-006 |
| 20 | Error Handlers without Human Service | 3 | Minor | HS | 1 |  TCA-SVC-006 |
| 21 | BPD Complexity Score - Too complex BPDs | 7 | Critical | BPD | 25 |  TCA-SVC-041 |
| 22 | Human Service Complexity Score - Too complex Human Service/CSHS | 7 | Critical | HS | 1 |  TCA-SVC-041 |
| 23 | Customization of CSS without themes | 1 | Minor | HS | 64 |  not static (theme usage) |
| 24 | Excessive Logging | 3 | Minor | INTG | 3011 |  TCA-JS-034 |
| 25 | JavaScript Hard Codings | 2 | Minor | JS | 3011 |  TCA-JS-006, TCA-JS-007, TCA-JS-032 |
| 26 | Non-Ending HHS/CSHS | 4 | Major | HS | 1 |  TCA-SVC-001 |
| 27 | Hard Coded JNDI Name | 5 | Major | INTG | 1 |  TCA-JS-032 |
| 28 | Dynamic WSDLs instead of static Local Copies | 3 | Minor | INTG | 1 |  not static (WSDL location) - partly TCA-JS-032 |
| 29 | JS loaded as server file | 3 | Minor | Model | 61 |  TCA-JS-017 |
| 30 | Too Large Variables | 7 | Critical | Model | 12 |  TCA-SVC-034, TCA-BO-001 |
| 31 | BPD Envelope Pattern not used | 7 | Critical | BPD | 25 |  TCA-SVC-034 (context size) |
| 32 | BPD Claim Check Pattern not used | 7 | Critical | BPD | 25 |  TCA-SVC-034 (context size) |
| 33 | Large BPD Context Size | 7 | Critical | BPD | 25 |  TCA-SVC-034 |
| 34 | Large HHS/CSHS Context Size | 7 | Critical | HS | 1 |  TCA-SVC-034 |
| 35 | All User Exposure Service | 5 | Major | Model | 1 |  TCA-SVC-039 |
| 36 | Complicated and Large Validation Logic | 3 | Minor | HS | 1 |  TCA-JS-020 (script size) |
| 37 | Polling pattern in Coach | 3 | Minor | HS | 1 |  TCA-UI-010 |
| 38 | Naming Convention Check | 3 | Minor | Model | 2056 |  TCA-SVC-016, TCA-BPD-017, TCA-BO-005 |
| 39 | Hard Coded WSDL Endpoint | 5 | Major | INTG | 1 |  TCA-JS-032 |
| 40 | Duplicate Managed Assets | 2 | Minor | Model | 61 |  TCA-APP-044, TCA-UI-008 |
| 41 | Auto-tracking of BPDs turned on | 8 | Critical | BPD | 25 |  TCA-BPD-020 |
| 42 | System Tasks delete at completion not checked | 8 | Critical | BPD | 25 |  TCA-BPD-014 |
| 43 | Level of Business Data Exposure by BPD | 5 | Major | BPD | 25 |  TCA-BPD-012 |
| 44 | Level of Business Data Exposure by Application | 5 | Major | BPD | 25 |  TCA-BPD-012 |
| 45 | General JS Bad Practices | 1 | Minor | JS | 3011 |  TCA-JS-012..023 |
| 46 | Save execution context checked | 5 | Major | HS | 1 |  TCA-SVC-014 |
| 47 | BPD swimlane assigned to All Users | 8 | Critical | BPD | 25 |  TCA-BPD-007 |
| 48 | Sequential system lane activities -(String of pearls) | 5 | Major | BPD | 25 |  TCA-BPD-013 |
| 49 | Mutually dependent toolkits | 5 | Major | Model | 2069 |  TCA-DEP-001 |
| 50 | Missing Error Handling DB/SQL Calls | 3 | Minor | INTG | 1 |  TCA-SVC-006 |
| 51 | Missing Error Handling Web Services | 3 | Minor | INTG | 1 |  TCA-SVC-006 |
| 52 | Governance Process not used | 1 | Minor | Model | 0 |  not static (governance) |
| 53 | EPVs usage in Server Script | 4 | Major | INTG | 1 |  not applicable |
| 54 | JS Code not indented | 1 | Minor | JS | 3011 |  not static (formatting) |
| 55 | Insufficient Logging | 1 | Minor | INTG | 1 |  not static |
| 56 | Validation Errors | 1 | Minor | Model | 1 |  not static (designer validation) |
| 57 | No usage of Decision Services | 1 | Minor | Model | 0 |  not applicable |
| 58 | Wrong version of system toolkit | 6 | Major | Model | 0 |  TCA-APP-002 |
| 59 | Outdated toolkit versions | 3 | Minor | Model | 0 |  TCA-APP-002 (version mismatch) |
| 60 | Default values set for input variables | 3 | Minor | Model | 1 |  TCA-SVC-035 |
| 61 | Floating static resources | 1 | Minor | Model | 61 |  TCA-DEP-006 |
| 62 | Not used services | 3 | Minor | Model | 1 |  TCA-DEP-003 |
| 63 | Not used Resouce Bundles | 3 | Minor | Model | 50 |  TCA-QA-010 |
| 64 | Repeated Ajax Services on Coach | 7 | Critical | HS | 64 |  not static (ajax usage on coach) |
| 65 | Deep Copy in a Loop | 6 | Major | JS | 3011 |  TCA-JS-031 |
| 66 | Deprecated Responsive Coaches Toolkit Usage | 5 | Major | HS | 0 |  TCA-UI-004 |
| 67 | Depcreated Coaches Toolkit Usage | 5 | Major | HS | 0 |  TCA-UI-004 |
| 68 | Unused Toolkits | 3 | Minor | Model | 0 |  TCA-APP-040 |
| 69 | Non-standard XML parsing | 2 | Minor | JS | 3011 |  TCA-JS-033 |
| 70 | Business Data Lookup in a Loop | 5 | Major | JS | 3011 |  TCA-SVC-030 |
| 71 | Duplicate environment variables across Toolkits and Process App | 2 | Minor | Model | 0 |  TCA-APP-041 |
| 72 | Auto Save Timer | 5 | Major | HS | 64 |  TCA-UI-010 |
| 73 | EPV and ENV usage without typecasting | 1 | Minor | JS | 3011 |  not static |
| 74 | Serializer Usage | 2 | Minor | JS | 3011 |  TCA-JS-033 |
| 75 | Un-used Variables | 4 | Major | Model | 12 |  TCA-SVC-009, TCA-BPD-018 |
| 76 | Unused Config Options in Coach Views | 3 | Minor | HS | 64 |  not static (coach option usage) |
| 77 | Javascript set as AMD module but not used | 1 | Minor | HS | 64 |  TCA-UI-015 |
| 78 | Unwanted artificats (e.g. TODO, 2, 2 2 etc) | 1 | Minor | Model | 0 |  TCA-QA-001 |
| 79 | Default Human Service in BPD | 4 | Major | BPD | 25 |  not static |
| 80 | Default System Service in BPD | 4 | Major | BPD | 25 |  not static |
| 81 | Web Service invocation in a loop | 7 | Critical | INTG | 1 |  TCA-SVC-030 |
| 82 | Java Connector in a loop | 7 | Critical | INTG | 1 |  TCA-SVC-030 |
| 83 | SQL calls in a loop | 7 | Critical | INTG | 1 |  TCA-SVC-030 |
| 84 | Too many custom HTML in a Coach | 5 | Major | HS | 3003 |  not static |
| 85 | Style coach view (only css) | 1 | Minor | HS | 64 |  not static |
| 86 | Duplicate js/css usage in Coach Views | 3 | Minor | HS | 64 |  TCA-UI-008 |
| 87 | Deeply nested Coach Views | 7 | Critical | HS | 64 |  not static |
| 88 | Excessive Boundary Events | 6 | Major | HS | 64 |  not static |
| 89 | Business objects not needed and not nullified | 2 | Minor | BPD | 25 |  TCA-SVC-034 |
| 90 | HHS/CSHS private variables not used | 4 | Major | HS | 1 |  TCA-SVC-009 |
| 91 | Service private variables not used | 4 | Major | INTG | 1 |  TCA-SVC-009 |
| 92 | Large number of private variables for CSHS/HHS | 5 | Major | HS | 1 |  TCA-SVC-033 |
| 93 | Large number of private variables for BPD | 5 | Major | BPD | 25 |  TCA-SVC-033 |
| 94 | Large number of private variables for service | 5 | Major | INTG | 1 |  TCA-SVC-033 |
| 95 | Binding and config option usage without NULL check | 6 | Major | HS | 64 |  TCA-UI-018 |
| 96 | Coach views without binding | 6 | Major | HS | 64 |  TCA-UI-006 |
| 97 | AMD dependencies defined but not used | 3 | Minor | HS | 64 |  TCA-UI-015 |
| 98 | Unused JS Functions | 1 | Minor | JS | 3011 |  not static |
| 99 | Use of Generics (ANY class) | 1 | Minor | Model | 12 |  TCA-BO-006, TCA-QA-011 |
| 100 | Unconnected Boundary Events on a Coach | 6 | Major | HS | 3003 |  not static |
| 101 | Ajax calls without Error Handler defined | 3 | Minor | HS | 64 |  not static |
| 102 | Duplicate CSS class declaration | 1 | Minor | HS | 64 |  not static |
| 103 | Hidden Coach Views in CSS/HHS | 1 | Minor | HS | 3003 |  not static |
| 104 | Coach View without preview HTML/JS Snipped | 2 | Minor | HS | 64 |  TCA-UI-003 |
| 105 | Hard Coded Coach Views labels  | 1 | Minor | HS | 64 |  not static |
| 106 | EPV linked but not used | 3 | Minor | HS | 1 |  TCA-QA-010 |
| 107 | ENV linked but not used | 3 | Minor | HS | 1 |  TCA-APP-042 |
| 108 | Resource Bundle linked but not used | 3 | Minor | HS | 1 |  TCA-QA-010 |
| 109 | Not implemented Boundary Event checked | 1 | Minor | HS | 64 |  not static |
| 110 | Regular Coach View Defined as a template | 2 | Minor | HS | 64 |  not static |
| 111 | Missing JS Null check before method invocation | 4 | Major | JS | 3011 |  TCA-UI-018 |
| 112 | Unused EPV | 1 | Minor | Model | 21 |  TCA-QA-005 |
| 113 | Unused ENV | 1 | Minor | Model | 62 |  TCA-APP-042 |
| 114 | Excessive change handlers | 4 | Major | HS | 64 |  TCA-UI-012 |
| 115 | Global Change handler  | 5 | Major | HS | 64 |  TCA-UI-012 |
| 116 | Tab / Stack used with too many tabs | 7 | Critical | HS | 64 |  not static |
| 117 | Table control heavily loaded with Coach Views | 7 | Critical | HS | 64 |  not static |
| 118 | Combined too many ajax calls in Load handler | 7 | Critical | HS | 64 |  not static |
| 119 | Prototype Event Handler not enabled for multiple usages in a Coach | 5 | Major | HS | 64 |  TCA-UI-014 |
| 120 | Usage of lifecycle change() as event handler | 3 | Minor | HS | 64 |  TCA-UI-013 |
| 121 | Usage of lifecycle view() in Load | 3 | Minor | HS | 64 |  TCA-UI-013 |
| 122 | Conflicting DOM ids - non-usage of $$viewDOMID$$ | 2 | Minor | HS | 64 |  TCA-UI-011 |
| 123 | Combine multiple JS files | 1 | Minor | HS | 64 |  TCA-UI-016 |
| 124 | Combine multiple css files | 1 | Minor | HS | 64 |  TCA-UI-016 |
| 125 | CSS define after JS (js may be dependent on css) | 1 | Minor | HS | 64 |  not static |
| 126 | Large Coach Views with too many controls - (Atomic Coach Views) | 4 | Major | HS | 64 |  TCA-UI-001 |
| 127 | Coach View services defined but not used | 1 | Minor | HS | 64 |  not static |
| 128 | Wrong type of bound data | 1 | Minor | HS | 3003 |  not static |
| 129 | Javascript alert messages | 2 | Minor | HS | 64 |  TCA-JS-008 |
| 130 | Coaches used without templates | 1 | Minor | HS | 3003 |  not static |
| 131 | Mixed usage of responsive and non-responsive coach views | 3 | Minor | HS | 64 |  not static |
| 132 | Coaches without validation handlers | 1 | Minor | HS | 3003 |  not static |
| 133 | Controls event handlers not leveraged | 1 | Minor | HS | 64 |  not static |
| 134 | Unconnected End Points in services | 5 | Major | INTG | 1 |  TCA-SVC-001 |
| 135 | Unconnected End Points in Human Services | 5 | Major | HS | 1 |  TCA-SVC-001 |
| 136 | Unconnected End Points in BPDs | 5 | Major | BPD | 25 |  TCA-BPD-004 |
| 137 | Mixed usage of dojo, jquery and DOM methods | 3 | Minor | HS | 64 |  TCA-UI-017 |
| 138 | Local floating copies of Controls | 1 | Minor | Model | 64 |  TCA-QA-002 |
| 139 | Mixed Brazos  and OOB controls  | 4 | Major | HS | 1 |  not applicable (vendor) |
| 140 | Dead Branches and steps in BPD | 5 | Major | BPD | 25 |  TCA-BPD-004, TCA-BPD-005 |
| 141 | Unconverted HHS to CSHS | 8 | Critical | HS | 1 |  TCA-SVC-019 |
| 142 | Unconverted Services | 8 | Critical | INTG | 1 |  not static |
| 143 | Unconverted BPD to Process | 8 | Critical | BPD | 25 |  not static |
| 144 | Service Steps with Unknown in Name | 5 | Major | Model | 1 |  TCA-SVC-040 |
| 145 | Mix of CSHS and HHS | 3 | Minor | HS | 0 |  TCA-SVC-019 |
| 146 | PO Count Too Large Split into multiple applications/toolkits | 3 | Minor | Model | 0 |  TCA-APP-010..023 |
| 147 | CSHS marked Nested but not used as nested | 4 | Minor | HS | 1 |  not static |
| 148 | Junk CSHS File created but not saved due to WebPD error PO_NAME 64 char | 3 | Minor | Model | 1 |  not static |
| 149 | Junk Managed Asset uploaded but not saved | 3 | Minor | Model | 61 |  TCA-DEP-006 |
| 150 | Empty Server Definitions ECM/REST/WEB SERVICE | 5 | Major | Model | 0 |  not static |
| 151 | Default Labels for Process Steps | 2 | Minor | Model | 1 |  TCA-QA-007 |
| 152 | Too Many Pre Post Defined Service | 3 | Minor | Model | 1 |  TCA-SVC-038 |
| 153 | Link Name Untitled1-n | 1 | Minor | Model | 1 |  TCA-SVC-036 |
| 154 | Deprecated in Process Label Description | 2 | Minor | Model | 1 |  TCA-SVC-040 / TCA-DEP-002 |
| 154 | Recursive Component Link | 10 | Critical | Model | 1 |  TCA-SVC-040 / TCA-DEP-002 |
| 155 | Recursive Activity Link BPD | 10 | Critical | BPD | 25 |  TCA-BPD-005 |
| 156 | Compexity Score Per PO | 7 | Critical | Model | 0 |  TCA-SVC-041 |
| 157 | No Notes in BPD | 1 | Minor | BPD | 25 |  TCA-BPD-031 |
| 158 | No Usage of Resource Bundles | 4 | Major | HS | 50 |  not static |
| 159 | There is no script Content (Empty Script) | 5 | Major | INTG | 3011 |  TCA-SVC-003 |
| 160 | Script Length > 2000 | 5 | Major | INTG | 3011 |  TCA-JS-020 |
| 161 | Pre/Post of Start and Exit Points | 3 | Minor | HS | 1 |  TCA-SVC-037 |
| 162 | Missing CV Localizations | 1 | Minor | HS | 50 |  not static |
| 163 | Too many steps in a BPD | 4 | Major | BPD | 25 |  TCA-BPD-030 |
| 164 | CSS Minification in zip file |  |  |  | 61 |  TCA-APP-043 |
| 165 | js minification in zip file |  |  |  | 61 |  TCA-APP-043 |
| 166 | All User Exposure BPD | 6 | Major | BPD | 25 |  TCA-SVC-039 |
| 167 | Too Many Pre Post Defined BPD |  |  |  | 25 |  TCA-SVC-038 |
| 168 | Missing Error Handling JavaConnector | 6 | Major | INTG | 1 |  TCA-SVC-006 |
| 169 | Unreachable Service Flow (Dead Code) |  |  |  | 1 |  TCA-DEP-003 |
| 170 | Non Ending CSHS |  |  |  | 1 |  TCA-SVC-001 |
| 171 | Validation Errors BPD |  |  |  | 25 |  not static |
| 172 | Modified System Toolkits |  |  |  | 2069 |  not static |
| 173 | Wrong version of system toolkits inside attached toolkits |  |  |  | 2069 |  TCA-APP-002 |
| 174 | Unimplemented Service only start and end |  |  |  | 1 |  TCA-SVC-032 |
| 175 | Unimplemented BPD only start and end |  |  |  | 1 |  TCA-BPD-002 |
| 176 | No steps service start->End |  |  |  | 1 |  TCA-SVC-032 |

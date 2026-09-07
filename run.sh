#!/bin/sh
# Starts TWX Code Analyzer (local web UI). Uses the bundled runtime (jre/) when present, else java on PATH.
cd "$(dirname "$0")"
if [ -x jre/bin/java ]; then JAVA=jre/bin/java; else JAVA=java; fi
exec "$JAVA" -Xmx2g -jar twx-code-analyzer.jar serve "${1:-8765}" "${2:-data}"

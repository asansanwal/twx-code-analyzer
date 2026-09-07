#!/bin/sh
# Builds build/twx-code-analyzer.jar (engine + web app, Java 8 bytecode; Rhino classes shaded in). Needs a JDK 17+ on PATH or in JAVA_HOME.
set -e
cd "$(dirname "$0")"
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac; JAR=${JAVA_HOME:+$JAVA_HOME/bin/}jar
rm -rf build/classes build/twx-code-analyzer.jar; mkdir -p build/classes
$JAVAC --release 8 -Xlint:-options -cp lib/rhino-1.7.15.jar -d build/classes $(find src/main/java -name '*.java')
(cd build/classes && $JAR xf ../../lib/rhino-1.7.15.jar && rm -rf META-INF)
printf 'Manifest-Version: 1.0\nMain-Class: tca.Main\nImplementation-Title: TWX Code Analyzer\nImplementation-Version: 1.1\n' > build/MANIFEST.MF
$JAR cfm build/twx-code-analyzer.jar build/MANIFEST.MF -C build/classes .
echo "built build/twx-code-analyzer.jar ($(du -h build/twx-code-analyzer.jar | cut -f1))"

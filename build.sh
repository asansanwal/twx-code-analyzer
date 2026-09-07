#!/bin/sh
# Builds build/twx-code-analyzer.jar (engine + desktop web app, Java 8 bytecode; Rhino classes shaded in) and the deployable
# web application build/twx-code-analyzer.war (Servlet 4, javax.servlet: WebSphere traditional 9, Liberty javaee-8 / webProfile-8,
# Tomcat 9) plus build/twx-code-analyzer-jakarta.war (Servlet 6, jakarta.servlet: Liberty jakartaee-10, Tomcat 10.1+).
# Needs a JDK 17+ on PATH or in JAVA_HOME. The servlet API jars in lib/ are compile-time only (the container provides them).
set -e
cd "$(dirname "$0")"
VERSION=1.2
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac; JAR=${JAVA_HOME:+$JAVA_HOME/bin/}jar
rm -rf build/classes build/war build/twx-code-analyzer.jar build/twx-code-analyzer.war build/twx-code-analyzer-jakarta.war; mkdir -p build/classes
$JAVAC --release 8 -Xlint:-options -cp lib/rhino-1.7.15.jar -d build/classes $(find src/main/java -name '*.java')
(cd build/classes && $JAR xf ../../lib/rhino-1.7.15.jar && rm -rf META-INF)
printf 'Manifest-Version: 1.0\nMain-Class: tca.Main\nImplementation-Title: TWX Code Analyzer\nImplementation-Version: %s\n' "$VERSION" > build/MANIFEST.MF
$JAR cfm build/twx-code-analyzer.jar build/MANIFEST.MF -C build/classes .
echo "built build/twx-code-analyzer.jar ($(du -h build/twx-code-analyzer.jar | cut -f1))"

# WAR: javax.servlet (Servlet 4) and jakarta.servlet (Servlet 6, same source with the package renamed)
war() { # $1 = suffix ('' | -jakarta), $2 = servlet api jar, $3 = source dir
  W=build/war$1; rm -rf "$W"; mkdir -p "$W/WEB-INF/classes"
  $JAVAC --release 8 -Xlint:-options -cp "build/classes:$2" -d "$W/WEB-INF/classes" $(find "$3" -name '*.java')
  cp -r build/classes/. "$W/WEB-INF/classes/"; cp src/war/webapp/WEB-INF/web.xml "$W/WEB-INF/"; cp -r static/. "$W/"
  printf 'Manifest-Version: 1.0\nImplementation-Title: TWX Code Analyzer\nImplementation-Version: %s\n' "$VERSION" > "$W/MANIFEST.MF"
  $JAR cfm "build/twx-code-analyzer$1.war" "$W/MANIFEST.MF" -C "$W" .
  echo "built build/twx-code-analyzer$1.war ($(du -h build/twx-code-analyzer$1.war | cut -f1))"
}
war '' lib/javax.servlet-api-4.0.1.jar src/war/java
rm -rf build/jakarta-src; mkdir -p build/jakarta-src; for f in $(cd src/war/java && find . -name '*.java'); do mkdir -p "build/jakarta-src/$(dirname $f)"; sed 's/javax\.servlet/jakarta.servlet/g' "src/war/java/$f" > "build/jakarta-src/$f"; done
war -jakarta lib/jakarta.servlet-api-6.0.0.jar build/jakarta-src

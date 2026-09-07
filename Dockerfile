# TWX Code Analyzer as a container: the desktop jar serving on 0.0.0.0:8765, history and rule settings in the /data volume.
#   docker build -t twx-code-analyzer . && docker run -p 8765:8765 -v tca-data:/data twx-code-analyzer
# (build the jar first: JAVA_HOME=<jdk17> ./build.sh). No authentication of its own: put it behind an authenticating reverse proxy.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/twx-code-analyzer.jar /app/
COPY static /app/static
VOLUME /data
EXPOSE 8765
ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app/twx-code-analyzer.jar", "serve", "8765", "/data", "--host", "0.0.0.0", "--no-browser"]

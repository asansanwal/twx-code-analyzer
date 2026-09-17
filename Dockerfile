# TWX Code Analyzer as a container: the desktop jar serving on 0.0.0.0:8765, history and rule settings in the /data volume.
#   docker build -t twx-code-analyzer . && docker run -p 8765:8765 -v tca-data:/data twx-code-analyzer
# (build the jar first: JAVA_HOME=<jdk17> ./build.sh). No authentication of its own: put it behind an authenticating reverse proxy.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --home /app --shell /usr/sbin/nologin tca && mkdir -p /app /data && chown tca:tca /app /data
WORKDIR /app
COPY --chown=tca:tca build/twx-code-analyzer.jar /app/
COPY --chown=tca:tca static /app/static
VOLUME /data
EXPOSE 8765
USER tca
# health: the base image has no curl; bash talks to the port directly
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8765 && printf "GET /api/info HTTP/1.0\r\n\r\n" >&3 && head -1 <&3 | grep -q " 200 "' || exit 1
ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app/twx-code-analyzer.jar", "serve", "8765", "/data", "--host", "0.0.0.0", "--no-browser"]

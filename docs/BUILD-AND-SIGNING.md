# Build, packaging and code signing (hand-over notes for the release builder)

## What is in the repository
* `src/main/java/tca` - engine + local web server (plain Java, compiled with `--release 8`, no build framework).
* `lib/rhino-1.7.15.jar` - Mozilla Rhino (MPL 2.0), shaded into the application jar for the JavaScript syntax checks.
* `static/` - the web UI (own code: `index.html`, `app.js`, `app.css`; third party under `static/vendor` and `static/webfonts`: Bootstrap 5.3.3 (MIT), Font Awesome Free 6.5.2 (CC BY 4.0 / SIL OFL / MIT), Chart.js 4.4.4 (MIT)).
* `lib/javax.servlet-api-4.0.1.jar`, `lib/jakarta.servlet-api-6.0.0.jar` - servlet API, compile-time only for the WAR builds (the container provides the runtime classes; not shipped).
* `build.sh` - compiles and builds `build/twx-code-analyzer.jar` (Main-Class `tca.Main`), `build/twx-code-analyzer.war` (javax.servlet) and `build/twx-code-analyzer-jakarta.war` (jakarta.servlet, generated from the same servlet source by renaming the packages).
* `package.sh` - builds `dist/twx-code-analyzer-<version>-linux-x64.tar.gz` and `-windows-x64.zip` (jar + static + docs + `run.sh`/`run.bat` + trimmed runtime made with jlink).
* `run.sh` / `run.bat` - launchers: start the local server (127.0.0.1 only) and open the browser.

## Reproducible build
1. JDK 17 (Temurin recommended; the produced bytecode is Java 8 so the jar also runs on the WebSphere/BAW Java 8 runtime).
2. `JAVA_HOME=<linux jdk> ./build.sh` -> `build/twx-code-analyzer.jar`, `build/twx-code-analyzer.war`, `build/twx-code-analyzer-jakarta.war`.
3. `VERSION=1.2 JAVA_HOME=<linux jdk> WIN_JDK=<extracted windows jdk of the same major version> ./package.sh` -> `dist/` (desktop archives and the two WAR files).
   The Windows runtime image is produced by cross jlink from the Windows JDK's `jmods` (no Windows machine needed for the zip package).
4. Record the SHA-256 of every artifact (`sha256sum dist/*`) and tag the commit `v<version>`.

## Windows: signed native launcher (EV certificate)
A `.bat` file cannot be Authenticode-signed; ship a native launcher and sign it. Recommended path on a Windows build machine with JDK 17 (`jpackage`):
```
jpackage --type app-image --name "TWX Code Analyzer" --app-version 1.0 --vendor "<vendor>" ^
  --input build --main-jar twx-code-analyzer.jar --main-class tca.Main --arguments "serve 8765 data" ^
  --runtime-image dist\twx-code-analyzer-1.0-windows-x64\jre --dest dist\win-app
```
Copy `static\` next to the launcher (the server looks for `static` in the jar directory or its parent; alternatively pass `--java-options -Dtca.static=<path>`).
Sign with signtool and the EV certificate (hardware token / cloud HSM), timestamped:
1. every `.exe` and `.dll` of the app image (`TWX Code Analyzer.exe`, `jre\bin\*.exe`, `jre\bin\*.dll`): `signtool sign /tr http://timestamp.digicert.com /td sha256 /fd sha256 /a <file>`;
2. the installer built from the signed app image (`jpackage --type msi` or `--type exe`), signed the same way;
3. optionally `jarsigner` for `twx-code-analyzer.jar` if the policy requires signed jars (Authenticode does not cover jars).
Verify with `signtool verify /pa /v <file>` and by installing on a clean Windows machine (SmartScreen must show the verified publisher).

## Linux
`tar.gz` + detached GPG signature (`gpg --armor --detach-sign`) and SHA-256 sums; an AppImage or deb can be produced from the same directory layout (`run.sh` is the entry point).

## Versioning
`VERSION` in `build.sh` (jar and WAR manifests), `VERSION` in `package.sh` and `Analyzer.VERSION` (reported as `engineVersion` in every report) must match the git tag.

## Security notes for the release
* The server binds to 127.0.0.1 only; there is no authentication (single-user desktop tool). The web deployment (later) must sit behind a reverse proxy with authentication.
* Uploaded TWX files and reports are stored under `data/` next to the launcher (the history); nothing is sent to the network.
* Third-party components and licences: see the list above, `lib/` and `static/vendor/`.

# Deployable web application

The desktop package (jar + `run.sh` / `run.bat`) and the BAW embedding stay as they are. This document describes how to deploy the web application archive (WAR) on a server and the container image built from the desktop jar. The features of the WAR (accounts, workspaces, policies, gates, repositories, API, help) and all its configuration options are documented in [WEB-APPLICATION.md](WEB-APPLICATION.md); the options below are the ones every deployment needs.

## Artifacts

| File | Servlet API | Runs on |
|---|---|---|
| `twx-code-analyzer-<version>.war` | Servlet 4.0, `javax.servlet` | WebSphere Application Server traditional 9.0, WebSphere Liberty with `javaee-8.0` / `webProfile-8.0` / `servlet-4.0`, Tomcat 9 |
| `twx-code-analyzer-<version>-jakarta.war` | Servlet 6.0, `jakarta.servlet` | WebSphere Liberty with `jakartaee-10.0` / `webProfile-10.0` / `servlet-6.0`, Open Liberty, Tomcat 10.1 and later |

Both are built by `build.sh` (`build/twx-code-analyzer.war`, `build/twx-code-analyzer-jakarta.war`) and copied to `dist/` by `package.sh`. The WAR contains the engine classes (Java 8 bytecode, Rhino shaded in), the static UI and `WEB-INF/web.xml`; it needs nothing but a Java 8 or newer runtime and the container.

## Configuration

Every option is resolved in this order: servlet init parameter or context parameter (`web.xml`), JVM system property, environment variable, default.

| Option | Property / variable | Default | Meaning |
|---|---|---|---|
| `dataDir` | `tca.data` / `TCA_DATA` | `<java.io.tmpdir>/twx-code-analyzer` | Directory for uploaded TWX files, reports, metadata and rule settings. Give it a persistent, backed-up location with space for the exports you keep (each analysis stores the uploaded file). |
| `workspaces` | `tca.workspaces` / `TCA_WORKSPACES` | `true` | Private workspace per browser (below). `false` = one shared history and one shared rule policy for everyone, for a team behind single sign-on. |
| `retentionDays` | `tca.retentionDays` / `TCA_RETENTION_DAYS` | `0` (keep) | Delete analyses older than this many days (checked once an hour); workspaces without analyses whose settings are as old are removed too. |
| `settingsReadOnly` | `tca.settingsReadOnly` / `TCA_SETTINGS_READONLY` | `false` | Demo mode: the Settings page shows and exports the rule settings but Save, Import and Reset are disabled (the API answers 403). |

The desktop jar takes the same options on the command line: `serve 8765 data --host 0.0.0.0 --workspaces --retention-days 30 --read-only-settings`.

## Workspaces, deletion and retention

With workspaces on, the first request of a browser receives a random 128-bit token in the cookie `tca_ws` (HttpOnly, SameSite=Lax, Secure behind HTTPS, one year). Everything that browser does is stored under `<dataDir>/ws/<token>/`: its analyses (`<id>/upload.twx`, `report.json`, `meta.json`) and its rule settings (`settings.json`). Another browser, or a request with a guessed report id or a forged token, gets an empty history and 404 for the reports; a browser that loses its cookie loses access to its workspace (the files then age out through the retention). No directory is created for a visitor that never uploads or saves anything.

Users delete their uploads themselves: the trash button in History (and on the home page) and the Delete button on a report remove the uploaded TWX, the report and the metadata from disk immediately and permanently (`DELETE /api/report/<id>`). The Analyze page states this policy and the retention period to the user (`GET /api/info` carries the values).

## WebSphere Liberty / Open Liberty

`server.xml`:

```xml
<featureManager><feature>servlet-4.0</feature></featureManager>            <!-- or jakartaee-10.0 with the -jakarta WAR -->
<webApplication id="twx-code-analyzer" location="twx-code-analyzer-1.2.war" contextRoot="/twx-code-analyzer">
    <application-bnd><security-role name="analysts"><group name="bpm-developers"/></security-role></application-bnd>
</webApplication>
```

`jvm.options`: `-Dtca.data=/var/lib/twx-code-analyzer`, `-Dtca.retentionDays=30` and `-Xmx2g` (a 40 MB export needs about 1 GB of heap during analysis).

## WebSphere Application Server traditional

Install the `javax` WAR through the administrative console (Applications > New Application, context root `/twx-code-analyzer`), set the data directory with a generic JVM argument `-Dtca.data=...` on the server, map the security role if you add one (below), then start the application. The WAR is Java 8 bytecode and runs on the WebSphere Java 8 runtime.

## Tomcat

Copy the WAR to `webapps/` (Tomcat 9: `twx-code-analyzer.war`; Tomcat 10.1+: the `-jakarta` WAR renamed to `twx-code-analyzer.war`) and set `CATALINA_OPTS="-Dtca.data=/var/lib/twx-code-analyzer -Xmx2g"`. The application is then available at `http://host:8080/twx-code-analyzer/`.

## Security

The application has no authentication of its own; workspaces keep visitors apart, they do not identify them. TWX exports contain source code and configuration of your applications, so for a private deployment:

* restrict access with the container: a `security-constraint` in `web.xml` mapped to a role (example below), the reverse proxy in front of the server, or the enterprise single sign-on;
* serve it over HTTPS only;
* keep the data directory out of any web-served path and readable by the server user only;
* do not expose the deployment to the internet.

`web.xml` fragment for a role-based constraint (add inside `<web-app>`, map the role in the container):

```xml
<security-constraint>
  <web-resource-collection><web-resource-name>analyzer</web-resource-name><url-pattern>/*</url-pattern></web-resource-collection>
  <auth-constraint><role-name>analysts</role-name></auth-constraint>
  <user-data-constraint><transport-guarantee>CONFIDENTIAL</transport-guarantee></user-data-constraint>
</security-constraint>
<login-config><auth-method>FORM</auth-method></login-config>   <!-- or BASIC, or the container's SSO -->
<security-role><role-name>analysts</role-name></security-role>
```

The engine makes no outbound connections. Uploads are parsed in memory and written to the data directory; nothing is executed from the TWX.

## Container (Docker)

The repository's `Dockerfile` packages the desktop jar as an image that listens on all interfaces inside the container and keeps its data in the `/data` volume:

```
JAVA_HOME=<jdk17> ./build.sh
docker build -t twx-code-analyzer .
docker run -d --name tca -p 127.0.0.1:8765:8765 -v tca-data:/data twx-code-analyzer
```

Publish the port on localhost or behind an authenticating reverse proxy (same security notes as the WAR). The plain jar can do the same without Docker: `java -jar twx-code-analyzer.jar serve 8765 /var/lib/twx-code-analyzer --host 0.0.0.0 --no-browser`.

## Public demo server (nginx in front of Tomcat)

This is the layout of the live demo at [https://twxca.com](https://twxca.com): Tomcat 10.1 with the Jakarta WAR as `ROOT.war`, listening on 127.0.0.1 only, run by a system user through systemd; nginx terminates TLS (certbot) and proxies to it. The demo runs with `demo=true`, `auth=builtin`, `signup=true`, `quotaMb=1024`, `retentionDays=30`, `workers=2` and `baseUrl=https://twxca.com/`, added as its own virtual host next to the other sites of the machine. Uploads are large and analyses take seconds to minutes, so raise the body size and the timeouts:

```
server {
    server_name example.com;
    client_max_body_size 1024m;
    location / {
        proxy_pass http://127.0.0.1:8089;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;    # marks the workspace cookie Secure
        proxy_read_timeout 900; proxy_send_timeout 900;
    }
}
```

Tomcat `server.xml`: `<Connector address="127.0.0.1" port="8089" protocol="HTTP/1.1" .../>` and `<Server port="-1" ...>` (no shutdown port). systemd unit (`/etc/systemd/system/twxca.service`):

```
[Service]
User=twxca
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
Environment=CATALINA_HOME=/opt/twxca/tomcat
Environment="CATALINA_OPTS=-Xms512m -Xmx4g -Dtca.data=/var/lib/twxca -Dtca.demo=true -Dtca.auth=builtin -Dtca.signup=true -Dtca.anonymous=true -Dtca.quotaMb=1024 -Dtca.maxUploadMb=512 -Dtca.retentionDays=30 -Dtca.workers=2 -Dtca.baseUrl=https://twxca.com/"
ExecStart=/opt/twxca/tomcat/bin/catalina.sh run
Restart=on-failure
```

Upgrade: stop the service, replace `webapps/ROOT.war` (remove the exploded `webapps/ROOT` and `work/Catalina`), start it, check `https://<host>/api/info` for the version. Workspaces and settings in the data directory survive the upgrade.

## Differences from the desktop app

| | Desktop | WAR / container |
|---|---|---|
| Users | one, local | many; private workspace per browser (or one shared history with `workspaces=false`) |
| Network | 127.0.0.1 only | the container's listener, protect it |
| Data | `data/` next to the jar | data directory of the deployment (see above), `ws/<token>/` per workspace |
| Rule settings | Settings page, `data/settings.json` | Settings page per workspace (or shared), optionally read-only; export a settings file to move a rule policy between installations |
| Deletion | History page | History page and report header; permanent; optional retention |
| Concurrency | one analysis at a time | analyses are serialised (one at a time), other requests are served meanwhile |

## Verification

The WARs were tested on Tomcat 9.0 (`javax`) and Tomcat 10.1 (`jakarta`) under the context root `/twx-code-analyzer`: upload and analysis, findings and overview, settings (save, export, import, reset, read-only mode), PDF export and the object API, workspace isolation with two browsers and a forged token, permanent deletion checked on disk, and the retention purge, with no browser errors and no failed requests. The UI uses relative URLs only, so any context root works; a request for the root without a trailing slash is redirected.

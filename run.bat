@echo off
rem Starts TWX Code Analyzer (local web UI). Uses the bundled runtime (jre\) when present, else java on PATH.
cd /d "%~dp0"
set JAVA=java
if exist "jre\bin\java.exe" set JAVA=jre\bin\java.exe
"%JAVA%" -Xmx2g -jar twx-code-analyzer.jar serve %1 %2

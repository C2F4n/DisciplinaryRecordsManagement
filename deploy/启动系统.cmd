@echo off
chcp 65001 >nul
setlocal
pushd "%~dp0" || exit /b 1

set "JAVA_EXE=java.exe"
if exist "%ProgramFiles%\Java\jdk-21\bin\java.exe" set "JAVA_EXE=%ProgramFiles%\Java\jdk-21\bin\java.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

set "JAR="
for %%F in ("DisciplinaryRecordsManagement-*.jar") do set "JAR=%%~fF"

if not defined JAR (
  echo [错误] 当前目录找不到 DisciplinaryRecordsManagement-*.jar
  echo        请把本脚本与 JAR 放在同一个文件夹。
  pause
  popd
  exit /b 1
)

if not exist ".env" (
  echo [错误] 当前目录找不到 .env
  echo        请先把 .env.example 复制为 .env，填好 DB_PASSWORD 与 APP_ADMIN_PASSWORD。
  pause
  popd
  exit /b 1
)

if not exist ".https\server.p12" (
  echo [错误] 当前目录找不到 .https\server.p12
  echo        缺少 HTTPS 证书，无法启动。请使用完整部署包，或按 部署教程.md 重新生成。
  pause
  popd
  exit /b 1
)

echo 正在启动，稍后浏览器打开 https://localhost:18643
echo 若 .env 里设置了 APP_PORT，请把地址中的端口换成对应值。
"%JAVA_EXE%" -jar "%JAR%"
set "APP_EXIT=%ERRORLEVEL%"
popd
if not "%APP_EXIT%"=="0" pause
exit /b %APP_EXIT%

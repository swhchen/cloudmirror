@echo off
chcp 65001 >nul
title 云镜推演 - 后端 (8080)
cd /d "%~dp0..\backend"

if not exist "target\cloudmirror.jar" (
  echo [错误] 还没构建出 target\cloudmirror.jar
  echo 请先在 IDEA 里运行 CloudMirrorApplication，或执行：
  echo    mvn -DskipTests package
  pause
  exit /b 1
)

if "%JAVA_HOME%"=="" (
  set "JAVA_HOME=C:\Users\swhc6\.jdks\openjdk-25.0.1"
)

echo 启动后端 http://127.0.0.1:8080  （默认连接 MySQL）
echo 健康检查 http://127.0.0.1:8080/api/health
"%JAVA_HOME%\bin\java.exe" -jar target\cloudmirror.jar
pause

@echo off
chcp 65001 >nul
title 云镜推演 - 后端 H2 模式 (8080)
cd /d "%~dp0..\backend"

if not exist "target\cloudmirror.jar" (
  echo [错误] 还没构建出 target\cloudmirror.jar，请先构建
  pause
  exit /b 1
)

if "%JAVA_HOME%"=="" (
  set "JAVA_HOME=C:\Users\swhc6\.jdks\openjdk-25.0.1"
)

echo 启动后端 H2 模式（不需要 MySQL），数据文件在 backend\data\
"%JAVA_HOME%\bin\java.exe" -jar target\cloudmirror.jar --spring.profiles.active=h2
pause

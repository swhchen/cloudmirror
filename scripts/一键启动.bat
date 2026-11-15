@echo off
chcp 65001 >nul
title 云镜推演 - 一键启动
echo 启动后端（H2 模式，不需要 MySQL）...
start "云镜推演-后端" cmd /k "%~dp0启动后端-H2.bat"

echo 等待后端就绪...
timeout /t 6 /nobreak >nul

echo 启动前端演示服务...
start "云镜推演-前端" cmd /k "%~dp0启动前端-演示.bat"

echo.
echo 两个窗口启动后，浏览器打开： http://localhost:4173
echo 接口文档： http://127.0.0.1:8080/swagger-ui.html
timeout /t 8 /nobreak >nul

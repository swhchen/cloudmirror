@echo off
chcp 65001 >nul
title 云镜推演 - 前端演示 (4173)
cd /d "%~dp0..\frontend"

if not exist "node_modules" (
  echo 首次运行，正在安装依赖...
  call npm install
)

echo 正在构建前端...
call npm run build
if errorlevel 1 (
  echo [错误] 构建失败，请把上面的报错发我
  pause
  exit /b 1
)

echo.
echo 启动演示服务 http://localhost:4173
echo 关闭本窗口即停止
call npm run preview
pause

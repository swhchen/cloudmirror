@echo off
chcp 65001 >nul
title 云镜推演 - 前端 (5173)
cd /d "%~dp0..\frontend"

if not exist "node_modules" (
  echo 首次运行，正在安装依赖...
  call npm install
)

echo 启动前端 http://127.0.0.1:5173
call npm run dev
pause

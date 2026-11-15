@echo off
chcp 65001 >nul
title 云镜推演 - AI 服务 (8010)
cd /d "%~dp0..\ai-service"

echo AI 服务 http://127.0.0.1:8010
echo 未配置 LLM_API_KEY 时自动使用规则诊断（不会报错）
python -m uvicorn app.main:app --host 127.0.0.1 --port 8010
pause

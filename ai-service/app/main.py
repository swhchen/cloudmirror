"""云镜推演 · AI 辅助诊断服务

- 配了 LLM_API_KEY：走 OpenAI 兼容协议（DeepSeek / 豆包都支持）
- 没配 Key 或调用失败：自动降级为本地规则诊断，service 不会挂

启动：uvicorn app.main:app --port 8010
"""
import os
from typing import List, Optional

import httpx
from fastapi import FastAPI
from pydantic import BaseModel

LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com").rstrip("/")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

app = FastAPI(title="云镜推演 AI 服务", version="0.1.0")


class FaultItem(BaseModel):
    nodeName: str
    faultType: str
    severity: str


class AffectedItem(BaseModel):
    nodeName: str
    impactLevel: str
    distance: int
    path: str = ""


class TopoNode(BaseModel):
    name: str
    kind: str


class DiagnoseReq(BaseModel):
    projectName: str = ""
    faults: List[FaultItem] = []
    affected: List[AffectedItem] = []
    topology: List[TopoNode] = []
    nodeCount: int = 0
    edgeCount: int = 0


@app.get("/ai/health")
def health():
    return {"status": "ok", "llm": "configured" if LLM_API_KEY else "fallback"}


@app.post("/ai/diagnose")
async def diagnose(req: DiagnoseReq):
    if LLM_API_KEY:
        try:
            content = await call_llm(req)
            return {"source": "llm", "content": content}
        except Exception as exc:  # 网络/密钥/额度问题都降级
            return {"source": "fallback", "content": rule_based(req), "error": str(exc)}
    return {"source": "fallback", "content": rule_based(req)}


async def call_llm(req: DiagnoseReq) -> str:
    prompt = build_prompt(req)
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{LLM_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {LLM_API_KEY}"},
            json={
                "model": LLM_MODEL,
                "temperature": 0.2,
                "messages": [{"role": "user", "content": prompt}],
            },
        )
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"]


def build_prompt(req: DiagnoseReq) -> str:
    faults = "\n".join(
        f"- {f.nodeName}：{f.faultType}（{f.severity}）" for f in req.faults
    ) or "- 无"
    affected = "\n".join(
        f"- {a.nodeName}：{a.impactLevel}，距离 {a.distance}，路径 {a.path}"
        for a in req.affected
    ) or "- 无其它节点受影响"
    topology = "、".join(f"{n.name}({n.kind})" for n in req.topology)

    return f"""你是云原生故障演练专家。请基于下面的装配拓扑与推演结果，输出一份中文诊断。

项目：{req.projectName}
节点数：{req.nodeCount}，连线数：{req.edgeCount}
拓扑：{topology}

当前故障：
{faults}

传播分析结果：
{affected}

请按以下结构输出（不要编造拓扑里没有的节点）：
1. 根因判断（一句话）
2. 影响范围（直接/间接，关键路径）
3. 处置建议（按优先级列 2-4 条，说明理由）
4. 风险提示（这个结论依赖哪些假设）"""


def rule_based(req: DiagnoseReq) -> str:
    if not req.faults:
        return (
            "【根因判断】当前没有激活故障，无法做故障诊断。\n"
            "【影响范围】无。\n"
            "【处置建议】先注入一个故障再推演，或检查是否符合预期。\n"
            "【风险提示】本结论来自规则判断，不是真实探测。"
        )

    fault_lines = "、".join(f"{f.nodeName}（{f.faultType}/{f.severity}）" for f in req.faults)
    direct = [a for a in req.affected if a.impactLevel == "DIRECT"]
    indirect = [a for a in req.affected if a.impactLevel != "DIRECT"]

    impact = "无其它节点受影响"
    if req.affected:
        parts = []
        if direct:
            parts.append("直接受影响：" + "、".join(a.nodeName for a in direct))
        if indirect:
            parts.append("间接受影响：" + "、".join(a.nodeName for a in indirect))
        deepest = max(req.affected, key=lambda a: a.distance)
        parts.append(f"最长传播链：{deepest.path or deepest.nodeName}（{deepest.distance} 跳）")
        impact = "\n".join(parts)

    return f"""【根因判断】{fault_lines} 发生异常，沿「被依赖 → 依赖方」方向扩散。

【影响范围】
{impact}

【处置建议】
1. 优先检查该节点是否有显式主备冗余组：有则切备，通常恢复最快。
2. 无冗余时先尝试重启（仅适用于宕机/服务不可用类故障）。
3. 若故障持续扩散且隔离不会切断全部用户入口，可先隔离止损。

【风险提示】以上结论来自规则推演模型（非真实探测），执行前请结合监控复核。"""

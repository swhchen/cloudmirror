# -*- coding: utf-8 -*-
"""修订 PPT：备份原文件、修正错误文案、更新与当前系统不一致的表述、补充演讲备注。"""
import os
import shutil
from pptx import Presentation

SRC = r"C:\Users\swhc6\Desktop\华为杯\云镜推演最终版.pptx"
BAK_DIR = r"C:\Users\swhc6\Desktop\华为杯\原始备份"
DST = r"C:\Users\swhc6\Desktop\华为杯\云镜推演最终版-修订.pptx"

os.makedirs(BAK_DIR, exist_ok=True)
shutil.copy2(SRC, os.path.join(BAK_DIR, "云镜推演最终版-原始.pptx"))

prs = Presentation(SRC)

# 文本替换表：旧片段 -> 新片段（整段替换时保留第一段 run 的格式）
REPLACE = {
    "而北故障影响分析与决策": "而非故障影响分析与决策",
    "缺乏侍播分析与恢复评价": "缺乏传播分析与恢复评价",
    "所需资源开梢": "所需资源开销",
    "避俛无效恢复方案": "避免无效恢复方案",
    "容器、服务发现、目志与运行环境支撑，保障平台稳定执行":
        "MySQL 8 / H2、Docker Compose 与运行环境支撑，保障平台稳定执行",
    "创新点一：服务依赖拓扑驱动的故障传播分析":
        "创新点一：装配预检 + 容量感知的故障传播分析",
    "基于有向图与多源BFS算法，精准识别故障影响范围，追踪传播路径":
        "先用 12 条规则判断能否运行，再用多源 BFS + 容量感知 + 依赖冗余度计算影响范围与路径",
    "核心业务流程：八步推演闭环": "核心业务流程：六步推演闭环",
    "一次完整推演由八步组成，每一步都产生可保存的中间结果；任何一次推演都可以被完整重现":
        "装配 → 校验 → 注入 → 推演 → 恢复 → 复盘；每一步都产生可保存的中间结果，任何一次推演都可完整重现",
    "元数据接入": "装配建模",
    "数据注入": "可运行性校验",
    "构建知识图谱": "故障注入",
    "多源知识融合": "传播推演",
    "规则配置与验证": "恢复决策",
    "方案优化": "复盘沉淀",
    "结果呈现": "报告导出",
    "接入基础数据与元数据": "节点、端口、部署与依赖",
    "注入业务数据与模型参数": "12 条规则判断能否运行",
    "构建实体关系与知识网络": "选择节点注入故障",
    "融合多源知识与规则": "容量感知 + 时间窗传播",
    "配置规则并验证一致性": "三策略五维评分与级联",
    "呈现推演结果与路径": "结果落库、可回看",
    "输出持续可执行策略方案": "导出报告与复现",
}


def replace_in_frame(frame):
    changed = False
    for para in frame.paragraphs:
        text = "".join(r.text for r in para.runs)
        if not text:
            continue
        new = text
        for old, rep in REPLACE.items():
            if old in new:
                new = new.replace(old, rep)
        if new != text and para.runs:
            para.runs[0].text = new
            for r in para.runs[1:]:
                r.text = ""
            changed = True
    return changed


for slide in prs.slides:
    for shape in slide.shapes:
        if shape.has_text_frame:
            replace_in_frame(shape.text_frame)
        if shape.has_table:
            for row in shape.table.rows:
                for cell in row.cells:
                    replace_in_frame(cell.text_frame)

# 演讲备注（按页序）
NOTES = [
    "开场：一句话定位——把故障关进演练环境，把恢复变成可验证的决策。停 1 秒再翻页。",
    "导览：五部分，今天重点第三、第四部分；控制在 8 分钟。",
    "一句带过：先说为什么做。",
    "三个痛点：故障会扩散、影响范围靠经验、恢复方案缺量化。每个痛点停半秒。",
    "一句带过。",
    "政策只讲对应关系，不逐字念：安全、AI 融合、数字基础设施韧性。",
    "语气加重：接下来是重点，四个创新点。",
    "创新一：先装配预检（12 条规则），再传播推演（端口级、容量感知、冗余度）。承认 BFS 成熟，创新在组合与粒度。",
    "创新二：五维评分；重点讲隔离业务分×0.6，避免靠速度取胜。实测隔离从 91 降到 75，推荐回到重启。",
    "创新三：备用不是有就能切，要校验状态/资源/优先级；再讲级联：接管后 1.2/1.0 超 20% 被拖垮。",
    "创新四：闭环 + 可复现；AI 只解释不判定，断网也能用。",
    "一句带过。",
    "竞品：K8s 编排、Prometheus 监控、Chaos Mesh 注入、Gremlin 平台；我们补“决策”这一段，不是替代。",
    "架构：Vue 3 自绘 SVG、Spring Boot 3、FastAPI 独立部署、MySQL/H2；AI 挂了不影响主流程。",
    "流程已改为六步：装配、校验、注入、推演、恢复、复盘。每一步都有输入输出，可完整重现。",
    "切浏览器，按演示脚本走：校验→注入→推演→恢复→增强→AI→历史。",
    "空白页：若无截图直接跳过，不要停留。",
    "空白页：建议放校验清单与恢复对比两张截图。",
    "应用场景：高校实验、变更前评估、故障复盘训练；目前实验室内部试用。",
    "收尾：接真实监控、K8s 导入、多目标比较、报告导出；从能推演到能指导真实恢复。停两秒再鞠躬。",
]
for i, slide in enumerate(prs.slides):
    if i < len(NOTES):
        slide.notes_slide.notes_text_frame.text = NOTES[i]

prs.save(DST)
print("saved", DST, "slides", len(prs.slides))

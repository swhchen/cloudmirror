# -*- coding: utf-8 -*-
"""二次微调：修正第 8 页标题过长、第 15 页 8 个步骤与新流程不一致的问题。"""
from pptx import Presentation

PATH = r"C:\Users\swhc6\Desktop\华为杯\云镜推演最终版-修订.pptx"

REPLACE = {
    "创新点一：装配预检 + 容量感知的故障传播分析": "创新点一：装配预检 + 容量感知传播",
    "先用 12 条规则判断能否运行，再用多源 BFS + 容量感知 + 依赖冗余度计算影响范围与路径":
        "先用 12 条规则判断能否运行，再用容量感知 BFS 计算影响范围",
    "核心创新：将BFS算法与服务依赖关系深度结合，应用于云原生故障场景":
        "核心创新：装配预检 + 容量感知 + 冗余度，回答影响谁、多严重",
    "容量感知 + 时间窗传播": "容量感知 + 时间窗",
    "调用API": "多动作序列",
    "执行知识推演与推理计算": "先切备再重启的顺序",
    "报告导出": "级联风险",
    "备用接管过载判定": "备用接管过载判定",
    "结果落库、可回看": "备用接管过载判定",
    "导出报告与复现": "结果落库、可回看",
}

prs = Presentation(PATH)
changed = 0
for slide in prs.slides:
    for shape in slide.shapes:
        if not shape.has_text_frame:
            continue
        for para in shape.text_frame.paragraphs:
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
                changed += 1
prs.save(PATH)
print("changed", changed)

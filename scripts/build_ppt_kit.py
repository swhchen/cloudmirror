# -*- coding: utf-8 -*-
"""生成《云镜推演 · 答辩 PPT 素材包》Markdown + Word 两版。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

DOCX = r"C:\Users\swhc6\Desktop\华为杯\云镜推演-PPT素材包与20页大纲.docx"
MD = r"C:\Users\swhc6\Desktop\AI编程专属文件\云镜推演\docs\PPT事实包与20页大纲.md"

doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Cm(21), Cm(29.7)
sec.left_margin = sec.right_margin = Cm(2.2)
sec.top_margin = sec.bottom_margin = Cm(2.0)
normal = doc.styles["Normal"]
normal.font.name = "Times New Roman"
normal._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
normal.font.size = Pt(10.5)
normal.paragraph_format.line_spacing = 1.4
normal.paragraph_format.space_after = Pt(3)

md = []


def set_font(run, size=10.5, bold=False, color=None, east="宋体"):
    run.font.name = "Times New Roman"
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), east)
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor(*color)


def H(text, level=1):
    sizes = {0: 20, 1: 15, 2: 12.5, 3: 11}
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(12 if level <= 1 else 8)
    p.paragraph_format.space_after = Pt(4)
    if level == 0:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_font(r, sizes.get(level, 11), True, (0x1F, 0x3A, 0x5F) if level else (0x0B, 0x12, 0x20), "黑体")
    if level > 0:
        md.append("#" * level + " " + text + "\n")


def P(text, bold=False, indent=False, size=10.5, color=None):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(21) if indent else None
    r = p.add_run(text)
    set_font(r, size, bold, color)
    md.append(text + "\n")


def B(text):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.line_spacing = 1.35
    r = p.add_run(text)
    set_font(r, 10.5)
    md.append("- " + text + "\n")


def shade(cell, hex_color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), hex_color)
    tcPr.append(shd)


def T(headers, rows, widths=None, size=9):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, x in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(x)
        set_font(r, size + 0.5, True)
        shade(hdr[i], "E8EEF6")
    for row in rows:
        cells = t.add_row().cells
        for i, x in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(str(x))
            set_font(r, size)
    if widths:
        total = sum(widths)
        scale = min(1.0, 16.2 / total) if total else 1.0
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w * scale)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    # markdown 表
    md.append("| " + " | ".join(headers) + " |")
    md.append("| " + " | ".join(["---"] * len(headers)) + " |")
    for row in rows:
        md.append("| " + " | ".join(str(x).replace("\n", " / ") for x in row) + " |")
    md.append("")


# ================= 封面 =================
doc.add_paragraph().paragraph_format.space_after = Pt(50)
H("云镜推演", 0)
H("答辩 PPT 素材包与 20 页大纲", 0)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("事实包 · 逐页要点 · 口播稿 · 提示词模板")
set_font(r, 11.5, False, (0x33, 0x41, 0x55))
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("v0.9.1 · 2026-11 · 云启先锋小队")
set_font(r, 10, False, (0x55, 0x65, 0x77))
md.insert(0, "# 云镜推演 · 答辩 PPT 素材包与 20 页大纲\n\n> v0.9.1 · 2026-11 · 云启先锋小队\n")
doc.add_page_break()

# ================= 使用说明 =================
H("零、怎么用这份文档", 1)
P("这份文档分两部分：前半是「事实包」，所有数字与结论都在这里，PPT 只能引用它；后半是「20 页大纲」，每页给了标题、结论句、三个要点、关键数字、配图建议和口播稿。", indent=True)
P("给 GPT 的正确顺序是三步：先喂事实包 → 再喂 20 页大纲 → 最后让它按「视觉规范」写每页排版建议。一次让它从零生成 20 页，一定会水。", indent=True)
P("如果只想要现成的，直接看第 2 节，每页照着做即可。", indent=True)

# ================= 1 事实包 =================
doc.add_page_break()
H("一、事实包（唯一可引用来源）", 1)

H("1.1 一句话定位", 2)
P("云镜推演是面向云原生系统的故障推演与恢复决策沙盘：先把系统装配出来判断能不能跑，再注入故障推演会波及谁、多久到，最后比较恢复方案，回答先救谁、救完会不会更糟。", indent=True)
P("边界：不接入生产监控、不执行真实故障注入；恢复时间/成本/风险为模型估算；基础算法（BFS、割点、加权评分）为成熟方法，创新在建模粒度、组合方式与决策表达。", indent=True)

H("1.2 技术栈", 2)
T(["层", "选型", "说明"], [
    ["前端", "Vue 3 + Vite，自绘 SVG 无限画布", "不依赖 Vue Flow / AntV G6，端口分边、传播流动可定制"],
    ["后端", "Spring Boot 3.3.5 + MyBatis-Plus + Java 17", "不用 Lombok；统一响应、traceId、错误码"],
    ["数据库", "MySQL 8，本地可切 H2", "17 张表；H2 免安装演示"],
    ["AI", "Python 3.11 + FastAPI，OpenAI 兼容", "三层降级：大模型 → AI 规则 → 本地规则"],
    ["部署", "Docker Compose（mysql/ai/backend/frontend）", "已写配置，未在本机 Docker 实测"],
], widths=[2.4, 6.6, 7.2])

H("1.3 数据模型", 2)
T(["分组", "内容"], [
    ["装配", "assembly_node、node_port、assembly_edge（边保存两端端口 ID）"],
    ["工程", "project（多工程，无登录）"],
    ["型号", "device_model（16 个内置型号）"],
    ["故障", "fault_event（类型、严重度、状态）"],
    ["校验快照", "asm_check_run、asm_check_item"],
    ["推演快照", "propagation_analysis、analysis_fault_source、analysis_affected_node、analysis_path_step"],
    ["恢复快照", "recovery_plan、recovery_strategy"],
    ["冗余", "redundancy_group、redundancy_member"],
], widths=[3.2, 13.0])

H("1.4 算法与公式", 2)
P("传播（E1）：多源反向 BFS，O(V+E)；强度 = 边权累乘 × 容量放大 × 依赖冗余度。", indent=True)
P("容量放大 = 1 + 0.5 × 宿主 CPU 利用率（最高 1.5 倍）。", indent=True)
P("依赖冗余度 r = 受影响下游依赖数 / 总下游依赖数；强度 × r，阈值 0.6 判「必崩」，否则「可能」。", indent=True)
P("时间窗：到达时间 = 路径上 latency_ms 之和，可只算 T 秒内的波及范围。", indent=True)
P("恢复评分 = 100 × (0.25×时间分 + 0.20×影响分 + 0.15×成本分 + 0.20×风险分 + 0.20×业务恢复分)；时间分 = max(0, 1−耗时/600s)。", indent=True)
P("隔离折扣：隔离不恢复业务，业务恢复分 ×0.6，避免靠「快」永远第一。", indent=True)
P("级联：容量 = cpuCores 或 cpuRequest；基线负载 = 0.6×容量；故障负载转移给优先级最高的备用；接管后负载 > 容量即过载，最多 6 轮。", indent=True)

H("1.5 十二条校验规则", 2)
T(["编码", "规则", "级别", "判定"], [
    ["R01", "端口冲突", "错误", "同节点 (协议,端口) 重复；同宿主 public 宿主端口重复"],
    ["R02", "端口越界", "错误", "端口不在 1–65535"],
    ["R03", "端口映射冲突", "错误", "port_map 目标非宿主机，或宿主端口冲突"],
    ["R04", "CPU 超配", "错误", "宿主上 cpuRequest 之和 > cpuCores"],
    ["R05", "内存超配", "错误", "memRequest 之和 > memTotalMb"],
    ["R06", "磁盘超配", "错误", "diskGb 之和 > 宿主磁盘"],
    ["R07", "依赖缺失", "错误", "接口级依赖缺端口；服务沿依赖链到不了中间件/对象存储"],
    ["R08", "网络不可达", "错误", "跨宿主调用无网络路径（外部系统、未部署节点除外）"],
    ["R09", "循环依赖", "错误", "depends_on 有环"],
    ["R10", "入口不可达", "警告/错误", "无入口时警告；有入口但服务不可达时报错"],
    ["R11", "孤儿节点", "提示", "节点没连任何线"],
    ["R12", "主备不完整", "警告", "缺主/缺备/端口不一致/备用不可用"],
], widths=[1.4, 2.6, 2.0, 10.0])

H("1.6 测试与性能", 2)
T(["项", "数值"], [
    ["后端测试", "68 个全绿（规则 19 / 传播 12 / 恢复 16 / 高级分析 7 / 级联 4 / 接口 8 / 数据一致性 2 / 性能 1）"],
    ["UI 端到端", "23 步全过（主链路 14 步 + 进阶交互 9 步，CDP 真实点击）"],
    ["校验性能", "500 节点 / 1000 边，全量 12 条规则约 1.6 ms"],
    ["传播复杂度", "O(V+E)；时间窗最坏 O((V+E)logV)"],
    ["接口规模", "13 个控制器、32 个接口"],
    ["节点类型", "11 类；连线 5 类；表 17 张"],
], widths=[3.4, 13.0])

H("1.7 演示拓扑与真实结果", 2)
P("测试工程「测试-电商全量26节点」：26 个节点、48 条连线、2 个冗余组，校验 100 分可运行。", indent=True)
T(["场景", "结果"], [
    ["MySQL 主 node_down", "波及 4 个节点（直接 1 / 间接 3）；支付服务场景下 Ingress 强度 0.2 判可能，网关 1.0 判必崩"],
    ["支付服务不可用", "波及 4 个节点，影响比例 15.4%"],
    ["恢复对比", "备用切换 79.08 ＞ 服务重启 77.83 ＞ 隔离 58.43（隔离不适用）"],
    ["级联", "MySQL-备接管后 4.8 / 4.0，超 20%，1 个节点被拖垮"],
    ["基线校验", "26 节点 / 48 连线：可运行，100 分，0 错 0 警"],
], widths=[5.0, 11.4])

H("1.8 可引用数字清单（PPT 只能用这些）", 2)
for x in [
    "12 条校验规则（R01–R12）",
    "11 类节点、5 类边、17 张表、32 个接口、13 个控制器",
    "26 节点 / 48 连线 / 2 冗余组，100 分可运行",
    "500 节点 / 1000 边，校验 1.6 ms",
    "68 个后端测试 + 23 步 UI 端到端",
    "恢复评分：备用切换 79.08、服务重启 77.83、隔离 58.43（不适用）",
    "级联：4.8 / 4.0，超 20%，1 个节点被拖垮",
    "五维权重 25/20/15/20/20",
    "必崩阈值 0.6；容量放大最高 1.5 倍；级联最多 6 轮",
    "AI 三层降级，断网也能出结论",
]:
    B(x)

H("1.9 禁止编造", 2)
P("以下内容不要出现在 PPT 里，除非另有证据：真实企业用户数量、准确率百分比、专利号、论文录用、生产环境部署规模、未实测的 Docker 结果。", indent=True)

# ================= 2 大纲 =================
doc.add_page_break()
H("二、20 页大纲（每页：标题 / 结论 / 要点 / 数字 / 配图 / 口播）", 1)

IMG_DIR = r"C:\Users\swhc6\Desktop\AI编程专属文件\云镜推演\docs\screenshots"
PAGE_IMG = {
    1: ("scenario-26.png", "26 节点 / 48 连线拓扑，校验 100 分"),
    4: ("ppt-analysis.png", "故障传播：路径、强度、冗余度、到达时间"),
    8: ("ppt-validate.png", "12 条规则的问题清单与修复建议"),
    9: ("ppt-recovery.png", "三策略同屏对比：79.08 / 77.83 / 58.43"),
    10: ("ppt-insights.png", "级联风险：MySQL-备 4.8/4.0 被拖垮"),
    11: ("ppt-ai.png", "AI 诊断：只做解释，不参与判定"),
    15: ("ppt-history.png", "历史回看：每次推演都落库"),
    16: ("ppt-analysis.png", "现场演示：推演结果面板"),
    17: ("ppt-validate.png", "现场演示：校验问题清单"),
    18: ("ppt-recovery.png", "现场演示：恢复方案对比"),
}
PAGES = [
    dict(no=1, title="封面：云镜推演", sub="把故障关进演练环境，把恢复变成可验证的决策",
         points=["副标题：面向云原生系统的故障传播分析与恢复决策平台", "一行身份：云启先锋小队 · 科技发明制作 B 类", "一行数据：26 节点 / 48 连线 / 12 条规则"],
         data="26 节点 · 48 连线 · 12 规则", visual="画布全屏截图（scenario-26.png），深色底",
         script="各位老师好，我们的作品叫云镜推演，一句话：把故障关进演练环境，把恢复变成可验证的决策。",
         tip="停 1 秒再翻页；不要念团队名单"),
    dict(no=2, title="汇报导览", sub="五部分，重点在第三、第四部分",
         points=["为什么做 → 怎么做 → 创新在哪 → 比别人强在哪 → 用在哪", "时间分配：背景 2 分钟、创新 3 分钟、演示 2 分钟、收尾 1 分钟"],
         data="8 分钟", visual="五段式导览条，当前段高亮",
         script="今天分五部分，重点讲第三部分创新和第四部分的真实演示。",
         tip="快速过，不要逐条念"),
    dict(no=3, title="01 项目背景与定位", sub="章节页",
         points=["从「建系统」到「建韧性」", "故障推演是韧性建设的前置动作"],
         data="", visual="大号 01 + 一句话", script="先说为什么做。", tip="一句话带过"),
    dict(no=4, title="三个现实痛点", sub="故障会扩散、影响范围靠经验、恢复缺量化",
         points=["扩散：26 节点拓扑里，一个数据库故障波及 4 个节点、影响 15.4%", "判不清：哪些业务会受影响，主要靠人工经验", "选不准：重启/切备/隔离各有代价，缺少量化比较"],
         data="波及 4 个节点 / 15.4%", visual="三张痛点卡片 + 一张传播路径小图",
         script="云原生系统有三个现实问题：故障会顺着调用链扩散，影响范围主要靠经验判断，恢复动作缺少量化依据。我们的目标是把这三件事变成可计算、可比较的。",
         tip="每个痛点停半秒，让评委跟上"),
    dict(no=5, title="02 政策与技术方案", sub="章节页",
         points=["统筹发展和安全", "人工智能+ 与产业融合", "数字基础设施韧性"],
         data="", visual="大号 02", script="第二部分，政策背景和技术方案。", tip="快速过"),
    dict(no=6, title="政策方向与产品方向一致", sub="从「建系统」到「建韧性」",
         points=["安全：系统韧性、故障演练成为必答题", "AI：可解释的辅助决策，而不是黑箱", "数字基础设施：国产化、轻量部署"],
         data="3 条政策方向 → 4 项能力", visual="三列政策 + 右侧对应产品能力",
         script="政策强调安全、AI 与产业融合、数字基础设施。我们的方向正好对应：故障演练、可解释决策、轻量部署。",
         tip="不要念政策原文，只讲对应关系"),
    dict(no=7, title="03 技术创新", sub="四个创新点，重点看第 2、3 个",
         points=["装配预检", "容量感知传播", "恢复决策工程化", "确定性可复现"],
         data="4 个创新点", visual="大号 03 + 四个图标", script="第三部分是重点，四个创新点。", tip="语气加重"),
    dict(no=8, title="创新一：先判能不能跑", sub="12 条规则 + 容量感知 BFS，不只是一个可达性判断",
         points=["装配预检：端口冲突、资源超配、入口缺失在故障前暴露，定位到节点与连线", "传播推演：端口级反向 BFS，路径精确到「哪个端口调用哪个端口」", "强度修正：容量放大最高 1.5 倍，依赖冗余度按「挂几个/共几个」打折"],
         data="12 条规则 · 500 节点 1.6 ms · 阈值 0.6", visual="左侧规则清单截图，右侧传播路径图",
         script="第一个创新是装配预检加传播推演。我们不是上来就注入故障，而是先用 12 条规则判断这套系统能不能跑，确认能跑再沿依赖反向推演。这里有两个细节：依赖必须落到端口，所以路径能精确到接口；强度还叠加了宿主容量和依赖冗余度。",
         tip="主动承认 BFS 成熟，创新在组合与粒度"),
    dict(no=9, title="创新二：恢复不是拍脑袋", sub="五维评分 + 隔离折扣，避免「快但救不回」",
         points=["五维加权：时间 25%、影响 20%、成本 15%、风险 20%、业务恢复 20%", "隔离只止损不恢复业务，业务分 ×0.6", "实测：备用切换 79.08、服务重启 77.83、隔离 58.43（不适用）"],
         data="79.08 vs 77.83 vs 58.43", visual="五维雷达图 + 三策略条形对比",
         script="第二个创新是恢复决策。五维加权评分里，隔离最快但不恢复业务，所以我们把它的业务分打六折。实测隔离 58.43 被判不适用，推荐备用切换 79.08——这是算法算出来的，不是拍的。",
         tip="被打折这个点最能打动人，放慢"),
    dict(no=10, title="创新三：备用不是有就能切", sub="可用性校验 + 级联过载，防止越救越糟",
         points=["切备前逐项校验：状态、资源、优先级、端口一致性", "级联模型：备用接管 60% 负载，超 100% 即过载，最多 6 轮", "实测：MySQL-备接管后 4.8/4.0，超 20%，被拖垮"],
         data="4.8 / 4.0，超 20%", visual="主备接管示意 + 负载条形图",
         script="第三个创新是备用节点校验。有备用不代表能切，我们逐项检查状态、资源、端口。更进一步做了级联：备用接管后如果过载，它也会挂。演示里 MySQL-备接管后 4.8/4.0，超 20%，被判定拖垮。这说明主备同规格并不安全。",
         tip="全篇最有说服力的一段，数字说清楚"),
    dict(no=11, title="创新四：全过程可复现", sub="确定性算法 + 落库快照 + AI 只做解释",
         points=["无随机、无大模型参与判定，同输入同输出", "校验/推演/恢复全部落库，可回看、可导出拓扑 JSON", "AI 三层降级：大模型 → AI 规则 → 本地规则，断网也能出结论"],
         data="68 测试 · 23 步 E2E · 0 随机", visual="落库快照 + 三层降级示意",
         script="第四个创新是闭环和可复现。所有判定都是确定性的，AI 只负责把结论翻译成人话，不参与判定，所以断网也能用，答辩现场不会翻车。",
         tip="强调「答辩现场不会翻车」"),
    dict(no=12, title="04 竞品分析与演示", sub="章节页",
         points=["Kubernetes、Prometheus、Chaos Mesh、Gremlin 各管一段", "我们补的是「决策」这一段"],
         data="", visual="大号 04", script="第四部分，和现有工具比，我们强在哪。", tip="快速过"),
    dict(no=13, title="我们不做监控，做决策", sub="四个竞品各管一段，我们串成闭环",
         points=["K8s：编排自愈；Prometheus：监控告警；Chaos Mesh：故障注入；Gremlin：企业级实验", "共同短板：缺少端口级建模、恢复比较与可复现推演", "我们的差异：装配预检 + 接口级传播 + 多目标恢复 + 确定性"],
         data="4 个竞品 → 1 个定位", visual="四列竞品 + 底部「我们的位置」",
         script="K8s 管编排、Prometheus 管监控、Chaos Mesh 管注入、Gremlin 管企业级实验。它们各管一段，我们串成一条从装配到恢复的决策链。我们不是替代它们。",
         tip="主动说「不是替代」，评委更信"),
    dict(no=14, title="四层架构，AI 可降级", sub="交互、业务、智能、基础设施解耦",
         points=["交互层：Vue 3 + Vite，自绘 SVG 无限画布", "业务层：Spring Boot 3 + MyBatis-Plus，13 个控制器 32 个接口", "智能层：FastAPI 独立部署，挂了不影响主流程"],
         data="17 表 · 32 接口 · 4 层", visual="四层架构图（横向分层）",
         script="技术上四层解耦：前端自绘 SVG 画布，后端 Spring Boot，AI 独立部署，数据 MySQL，本地可切 H2。AI 挂了主流程不受影响。",
         tip="强调「自绘」和「降级」"),
    dict(no=15, title="八步闭环，每步可回看", sub="装配 → 校验 → 注入 → 推演 → 恢复 → 多动作 → 级联 → 复盘",
         points=["每一步都有输入、处理、输出", "校验/推演/恢复全部落库，带 traceId", "任何一次推演都能完整重现"],
         data="8 步 · 全部落库", visual="八步流程图（横向时间轴）",
         script="一次完整推演八步，每一步都产生可保存的中间结果，任何一次推演都能被完整重现。这是「可验证」的基础。",
         tip="手指沿流程走一遍，节奏清楚"),
    dict(no=16, title="现场演示：90 秒看完整链路", sub="校验 → 注入 → 推演 → 恢复 → 增强",
         points=["点校验：100 分可运行，问题定位到端口与连线", "注入 MySQL 主 node_down，点推演看路径与到达时间", "点恢复看三策略对比与级联提示，再点增强看割点与级联"],
         data="90 秒 · 5 步点击", visual="浏览器实屏（或录屏）",
         script="接下来现场演示一遍：校验、注入、推演、恢复、增强，五步。",
         tip="演示前先点「适应」，把画布满屏"),
    dict(no=17, title="真实截图一：装配校验", sub="问题定位到端口与连线，不是一句「配置有误」",
         points=["26 节点 / 48 连线，100 分可运行", "故意把宿主 CPU 改小，立刻报 R04 CPU 超配", "每条问题给修复建议，点击定位节点"],
         data="100 分 → 70 分（改 1 个数）", visual="校验问题清单截图（完整一屏）",
         script="这是真实的校验结果：26 个节点、48 条连线、100 分。我改一个宿主 CPU，它立刻报 CPU 超配，分数掉到 70。",
         tip="现场改一个数字，比讲十句强"),
    dict(no=18, title="真实截图二：恢复对比与级联", sub="推荐不是一个分数，而是一组理由",
         points=["三策略同屏对比：重启 84.50、隔离 75.46、切备不适用", "级联提示：MySQL-备接管 4.8/4.0 超 20%", "推荐理由包含数字，可复算"],
         data="79.08 / 77.83 / 58.43", visual="恢复方案面板截图 + 级联提示",
         script="恢复页面上三条策略并排，推荐服务重启，理由是隔离业务分被打了六折；下面还有级联提示：备用会过载（4.8/4.0）。",
         tip="让评委看到「理由」，不是只看分数"),
    dict(no=19, title="用在哪：三个场景", sub="高校实验、变更前评估、故障复盘训练",
         points=["高校：云原生课程的故障演练实验，天然可复现", "企业：变更前的影响评估，先推演再上线", "团队：故障复盘训练，把「如果当时」算出来"],
         data="3 个场景", visual="三栏场景卡片",
         script="这套东西可以用在三个地方：高校实验、企业变更前评估、运维团队的复盘训练。",
         tip="说「三个地方」时伸三根手指"),
    dict(no=20, title="下一步：从能推演到能指导恢复", sub="接真实数据、支持 K8s 导入、多目标比较、报告导出",
         points=["接入 Prometheus 指标，校准传播强度", "支持 K8s YAML 导入，从真实集群反向生成拓扑", "恢复方案做 Pareto 前沿，补演练报告导出"],
         data="4 项规划", visual="四步路线图 + 收尾句",
         script="后续四件事：接真实监控、支持 K8s 导入、多目标比较、报告导出。目标是能从能推演走到能指导真实恢复。谢谢各位老师。",
         tip="收尾放慢，停两秒再鞠躬"),
]

for pg in PAGES:
    H(f"第 {pg['no']} 页 · {pg['title']}", 2)
    T(["项", "内容"], [
        ["结论句", pg["sub"]],
        ["三个要点", "\n".join(f"{i+1}. {x}" for i, x in enumerate(pg["points"]))],
        ["关键数字", pg["data"] or "—"],
        ["配图建议", pg["visual"]],
        ["口播稿", pg["script"]],
        ["注意事项", pg["tip"]],
    ], widths=[2.6, 13.6], size=9.5)

    item = PAGE_IMG.get(pg["no"])
    if item:
        img_path = os.path.join(IMG_DIR, item[0])
        if os.path.exists(img_path):
            pic = doc.add_paragraph()
            pic.alignment = WD_ALIGN_PARAGRAPH.CENTER
            pic.add_run().add_picture(img_path, width=Cm(15.6))
            cap = doc.add_paragraph()
            cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
            cr = cap.add_run("图：" + item[1])
            set_font(cr, 9, False, (0x55, 0x65, 0x77))
            md.append("")
            md.append("![%s](docs/screenshots/%s)" % (item[1], item[0]))
            md.append("")

# ================= 3 视觉规范 =================
doc.add_page_break()
H("三、视觉规范（让 PPT 不像 AI 做的）", 1)
T(["项", "规范"], [
    ["画幅", "16:9（1920×1080）"],
    ["底色", "#0B1220 深蓝黑，内容区可加 #0F172A 卡片"],
    ["主色", "#38BDF8 青蓝，辅助 #A78BFA 紫、#F59E0B 橙"],
    ["文字", "标题思源黑体 Bold 36–44pt；正文 20–24pt；最小不小于 18pt"],
    ["版式", "每页一个大标题 + 一句结论 + 三块内容；留白 ≥ 20%"],
    ["配图", "只用真实截图与自绘图表，禁止 AI 生成配图、禁止图库风景图"],
    ["图表", "条形/雷达/流程图优先；数字大字号，单位清晰"],
    ["动效", "只保留「逐条出现」和「数字滚动」，不加翻页转场"],
    ["禁忌", "大段文字、渐变紫、无意义图标、英文缩写不解释、假数据"],
], widths=[2.6, 13.6])
P("视频里说的「去 AI 味」，核心就三条：真实素材、具体数字、短句。每页只讲一个观点，能用图就不用字。", indent=True)

# ================= 4 提示词 =================
doc.add_page_break()
H("四、给 GPT 的提示词模板（复制即用）", 1)
P("第一段（角色与约束）：", bold=True)
P("你是挑战杯答辩 PPT 设计顾问。下面给你《事实包》和《20 页大纲》，请严格基于它们工作：1）只能引用事实包里的数字，禁止编造；2）每页标题 ≤12 字，下面一句「所以呢」≤30 字；3）每页给 3 个要点 + 至少 1 个数字；4）标注该页用图类型（截图/流程图/对比表/数字卡片）；5）每页给 60 字以内口播稿；6）风格深色科技，主色 #38BDF8，16:9，正文不小于 20pt。", indent=True)
P("第二段（产出格式）：", bold=True)
P("请按页输出：页码｜标题｜结论句｜三个要点｜关键数字｜配图建议｜口播稿。不要写「欢迎来到」「让我们」这类空话，不要用感叹号。", indent=True)
P("第三段（迭代）：", bold=True)
P("先只输出 20 页的标题和结论句让我确认结构，确认后再逐页展开；每 5 页停下来等我反馈。", indent=True)

# ================= 5 素材 =================
doc.add_page_break()
H("五、素材清单与截图建议", 1)
T(["文件", "用途"], [
    ["docs/screenshots/scenario-26.png", "封面 / 创新一 / 演示背景（26 节点全图）"],
    ["docs/screenshots/cascade.png", "创新三 级联风险"],
    ["docs/screenshots/time-window.png", "创新一 时间窗与强度"],
    ["docs/screenshots/ports-sides.png", "端口分边说明"],
    ["README.md", "技术与运行事实"],
    ["云镜推演-算法与功能详解.docx", "公式与规则细节"],
    ["云镜推演-答辩与PPT演示说明.docx", "完整讲稿与问答"],
], widths=[7.2, 9.0])
P("截图规范：1600×900 以上、深色主题、只截关键区域、不要出现无关窗口；每张图配一句图注（例如「26 节点拓扑，校验 100 分」）。", indent=True)

doc.save(DOCX)
with open(MD, "w", encoding="utf-8") as f:
    f.write("\n".join(md))
print("saved", DOCX)
print("saved", MD)

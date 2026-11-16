# -*- coding: utf-8 -*-
"""生成《云镜推演 · 算法与功能详解》Word 文档。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = r"C:\Users\swhc6\Desktop\华为杯\云镜推演-算法与功能详解.docx"

doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Cm(21), Cm(29.7)
sec.left_margin = sec.right_margin = Cm(2.4)
sec.top_margin = sec.bottom_margin = Cm(2.2)

normal = doc.styles["Normal"]
normal.font.name = "Times New Roman"
normal._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
normal.font.size = Pt(11)
normal.paragraph_format.line_spacing = 1.45
normal.paragraph_format.space_after = Pt(4)


def set_font(run, size=11, bold=False, color=None, east="宋体"):
    run.font.name = "Times New Roman"
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), east)
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor(*color)


def h(text, level=1):
    sizes = {0: 22, 1: 16, 2: 13.5, 3: 12}
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(14 if level <= 1 else 10)
    p.paragraph_format.space_after = Pt(6)
    if level == 0:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_font(r, sizes.get(level, 12), True, (0x1F, 0x3A, 0x5F) if level else (0x0B, 0x12, 0x20), "黑体")
    return p


def para(text, indent=True, size=11, bold=False, color=None):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(size * 2) if indent else None
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run(text)
    set_font(r, size, bold, color)
    return p


def bullet(text, size=11):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.line_spacing = 1.4
    r = p.add_run(text)
    set_font(r, size)
    return p


def code(text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.6)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.1
    r = p.add_run(text)
    r.font.name = "Consolas"
    r._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), "宋体")
    r.font.size = Pt(9.5)
    r.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)
    return p


def shade(cell, hex_color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tcPr.append(shd)


MAX_TABLE_WIDTH = 16.0


def table(headers, rows, widths=None, font_size=9.5):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, x in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(x)
        set_font(r, font_size + 0.5, True)
        shade(hdr[i], "E8EEF6")
    for row in rows:
        cells = t.add_row().cells
        for i, x in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            r = p.add_run(str(x))
            set_font(r, font_size)
    if widths:
        total = sum(widths)
        scale = min(1.0, 16.0 / total) if total else 1.0
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w * scale)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


# ---------------- 封面 ----------------
doc.add_paragraph().paragraph_format.space_after = Pt(60)
h("云镜推演", 0)
h("算法与功能详解", 0)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("面向云原生系统的故障传播分析与恢复决策平台")
set_font(r, 13, False, (0x33, 0x41, 0x55))
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("版本 v0.9 · 2026 年 11 月 · 云启先锋小队")
set_font(r, 10.5, False, (0x55, 0x65, 0x77))

doc.add_page_break()

# 目录
h("目  录", 1)
toc = [
    "第 1 章  系统概述",
    "第 2 章  数据模型",
    "第 3 章  装配建模：节点、端口与连线",
    "第 4 章  可运行性校验：R01–R12",
    "第 5 章  故障传播算法",
    "第 6 章  恢复决策算法",
    "第 7 章  增强分析：关键单点、隔离切口、级联",
    "第 8 章  AI 辅助诊断",
    "第 9 章  工程质量、测试与部署",
    "第 10 章 创新点总结与展望",
]
for x in toc:
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run(x)
    set_font(r, 11.5)
doc.add_page_break()

# ---------------- 第 1 章 ----------------
h("第 1 章  系统概述", 1)
h("1.1  定位与边界", 2)
para("云镜推演是一个面向云原生系统的“装配—推演—决策”沙盘：用户在无限画布上把系统搭出来（节点、端口、部署关系、调用依赖、冗余关系），系统先判断这套装配能否运行，再在注入故障后推演影响范围与传播路径，最后比较多种恢复方案并给出可解释的理由。")
para("它解决的问题不是“监控指标是多少”，而是三个更靠前的问题：第一，这套系统现在就存在端口冲突、资源超配、入口缺失吗？第二，某个节点故障会波及谁、多严重、多久到？第三，先救谁最划算、救完会不会引发二次故障？")
para("明确的边界：本作品不接入生产监控、不执行真实故障注入、不替代 Kubernetes 或混沌工程平台；恢复时间、成本、风险均为模型估算值。它适合教学演练、方案评审与变更前评估，而不是生产环境的自动化运维。")

h("1.2  总体架构", 2)
para("系统采用前后端分离的四层结构：交互层、业务层、智能服务层与基础设施层。")
table(["层次", "技术选型", "职责"], [
    ["交互层", "Vue 3 + Vite，自绘 SVG 无限画布", "拓扑编辑、端口连线、校验/推演/恢复结果展示、历史回看"],
    ["业务层", "Spring Boot 3.3 + MyBatis-Plus", "装配管理、规则引擎、传播与恢复算法、落库与查询"],
    ["智能服务层", "Python FastAPI + OpenAI 兼容接口", "AI 解释与建议；不可用时降级到规则引擎"],
    ["基础设施层", "MySQL 8 / H2、Docker Compose", "持久化、部署与演示环境"],
], widths=[3.0, 6.4, 7.4])
para("交互层不依赖 Vue Flow、AntV G6 等图库，画布、节点、连线、缩放、拖拽全部自绘，因此端口分边、传播流动、故障脉冲这类细节可以按产品需要定制；业务层不依赖 Lombok，实体与 DTO 全部显式书写，便于在 JDK 17–25 之间稳定编译运行。")

h("1.3  核心流程", 2)
para("一次完整的推演由六步组成，每一步都产生可保存的中间结果：")
table(["步骤", "输入", "处理", "输出"], [
    ["① 装配", "节点库、端口、资源", "创建节点/端口，连线（部署、调用、复制、映射）", "拓扑数据落库"],
    ["② 校验", "当前拓扑", "12 条规则 + 可达性计算", "可运行性、0–100 分、问题清单"],
    ["③ 注入", "选择节点与故障类型", "生成 active 故障事件", "故障源列表"],
    ["④ 推演", "故障源 + 拓扑", "多源反向 BFS + 容量/冗余度/时间窗", "受影响节点、路径、必崩/可能、到达时间"],
    ["⑤ 决策", "故障 + 冗余组", "三策略五维评分、多动作序列、级联分析", "推荐方案、对比、理由"],
    ["⑥ 复盘", "历史记录", "落库快照 + AI 解释", "可回看的分析详情与恢复方案"],
], widths=[2.2, 4.0, 6.4, 4.4])
para("所有步骤都是确定性的：不引入随机数，AI 不参与判定，只负责把结构化结果翻译成人能读的建议。因此同一份拓扑与同一组故障，无论在哪台机器上、运行多少次，结论完全一致。")

h("1.4  代码结构", 2)
code("""backend/src/main/java/com/cloudmirror/
  controller/    13 个控制器，32 个接口
  service/       ProjectService、NodeService、EdgeService、ValidationRunner、FaultService…
  service/rule/  12 条规则实现（R01–R12）、ValidateContext、CheckItem、ValidateResult
  service/propagation/  PropagationService（反向 BFS + 容量 + 冗余度 + 时间窗）
  service/recovery/     RecoveryService（三策略五维评分）、RecoveryPlanner（多动作序列）
  service/analysis/     CriticalNodeAnalyzer（割点）、IsolationCutter（隔离切口）、CascadeAnalyzer（级联）
frontend/src/views/CanvasView.vue   单文件画布应用（约 2800 行）
ai-service/app/main.py              FastAPI：/ai/health、/ai/diagnose
tools/ui-e2e.mjs、ui-e2e-extra.mjs  零依赖 CDP 端到端测试""")

# ---------------- 第 2 章 ----------------
doc.add_page_break()
h("第 2 章  数据模型", 1)
h("2.1  十七张表", 2)
para("系统用 17 张表把“装配期数据”和“推演期数据”分开：装配期是用户画的拓扑，推演期是每次校验、推演、恢复产生的快照。这样历史记录不会因为后续改拓扑而失真。")
table(["分组", "表", "作用"], [
    ["用户与工程", "sys_user、project", "多用户预留；工程（拓扑容器）"],
    ["装配", "assembly_node、node_port、assembly_edge", "节点、端口、连线（边保存两端端口 ID）"],
    ["型号库", "device_model", "16 个内置型号，按类型过滤"],
    ["故障", "fault_event", "注入的故障（类型、严重度、状态）"],
    ["校验快照", "asm_check_run、asm_check_item", "每次校验的分数、可运行性、问题明细"],
    ["推演快照", "propagation_analysis、analysis_fault_source、analysis_affected_node、analysis_path_step", "分析汇总、故障源、受影响节点、逐跳路径"],
    ["恢复快照", "recovery_plan、recovery_strategy", "每次推荐的方案与三策略评分"],
    ["冗余", "redundancy_group、redundancy_member", "主备/双活组与成员优先级"],
], widths=[2.6, 7.6, 6.6])

h("2.2  节点与端口", 2)
para("节点（assembly_node）保存类型、型号、CPU/内存/磁盘、副本数、IP、运行时、状态、坐标与描述。端口（node_port）挂在节点下，字段包括端口号、协议、访问范围（internal/public）、宿主端口与用途。")
para("端口不是一个装饰性的圆点，它是“接口级依赖”的落点：一条调用依赖边必须同时记录调用方端口和被调用方端口，校验规则会检查这两个端口是否真实存在、是否属于对应节点。")

h("2.3  五类连线与端口约束", 2)
table(["连线类型", "语义", "是否需要端口", "典型场景"], [
    ["deploy_on 部署于", "工作负载跑在宿主机/VM/K8s 集群上", "否", "订单服务 → 物理机-1"],
    ["network_link 网络可达", "两台主机/集群之间网络连通", "否", "物理机-1 ↔ 物理机-2"],
    ["depends_on 调用依赖", "调用方依赖被调用方的某个接口", "是（两端都要）", "订单服务:8081 → MySQL:3306"],
    ["replicate 主备复制", "主节点向备节点复制数据", "是（两端都要）", "MySQL 主:3306 → MySQL 备:3306"],
    ["port_map 端口映射", "宿主端口映射到容器端口", "是（通常源为 public）", "宿主机:18080 → 容器:8080"],
], widths=[3.6, 5.4, 3.2, 5.4])
para("例外：外部系统与对象存储不建模端口，因此它们参与的依赖边允许不带端口；后端在创建边时会拦截“接口级连线缺端口”的请求（HTTP 400），校验规则 R07 也会对历史数据给出错误提示。")

h("2.4  推演期快照", 2)
para("每次推演会写入一条 propagation_analysis 及三类子表：故障源、受影响节点、逐跳路径。受影响节点保存距离、来源故障、影响级别，路径表保存每一跳的节点、边与两端端口。恢复方案保存推荐类型、理由，以及三条策略的适用性、耗时、成本、风险与分数。")
para("从 v0.9 起，分析记录还保存时间窗（window_seconds）与当时的级联结论（cascade_summary），因此历史回看能看到“只统计了 1 秒内的波及范围”和“备用接管后过载”这类信息，而不是只有一个总数。")

h("2.5  统一响应与链路追踪", 2)
para("所有接口统一返回 {code, message, data}；错误码约定：40001 参数错误、40401 资源不存在、40901 冲突、42201 语义不合法、50000 服务内部错误。每个请求由 TraceIdFilter 生成 traceId，写入日志 MDC 并回写响应头 X-Trace-Id，前端在报错 toast 中展示，便于对照日志排查。")

# ---------------- 第 3 章 ----------------
doc.add_page_break()
h("第 3 章  装配建模：节点、端口与连线", 1)
h("3.1  十一类节点", 2)
table(["类型", "语义", "参与的关键规则"], [
    ["物理机 / 宿主机 host", "承载工作负载的物理资源池", "R04–R06 容量超配"],
    ["虚拟机 vm", "与宿主机同类的资源池", "R04–R06"],
    ["K8s 集群 k8s_cluster", "承载 Pod 的容量池", "R04–R06（按核数）"],
    ["Pod / 工作负载 pod", "K8s 最小调度单元", "R07 依赖、R08 网络"],
    ["容器 container", "请求 CPU/内存的最小单位", "R04–R06"],
    ["应用服务 service", "业务服务，必须依赖中间件", "R07、R10"],
    ["中间件 middleware", "MySQL/Redis/MQ 等", "R07 目标、R08"],
    ["对象存储 object_storage", "OSS/S3，外部依赖", "R07 视作存储依赖，R08 视为外网"],
    ["网关 / 负载均衡 gateway", "业务入口", "R10 入口可达"],
    ["Ingress ingress", "集群入口，等同网关", "R10"],
    ["外部系统 external", "拓扑之外的系统", "R10 入口、R07 例外"],
], widths=[5.4, 6.0, 5.4])
para("节点类型不是单纯的图标分类，它决定了资源校验的口径（宿主机看 cpuCores，工作负载看 cpuRequest）、依赖规则的适用（服务必须能到达存储）、以及入口判定的范围（网关、Ingress、外部系统）。")

h("3.2  端口的语义", 2)
para("一个节点可以暴露多个端口，这符合真实系统：MySQL 有 3306 业务口、9104 监控口、33060 X 协议口；网关有 443、80、9090 管理口。端口字段包括：")
table(["字段", "说明", "示例"], [
    ["port", "监听端口号，1–65535", "3306"],
    ["protocol", "tcp / udp", "tcp"],
    ["scope", "internal 仅内网 / public 对外暴露", "public"],
    ["hostPort", "对外暴露时占用的宿主端口", "13306"],
    ["purpose", "用途说明，便于评审与排障", "MySQL 协议"],
], widths=[3.2, 9.0, 4.6])
para("同一节点内 (protocol, port) 必须唯一，重复会返回 409；端口号越界返回 400。端口在画布上按使用方向自动分边：主要作为目标端口（被依赖）的放左侧，主要作为源端口（发起调用）的放右侧，因此整张图的连线大体从左向右流动，不会绕圈。")

h("3.3  连线创建的三道校验", 2)
bullet("前端：选择“调用依赖/主备复制/端口映射”后，如果起点或终点没有落到端口，直接提示并拒绝创建；")
bullet("后端：接口级连线缺端口返回 400；同一条边（同类型、同两端端口）重复创建返回 409；自依赖返回 400；")
bullet("校验：R07 会对历史遗留的无端口依赖边报错，提示“调用依赖必须落到接口”。")
para("删除端口时也做了保护：如果端口仍被连线引用，更新节点会返回 409 并提示先删连线；只有显式传 force=true 才强制删除，避免“改个端口把连线全删了”这类静默数据丢失。")

# ---------------- 第 4 章 ----------------
doc.add_page_break()
h("第 4 章  可运行性校验：R01–R12", 1)
h("4.1  规则引擎结构", 2)
para("每条规则实现统一接口 ValidateRule：code()、name()、severity()、check(ValidateContext)。上下文 ValidateContext 在规则执行前预计算四类索引：节点表、端口表、边表、冗余组，以及端口占用表、宿主资源汇总、可达性等派生数据，避免每条规则重复遍历。")
para("结果由 CheckItem 表达：规则编码、名称、严重度、问题描述、修复建议、涉及节点、涉及连线。ValidateResult 汇总可运行性、分数、错误/警告/提示数量与逐条规则状态。")

h("4.2  评分规则", 2)
para("总分从 100 起扣：每个 ERROR 扣 30 分，WARN 扣 10 分，INFO 扣 2 分，最低 0 分；只要存在任一 ERROR，即判“不可运行”。空工程不按“全部通过”处理，而是返回 0 分并提示“工程里还没有节点，先装配再校验”，避免误导。")

h("4.3  十二条规则逐条说明", 2)
rules = [
    ["R01", "端口冲突", "错误", "同一节点上 (协议, 端口) 重复；同一宿主机上 public 端口的宿主端口重复", "split_host 后 8080 被两个容器同时映射"],
    ["R02", "端口越界", "错误", "端口号不在 1–65535", "端口填 70000"],
    ["R03", "端口映射冲突", "错误", "port_map 的宿主端口与已有映射或宿主监听端口冲突", "两条映射都占用宿主 18080"],
    ["R04", "CPU 超配", "错误", "宿主机上所有工作负载 cpuRequest 之和 > cpuCores", "4 核宿主部署 5 核请求"],
    ["R05", "内存超配", "错误", "memRequest 之和 > memTotalMb", "8G 宿主请求 10G"],
    ["R06", "磁盘超配", "错误", "diskGb 之和 > 宿主磁盘", "100G 盘请求 150G"],
    ["R07", "依赖缺失", "错误", "接口级依赖缺端口/端口不属于该节点；服务沿依赖链无法到达任何中间件或对象存储", "服务直连另一个服务，但整条链上没有 DB"],
    ["R08", "网络不可达", "错误", "跨宿主调用既无 network_link 路径，也非同一宿主（外部系统除外）", "两主机未连网络可达"],
    ["R09", "循环依赖", "错误", "depends_on 中存在环", "A→B→C→A"],
    ["R10", "入口不可达", "警告/错误", "没有入口节点时警告；有入口但服务不可达时报错", "有网关但没连到订单服务"],
    ["R11", "孤儿节点", "提示", "节点没有连任何线", "画了但忘了连"],
    ["R12", "主备不完整", "警告", "主备组缺主/缺备/主备端口不一致/备用节点不可用", "备用节点与主节点端口不同"],
]
table(["编码", "规则", "级别", "判定要点", "示例"], rules, widths=[1.5, 2.4, 1.6, 7.6, 4.3], font_size=9)
para("几条容易误报的规则做了专门处理：R07 改为沿依赖链传递查找存储（服务→服务→DB 不再误报），外部系统与对象存储不要求端口；R08 在两端都未部署宿主机时跳过；R10 在没有入口节点时降为警告，避免边搭边看时满屏红。")

h("4.4  性能与确定性", 2)
para("规则只做一次上下文预计算，单条规则内不再查库。对 500 节点、1000 条边的拓扑，完整校验（12 条规则）平均约 1.6 ms。所有规则只读、无随机、无时间依赖，同一拓扑的两次校验结果完全一致。")

# ---------------- 第 5 章 ----------------
doc.add_page_break()
h("第 5 章  故障传播算法", 1)
h("5.1  图模型与反向邻接", 2)
para("系统把拓扑抽象为有向图 G=(V,E)：节点是 V，边是 E，方向为“调用方 → 被调用方”。故障沿依赖方向的反方向扩散：如果 A 依赖 B，那么 B 故障会影响 A。因此传播算法维护的是反向邻接表 dependents[B] = {A…}，而不是正向邻接。")
code("""dependents: Map<Long, List<Edge>>
for e in edges where e.kind == depends_on:
    dependents[e.target].add(e)      // B 的故障会传给 A = e.source""")

h("5.2  多源 BFS", 2)
para("所有激活故障同时入队，距离为 0；每次从队列取出节点，遍历它的 dependents，若目标未被访问则记录距离、父边、来源故障并入队。首次访问即最短距离，因此结果为最短传播路径；已访问集合同时保证环路安全。")
code("""queue = all fault nodes, distance = 0
while queue not empty:
    cur = queue.poll()
    for e in dependents[cur]:
        up = e.source
        if visited(up): continue
        distance[up] = distance[cur] + 1
        parentNode[up] = cur; parentEdge[up] = e; source[up] = source[cur]
        queue.add(up)""")
para("复杂度 O(V+E)。多源去重规则：取最短距离；同距离按故障 ID 升序，保证结果稳定。")

h("5.3  路径回溯", 2)
para("BFS 只记录父边，展示时需要从受影响节点沿父边一路回溯到故障源，再反转成“源 → 目标”。每一跳保存节点、边 ID、调用方端口、被调用方端口，因此路径可以精确到接口：例如“MySQL 主 → 订单服务(3306→8081) → 网关(8081→8080)”。")

h("5.4  容量感知（E1）", 2)
para("同样的依赖边，在宿主负载 20% 与 95% 时，故障后果并不相同。容量感知用宿主 CPU 利用率放大传播强度：")
code("""u(host) = min(1, Σ cpuRequest(children) / cpuCores(host))
amplification(node) = 1 + 0.5 × u(host(node))""")
para("即宿主越满，下游故障对上层的影响越强，最高放大 1.5 倍。这使“快撑不住的系统更容易被一条依赖拖垮”这一直觉变成可计算量。")

h("5.5  依赖冗余度（本作品的小创新）", 2)
para("传统做法中，一个服务只要有一条下游依赖受影响，就可能被判“必崩”。但真实系统里，服务通常有多个下游（数据库 + 缓存 + 消息队列），挂一个未必致命。为此引入依赖冗余度：")
code("""r(A) = 受影响的下游依赖数 / A 的总下游依赖数
strength(A) = 路径强度 × r(A)""")
para("举例：订单服务依赖 MySQL 与 Redis 两个下游；MySQL 故障时 r=0.5，强度从 1.0 降到 0.5，影响级别由“必崩”（阈值 0.6）降为“可能受影响”；若两个下游都故障，r=1.0，仍判必崩。这个修正不改变 BFS 的距离语义，只调整严重度，且完全确定、可解释。")

h("5.6  时间窗传播", 2)
para("边上记录了延迟 latency_ms。BFS 时同步累加到达时间：arrival(A) = arrival(B) + latency(B→A)。接口支持 windowSeconds 参数，只保留到达时间 ≤ T×1000 毫秒的受影响节点，从而回答“故障发生后 1 秒内会波及谁”。未设置延迟的边按 0 ms 计。")
para("实现上仍是 BFS（按跳数），时间窗只是过滤；若未来需要严格按时间排序，可改为以延迟为权的 Dijkstra，复杂度 O((V+E)logV)。")

h("5.7  输出与分级", 2)
table(["字段", "含义", "示例"], [
    ["impactLevel", "DIRECT 直接 / INDIRECT 间接", "直接（距离 1）"],
    ["distance", "传播跳数", "2"],
    ["pathStrength", "传播强度（0–1，保留 3 位）", "0.5"],
    ["dependencyRatio", "依赖冗余度", "0.5"],
    ["impactClass", "CERTAIN ≥ 0.6 / POSSIBLE < 0.6", "POSSIBLE"],
    ["arrivalMillis", "到达时间（毫秒）", "1000"],
    ["path", "逐跳节点、边、两端端口", "MySQL→订单→网关"],
], widths=[4.2, 7.6, 5.0])
para("摘要同时给出“直接/间接”“必崩/可能”“影响比例”，前端在分析面板中逐行展示，并支持点击定位节点。")

h("5.8  边界", 2)
para("当前模型未建模重试风暴、线程池耗尽、网络分区、跨可用区延迟抖动等细节；传播强度是经验参数（容量放大 0.5、必崩阈值 0.6），需要用真实故障数据校准。这些边界在申报书与答辩中都会主动说明，避免过度承诺。")

# ---------------- 第 6 章 ----------------
doc.add_page_break()
h("第 6 章  恢复决策算法", 1)
h("6.1  三种策略", 2)
table(["策略", "适用条件", "不适用原因示例"], [
    ["服务重启 restart", "故障节点是服务/中间件，且无备用可切换", "节点不是可重启的进程（如宿主机断电）"],
    ["备用切换 failover", "存在主备组，备用节点健康、资源足够、优先级可排序", "备用节点状态为 down 或资源不足"],
    ["故障隔离 isolate", "隔离后所有业务入口仍至少有一个可用依赖", "隔离会导致入口失去全部依赖"],
], widths=[3.6, 8.4, 5.0])

h("6.2  五维评分", 2)
para("每条策略在五个维度上打分（0–1），加权求和后乘 100：")
code("""Score = 100 × (0.25×时间分 + 0.20×影响分 + 0.15×成本分 + 0.20×风险分 + 0.20×业务恢复分)

时间分   = max(0, 1 - 预计耗时 / 600 秒)
影响分   = 1 - 恢复后仍不可用节点数 / 总节点数
成本分   = 按成本等级（LOW 1.0 / MEDIUM 0.7 / HIGH 0.3）
风险分   = 按风险等级（LOW 1.0 / MEDIUM 0.6 / HIGH 0.3）
业务恢复分 = 恢复后仍可达的业务入口数 / 总入口数""")
para("权重写死在代码与文档中，便于评审复算；后续可外置为配置项，并引入 Pareto 前沿，把“为什么是 0.25”从主观权重问题变成多目标比较问题。")

h("6.3  隔离策略的折扣", 2)
para("隔离只止损、不恢复业务，但它的耗时最短，如果不做修正，会靠“快”永远拿第一。因此隔离的业务恢复分按 0.6 折算，风险等级在适用时取 MEDIUM。实测效果：演示工程中隔离从 91.46 分降到 75.46 分，推荐回到“服务重启 84.50 分”。")

h("6.4  备用可用性校验", 2)
para("切换前逐项检查：备用节点是否存在、状态是否 healthy、资源是否足以接管、优先级是否可比较、主备端口是否一致。任一不满足，该策略判为不适用，并给出具体原因（例如“备用节点 MySQL 备 状态为 down”），而不是简单给低分。")

h("6.5  多动作恢复序列（E4）", 2)
para("真实恢复通常不是单一动作，而是“先切备、再重启原主、最后扩容”这样的序列。RecoveryPlanner 在可用动作集合上做贪心组合：每轮选择“单位时间收益最大”的动作，直到所有受影响入口恢复或没有可用动作。输出每一步的动作、目标节点、预计耗时、累计耗时与收益，并检查动作之间的前置条件（例如不能先重启再切备）。")

h("6.6  级联失效模型", 2)
para("主节点故障后，备用节点接管它的负载；如果备用没有余量，接管后就会过载，于是它也挂——这是真实事故中常见的二次故障。模型如下：")
code("""容量 cap(node) = cpuCores（宿主机）或 cpuRequest（其它）
基线负载 load(node) = 0.6 × cap(node)
转移：load(standby) += 0.6 × cap(failed)
过载判定：load(standby) > cap(standby)
最多迭代 6 轮，每轮只处理上一轮新失败的节点""")
para("实测演示工程：MySQL 主故障后，MySQL 备接管，负载变为 1.2 / 1.0，超出 20%，被判定过载并拖垮，级联摘要为“发生 1 轮级联过载，共 1 个节点被拖垮（MySQL 备）”。这条结论直接说明“主备同规格”并不安全，备用至少需要约 1.7 倍容量。")

h("6.7  推荐与解释", 2)
para("最终输出包含推荐策略、推荐理由、三策略对比、每条策略的适用性与原因、以及“预估说明”（提示时间为模型估算）。推荐理由不是模板句，而是拼接了关键数字：为什么选它、它比第二名多多少分、被淘汰的策略为什么不可用。")

# ---------------- 第 7 章 ----------------
doc.add_page_break()
h("第 7 章  增强分析：关键单点、隔离切口、级联", 1)
h("7.1  关键单点（Tarjan 割点）", 2)
para("把 depends_on 视为无向图（或按依赖方向构造），用 Tarjan 算法求割点：删除该节点会使连通分量增加，说明它是结构性单点。输出节点、被切断后的分量数、受影响的依赖边数。复杂度 O(V+E)。")
h("7.2  隔离切口", 2)
para("当故障已经发生，需要判断“切断哪些节点可以阻止继续扩散”。IsolationCutter 在故障源的上游做贪心：每轮选择一个能覆盖最多受影响入口的节点加入切口，最多 3 个；输出切口集合、被截断的路径数、仍可达的入口数。它是启发式，不保证全局最小点割，但对演示规模的图足够快且可解释。")
h("7.3  恢复序列与级联", 2)
para("增强分析面板同时展示 E4 的多动作序列与 6.6 的级联结论，让用户在一个界面里看到“先做什么、做完会不会更糟”。")

# ---------------- 第 8 章 ----------------
doc.add_page_break()
h("第 8 章  AI 辅助诊断", 1)
h("8.1  三层降级", 2)
table(["层级", "触发条件", "输出"], [
    ["大模型", "AI 服务可用且配置了 API Key", "自然语言诊断、处置建议"],
    ["AI 服务规则", "AI 服务在线但无 Key 或模型超时", "基于结构化结果的模板化建议"],
    ["后端本地规则", "AI 服务不可达", "后端拼装的摘要与建议"],
], widths=[3.6, 7.2, 6.2])
h("8.2  输入与约束", 2)
para("AI 只接收结构化结果：故障源、受影响节点、强度分级、传播路径、恢复评分与级联结论；提示词明确要求“不要编造未提供的数据、不要给出与评分矛盾的推荐”。AI 不参与判定，因此即使模型输出不稳定，系统结论仍然确定。")
h("8.3  超时与失败", 2)
para("后端 HTTP 客户端使用 HTTP/1.1、10 秒超时、无重试；失败即降级，不影响主流程。前端在 AI 面板展示来源标签（大模型 / AI 规则 / 本地规则），用户始终知道这段文字是谁生成的。")

# ---------------- 第 9 章 ----------------
doc.add_page_break()
h("第 9 章  工程质量、测试与部署", 1)
h("9.1  测试", 2)
table(["测试类型", "数量", "覆盖内容"], [
    ["校验引擎单测", "19", "基线、12 条规则的正例与反例、空工程、传递依赖"],
    ["传播算法单测", "12", "多源、环路、去重、容量放大、冗余度、时间窗"],
    ["恢复算法单测", "16", "三策略适用性、权重、确定性、多动作序列"],
    ["高级分析单测", "7", "割点、隔离切口"],
    ["级联分析单测", "4", "同规格备用、扩容备用、无冗余组、多级备胎"],
    ["API 集成测试", "8", "全链路、缺端口 400、重名 409、自依赖 400、保存保留连线"],
    ["数据一致性测试", "2", "删工程级联、删节点清冗余组"],
    ["性能测试", "1", "500 节点 / 1000 边，约 1.59 ms"],
    ["UI 端到端", "23 步", "主链路 14 步 + 进阶交互 9 步（CDP 真实点击）"],
], widths=[4.6, 2.6, 9.8])
h("9.2  部署", 2)
para("开发环境用 H2（文件库，零安装），生产可切 MySQL 8；前端构建为静态资源，由 nginx 或任意静态服务器托管；AI 服务独立部署，不启动也能用。Docker Compose 提供 mysql、ai、backend、frontend 四个服务，默认端口 3307/8010/8080/8088。")
h("9.3  已知限制", 2)
bullet("未接入真实监控与真实故障注入，时间/成本/风险为估算；")
bullet("未建模重试风暴、网络分区等复杂故障形态；")
bullet("恢复方案未做 Pareto 多目标前沿，仍是加权单分；")
bullet("Docker 配置尚未在真实 Docker 环境验证。")

# ---------------- 第 10 章 ----------------
doc.add_page_break()
h("第 10 章  创新点总结与展望", 1)
h("10.1  四点创新", 2)
para("第一，端口级装配预检：12 条规则把“能不能跑”前置，且问题定位到端口与连线；第二，容量感知 + 依赖冗余度 + 时间窗的传播模型，应答“多严重、多久到、还有没有别的下游”；第三，恢复决策工程化：隔离折扣、级联过载、多动作序列，避免“看起来快但救不回来”；第四，确定性可复现：无随机、AI 不判定、结果落库可审计。")
h("10.2  与现有工具的关系", 2)
table(["工具", "强项", "与本作品的关系"], [
    ["Kubernetes", "编排、自愈", "互补：它保证跑起来，本作品判断故障影响"],
    ["Prometheus", "指标与告警", "互补：它给现状，本作品给推演"],
    ["Chaos Mesh / ChaosBlade", "故障注入", "互补：它负责注入，本作品负责影响与恢复比较"],
    ["Jaeger / OpenTelemetry", "链路追踪", "互补：它记录已发生的调用，本作品预测未发生的传播"],
    ["Gremlin / 商业平台", "企业级实验编排", "差异：本作品轻量、可复现、面向教学与评审"],
], widths=[4.6, 5.2, 8.2])
h("10.3  未来工作", 2)
para("接入 Prometheus 指标校准传播强度；支持 K8s YAML 导入，从真实集群反向生成拓扑；引入 Pareto 前沿做多目标恢复比较；补充 PDF 演练报告；在大规模拓扑上做性能与准确性验证。")

doc.save(OUT)
print("saved", OUT)

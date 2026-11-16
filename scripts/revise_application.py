# -*- coding: utf-8 -*-
"""修订申报书：把 B3 表的目的/创新/技术关键/指标、科学性、推广说明、C 表研究概述更新为当前系统。"""
import os
import shutil
from docx import Document
from docx.shared import Pt
from docx.oxml.ns import qn

SRC = r"C:\Users\swhc6\Desktop\华为杯\云镜推演.docx"
BAK_DIR = r"C:\Users\swhc6\Desktop\华为杯\原始备份"
DST = r"C:\Users\swhc6\Desktop\华为杯\云镜推演-申报书-修订版.docx"

os.makedirs(BAK_DIR, exist_ok=True)
shutil.copy2(SRC, os.path.join(BAK_DIR, "云镜推演-原始-申报书.docx"))


def style_run(run, size=10.5, bold=False):
    run.font.name = "Times New Roman"
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), "宋体")
    run.font.size = Pt(size)
    run.bold = bold


def fill_cell(cell, blocks):
    """blocks: [(text, bold, indent)]，第一段复用原段落。"""
    cell.text = ""
    first = True
    for text, bold, indent in blocks:
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        first = False
        p.paragraph_format.space_before = Pt(2)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.line_spacing = 1.25
        if indent:
            p.paragraph_format.first_line_indent = Pt(21)
        run = p.add_run(text)
        style_run(run, 10.5 if not bold else 11, bold)


doc = Document(SRC)
t5 = doc.tables[5]

purpose = [
    ("作品设计、发明的目的和基本思路", True, False),
    ("一、设计目的", True, False),
    ("云原生系统服务多、依赖深，一次故障往往沿调用链扩散；而现有工具各管一段——Kubernetes 负责编排自愈，Prometheus 负责监控告警，Chaos Mesh 负责故障注入，链路追踪负责事后定位，却缺少一个把“这套系统能不能跑—故障会传到哪—先救谁最划算”串起来、且结论可复现的推演沙盘。本作品面向教学演练、方案评审与变更前评估三个场景，构建可交互、可解释、可回看的故障推演与恢复决策平台。", False, True),
    ("二、基本思路", True, False),
    ("1. 装配建模：提供 11 类节点（宿主机、虚拟机、K8s 集群、Pod、容器、应用服务、中间件、对象存储、网关、Ingress、外部系统）。节点下挂端口，端口记录协议、访问范围、宿主端口与用途；节点之间用 5 类边连接：部署于、网络可达属于节点级关系，不需要端口；调用依赖、主备复制、端口映射属于接口级关系，必须落到具体端口，从数据层面保证“调用关系可追溯到接口”。", False, True),
    ("2. 可运行性预检：在注入故障之前先判断这套装配是否成立。12 条规则（R01–R12）覆盖端口冲突、端口越界、端口映射冲突、CPU/内存/磁盘超配、依赖缺失、网络不可达、循环依赖、入口不可达、孤儿节点、主备不完整，输出“可运行/不可运行”、0–100 分、问题清单和逐条修复建议；空工程不判满分，而是提示先完成装配。", False, True),
    ("3. 故障注入与传播推演：以多源反向 BFS 计算受影响节点和传播路径，并在路径权重上叠加三项修正：容量感知（按宿主 CPU 余量放大冲击）、依赖冗余度（受影响下游数/总下游数，避免“挂一个就判必崩”）、时间窗（按边延迟累加到达时间，可只看 T 秒内的波及范围），输出“必崩/可能受影响”分级、到达时刻与完整链路。", False, True),
    ("4. 恢复决策：对服务重启、备用节点切换、故障节点隔离三种策略，按恢复时间、影响范围、资源成本、操作风险、业务恢复能力五个维度加权评分；隔离策略的业务恢复分按 0.6 折算，避免“靠快取胜却救不回业务”；结合冗余组给出多动作恢复序列；级联失效模型模拟“备用接管后过载再挂”的连锁反应，最多迭代 6 轮。", False, True),
    ("5. 解释与复盘：AI 诊断采用三层降级（大模型→AI 服务规则→后端本地规则），只做解释不做判定；校验、推演、恢复结果全部落库，支持历史回看、拓扑 JSON 导入导出和同一输入的完全复现。", False, True),
    ("三、创新点", True, False),
    ("1. 端口级装配预检：把“可运行性”前置为 12 条可解释规则，端口冲突、资源超配、入口缺失在故障发生前暴露，而不是等到线上出事才发现。", False, True),
    ("2. 容量感知 + 依赖冗余度 + 时间窗的传播模型：不止回答“会波及谁”，还回答“多严重、多久到、是否还有别的下游撑着”，并对“必崩/可能”给出可核对的分级依据。", False, True),
    ("3. 恢复决策工程化：隔离策略业务分折算、级联过载判定、多动作恢复序列，避免推荐“看起来快、实际救不回来”的方案。", False, True),
    ("4. 确定性可复现：无随机数、无大模型参与判定，同一拓扑与故障输入永远得到同一结果，所有结论带 traceId 落库可审计。", False, True),
    ("5. 面向教学的完整闭环：无限画布、分组节点库、端口分边、传播流动、拓扑导入导出、历史回看，配套 14 步主链路与 9 步进阶 UI 自动化测试，保证演示稳定。", False, True),
    ("四、技术关键", True, False),
    ("1. 端口级拓扑数据模型：17 张表规范化存储节点、端口、边、故障、分析快照、恢复方案、冗余组与校验记录；边保存两端端口 ID，使“接口级依赖”可追溯、可校验。", False, True),
    ("2. 校验规则引擎：统一规则接口 + 上下文预计算（端口占用表、宿主资源汇总、可达性 BFS），12 条规则彼此独立、可单测；评分按严重度扣分（错误 30、警告 10、提示 2），并给出修复建议。", False, True),
    ("3. 传播算法：反向邻接 O(V+E) 多源 BFS；传播强度 = 边权累乘 × 容量放大系数 × 依赖冗余度；时间窗按边延迟累计，可在 O((V+E)logV) 内回答“T 秒内影响范围”。", False, True),
    ("4. 恢复评估：五维加权（时间 0.25、影响 0.20、成本 0.15、风险 0.20、业务 0.20）；备用可用性校验覆盖状态、资源与优先级；级联失效按“容量 × 60% 基线负载”转移并迭代判定过载。", False, True),
    ("5. 可解释 AI：结构化结果受控注入提示词，AI 只做解释与建议；服务不可用时自动降级到规则引擎，保证离线环境也能出结论。", False, True),
    ("五、主要技术指标", True, False),
    ("1. 架构：前后端分离。后端 Spring Boot 3.3 + MyBatis-Plus + MySQL 8（本地可切 H2）；前端 Vue 3 + Vite，自绘 SVG 无限画布，不依赖 Vue Flow / AntV G6；AI 服务 Python FastAPI。", False, True),
    ("2. 规模：17 张表、16 个实体、13 个控制器共 32 个接口、11 类节点、5 类边、12 条校验规则、3 种恢复策略。", False, True),
    ("3. 性能：500 节点/1000 边全量校验约 1.6 ms；传播与校验均为 O(V+E)，时间窗模式 O((V+E)logV)；级联最多 6 轮。", False, True),
    ("4. 质量：后端 68 个单元/集成测试全部通过；UI 端到端 14 步主链路 + 9 步进阶交互全部通过；接口统一 {code,message,data} 响应并带 X-Trace-Id。", False, True),
    ("5. 可复现：同一拓扑与故障输入结果完全一致；校验、分析、恢复均落库，支持历史回看与拓扑 JSON 导入导出。", False, True),
]
fill_cell(t5.cell(2, 1), purpose)

science = [
    ("本作品“云镜推演——面向云原生系统的故障传播分析与恢复决策平台”的科学性与先进性说明如下。", False, True),
    ("一、科学性：模型有明确的理论依据", True, False),
    ("1. 拓扑与传播：把服务及依赖抽象为有向图 G=(V,E)，边方向为“调用方→被调用方”。故障沿依赖反向扩散，采用多源反向 BFS，时间复杂度 O(V+E)，可证明其给出的是最短传播距离；路径回溯记录每一跳的边与两端端口，保证结论可核对。", False, True),
    ("2. 容量与冗余：在路径权重上引入容量放大系数 1+0.5×宿主利用率，以及依赖冗余度 r=受影响下游数/总下游数，使“同样一条依赖边，在下游全部失效与仅失效其一”两种情况下得到不同强度，贴近真实系统的负载分担行为。", False, True),
    ("3. 时间维度：按边延迟累加得到到达时刻，支持“T 秒内波及范围”查询，把“影响面”从静态集合扩展为随时间的扩散过程。", False, True),
    ("4. 恢复决策：采用五维加权评分（时间 0.25、影响 0.20、成本 0.15、风险 0.20、业务 0.20），权重与折算规则显式写入代码与文档；隔离策略业务分按 0.6 折算，避免“恢复最快”压过“业务是否真的恢复”。", False, True),
    ("5. 级联失效：借鉴负载重分配级联模型，节点故障后其基线负载（容量的 60%）转移给优先级最高的可用备用，接管后利用率超过 100% 即判定过载并进入下一轮，最多 6 轮，可复现“主备同规格时切备反而连锁宕机”的典型风险。", False, True),
    ("二、先进性：与现有技术相比的特点", True, False),
    ("1. 与 Kubernetes 相比：K8s 解决“怎么编排、怎么自愈”，本作品解决“故障会传到哪、先救谁”，二者互补而非替代。", False, True),
    ("2. 与 Prometheus / 链路追踪相比：监控与追踪回答“现在是什么状态、请求走了哪条路”，本作品在装配阶段就回答“这套系统是否成立”，并在故障前推演影响面。", False, True),
    ("3. 与 Chaos Mesh / ChaosBlade 相比：混沌工具强在“注入什么故障”，本作品强在“注入之后如何评估影响、如何比较恢复方案”，并给出可落库、可回看的决策依据。", False, True),
    ("4. 与 Gremlin / 商业可靠性平台相比：商业平台偏生产级实验编排与组织流程，本作品面向教学、评审与变更前评估，强调轻量部署、端口级建模与确定性复现。", False, True),
    ("5. 组合创新：端口级装配预检（12 条规则）+ 容量感知/冗余度/时间窗传播 + 多动作恢复与级联失效 + 确定性可复现，构成一条从“能不能跑”到“怎么救”的完整证据链，这是与单一工具相比的实质性特点。", False, True),
    ("三、局限与验证计划", True, False),
    ("本作品的恢复时间、成本、风险为模型估算值，尚未接入生产监控数据；传播模型未建模重试风暴与网络分区细节；BFS、割点等基础算法为成熟方法，创新集中在建模粒度、组合方式与决策表达。后续计划接入 Prometheus 指标与 K8s YAML 导入，用真实拓扑与故障记录校验模型，并补充更大规模拓扑的性能测试。", False, True),
    ("参考文献：", True, False),
    ("[1] Cormen T H, Leiserson C E, Rivest R L, Stein C. Introduction to Algorithms[M]. 3rd ed. MIT Press, 2009.", False, True),
    ("[2] Google. Site Reliability Engineering: How Google Runs Production Systems[M]. O'Reilly Media, 2016.", False, True),
    ("[3] Kubernetes Authors. Kubernetes Documentation[EB/OL]. https://kubernetes.io/docs/", False, True),
    ("[4] Prometheus Authors. Prometheus Documentation[EB/OL]. https://prometheus.io/docs/", False, True),
    ("[5] Chaos Mesh Authors. Chaos Mesh Documentation[EB/OL]. https://chaos-mesh.org/docs/", False, True),
    ("[6] Motter A E, Lai Y C. Cascade-based attacks on complex networks[J]. Physical Review E, 2002, 66(6): 065102.", False, True),
    ("[7] Tarjan R. Depth-first search and linear graph algorithms[J]. SIAM Journal on Computing, 1972, 1(2): 146-160.", False, True),
]
fill_cell(t5.cell(3, 2), science)

promo = [
    ("一、使用说明", True, False),
    ("平台为浏览器端应用，无需安装客户端：打开页面即可新建工程，从左侧节点库拖入或点击添加节点，拖动端口连线，右侧属性面板修改资源与端口；点“校验可运行性”得到评分与问题清单，点“推演分析”查看影响范围与路径，点“恢复方案”比较三种策略，点“增强分析”查看关键单点、隔离切口、恢复序列与级联风险，点“AI 诊断”获取解释，点“历史”回看每次结果。拓扑可通过“导出/导入”以 JSON 形式备份与迁移。", False, True),
    ("二、技术特点与优势", True, False),
    ("1. 装配即校验：12 条规则覆盖端口、资源、依赖、网络、入口、环路、冗余，问题定位到节点与连线，并给出修复建议。", False, True),
    ("2. 接口级推演：依赖边必须落到端口，影响路径精确到“哪个服务的哪个端口调用了哪个端口”。", False, True),
    ("3. 决策可比较：三策略五维评分 + 多动作序列 + 级联风险，回答“先救谁、救完会不会更糟”。", False, True),
    ("4. 结论可复现：确定性算法，结果落库并带 traceId，适合教学评分与方案评审。", False, True),
    ("5. 部署轻量：单机 H2 即可演示，生产可切 MySQL；前端静态资源 + 后端 jar，Docker Compose 一键编排。", False, True),
    ("三、适用范围与推广前景", True, False),
    ("适用于高校云计算/软件工程课程的故障演练实验、企业变更前的影响评估、系统韧性方案评审、以及运维团队的故障复盘训练。相比商业混沌平台，本作品部署成本低、学习曲线平缓，适合作为教学与初阶演练工具；后续可扩展真实监控接入与 K8s 拓扑导入，向企业内训与方案预评估场景推广。", False, True),
    ("四、市场与经济效益（定性）", True, False),
    ("云原生可观测性与混沌工程市场持续增长，但商业平台授权费用高、实施周期长，中小团队与高校教学缺少轻量替代。本作品以开源技术栈实现核心能力，可按“教学授权 + 定制化拓扑建模 + 演练报告”方式推广，具备一定的课程共建与培训服务空间。", False, True),
]
fill_cell(t5.cell(8, 2), promo)

overview = [
    ("随着云计算、微服务与云原生技术的普及，国内外在故障检测、传播分析、可靠性评估与恢复决策方面已形成较完整的技术谱系。", False, True),
    ("国外方面：Google 的 SRE 体系把可靠性工程化；Kubernetes 通过健康检查、副本与自愈机制提升可用性；Prometheus 提供指标采集与告警；Jaeger、OpenTelemetry 提供分布式追踪；Chaos Mesh、Gremlin、AWS Fault Injection Simulator 提供故障注入与实验编排；Datadog、Dynatrace 等商业平台把可观测性与根因分析结合。总体特点是“监控—追踪—注入”分工明确，生产级能力强，但学习与实施成本高。", False, True),
    ("国内方面：阿里 ChaosBlade、腾讯蓝鲸、华为云 AOM 等围绕混沌工程、运维编排与可观测性持续投入；学术界在级联失效、关键节点识别、韧性评估等方向积累较多。整体趋势是从“被动告警”走向“主动演练”，从“单点指标”走向“拓扑与依赖分析”。", False, True),
    ("现有工具的共性不足：一是多在故障发生之后介入，缺少装配阶段的可运行性预检；二是依赖关系多停留在“服务到服务”，较少细化到端口与接口；三是恢复方案多以经验或单指标排序，缺少多目标比较与“救援会不会引发二次故障”的判断；四是教学与评审场景缺少可复现、可解释、部署轻量的沙盘。", False, True),
    ("本作品正是在这一背景下，把成熟算法（BFS、割点、加权评分、级联失效）组合成一条“装配预检—故障推演—恢复比较—复盘回看”的证据链：用 12 条规则回答“能不能跑”，用容量感知、冗余度与时间窗回答“传多远、多久到”，用五维评分、多动作序列与级联分析回答“先救谁、会不会越救越糟”，并以确定性计算和落库快照保证结果可复现。与单一监控、追踪或注入工具相比，本作品的差异化在于端口级建模、决策比较与教学可复现性。", False, True),
    ("目前作品处于实验室原型阶段，尚未接入生产监控数据，恢复时间与成本为模型估算；后续计划接入 Prometheus 与 K8s YAML，使用真实拓扑与故障记录校准模型，并开展更大规模拓扑的性能与准确性验证。", False, True),
]
fill_cell(doc.tables[6].cell(0, 0), overview)

doc.save(DST)
print("saved", DST)

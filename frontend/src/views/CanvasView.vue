<template>
  <div class="app">
    <!-- 端口选择器（端口多于 4 个时用） -->
    <div v-if="portPicker" class="dialog-mask" @click.self="portPicker = null">
      <div class="dialog port-dialog">
        <div class="dialog-title">
          {{ portPicker.mode === 'source' ? '选择源端口' : '选择目标端口' }}
        </div>
        <div class="hint">{{ pickerNode ? pickerNode.name : '' }} · 共 {{ pickerPorts.length }} 个端口</div>
        <div class="port-list">
          <button v-for="p in pickerPorts" :key="p.id" class="port-item" @click="pickPort(p)">
            <b>{{ p.port }}</b>
            <span>{{ p.protocol }} · {{ p.scope }}</span>
            <em v-if="p.purpose">{{ p.purpose }}</em>
          </button>
        </div>
        <div class="dialog-actions">
          <button @click="portPicker = null">取消</button>
        </div>
      </div>
    </div>

    <!-- 页面内输入弹窗（避免浏览器拦截 prompt/confirm） -->
    <div v-if="dialog" class="dialog-mask" @pointerdown.self="dialog = null">
      <div class="dialog">
        <div class="dialog-title">{{ dialog.title }}</div>
        <label>{{ dialog.label }}</label>
        <input v-model="dialog.value" autofocus @keyup.enter="submitDialog" />
        <template v-if="dialog.action === 'port'">
          <label>用途（可选，例如 MySQL 协议 / 监控端口）</label>
          <input v-model="dialog.purpose" @keyup.enter="submitDialog" />
        </template>
        <div class="dialog-actions">
          <button @click="cancelDialog">取消</button>
          <button class="primary" @click="submitDialog">确定</button>
        </div>
      </div>
    </div>

    <!-- 历史推演抽屉 -->
    <div v-if="historyOpen" class="overlay" @click.self="historyOpen = false">
      <div class="history-modal">
        <header class="history-head">
          <b>推演历史</b>
          <span class="meta">共 {{ historyTotal }} 条</span>
          <div class="spacer"></div>
          <button @click="historyOpen = false">关闭</button>
        </header>

        <div class="history-tabs">
          <button :class="{ active: historyTab === 'analysis' }" @click="switchHistoryTab('analysis')">
            推演记录
          </button>
          <button :class="{ active: historyTab === 'recovery' }" @click="switchHistoryTab('recovery')">
            恢复方案
          </button>
        </div>

        <div class="history-body" v-if="historyTab === 'analysis'">
          <div class="history-list">
            <div v-for="h in historyItems" :key="h.id"
                 class="history-item"
                 :class="{ active: historyDetail && historyDetail.id === h.id }"
                 @click="loadHistoryDetail(h.id)">
              <div class="hi-time">{{ (h.analysisTime || '').replace('T', ' ') }}</div>
              <div class="hi-summary">{{ h.summary }}</div>
              <div class="hi-meta">直接 {{ h.directCount }} ｜ 间接 {{ h.indirectCount }}</div>
            </div>
            <div v-if="!historyItems.length" class="hint">还没有推演记录，先做一次「推演分析」</div>
          </div>

          <div class="history-detail">
            <div v-if="!historyDetail" class="hint">点左侧记录看详情</div>
            <template v-else>
              <div class="summary">{{ historyDetail.summary }}</div>
              <div class="hint" v-if="historyDetail.windowSeconds">时间窗：{{ historyDetail.windowSeconds }} 秒</div>
              <div class="hint" v-if="historyDetail.cascadeSummary">级联：{{ historyDetail.cascadeSummary }}</div>

              <div class="hint">故障源</div>
              <div v-for="s in historyDetail.faultSources" :key="s.faultId" class="fault">
                <b>{{ s.nodeName }}</b> · {{ s.faultType }} · {{ s.severity }}
              </div>
              <div v-if="!historyDetail.faultSources.length" class="hint">无</div>

              <div class="hint" style="margin-top: 10px">受影响节点（{{
                  historyDetail.affectedNodes.length }}）</div>
              <div v-for="a in historyDetail.affectedNodes" :key="a.nodeId" class="issue">
                <span class="sev"
                      :style="{ background: a.impactLevel === 'DIRECT' ? '#f97316' : '#eab308' }">
                  {{ a.impactLevel }}
                </span>
                <b>{{ a.nodeName }}</b>
                <span class="msg">距离 {{ a.distance }}</span>
                <span class="sug">{{ a.path.map((p) => p.nodeName).join(' → ') }}</span>
              </div>
            </template>
          </div>
        </div>

        <div class="history-body" v-else>
          <div class="history-list">
            <div v-for="p in recoveryPlans" :key="p.id"
                 class="history-item"
                 :class="{ active: recoveryDetail && recoveryDetail.id === p.id }"
                 @click="recoveryDetail = p">
              <div class="hi-time">{{ (p.createdAt || '').replace('T', ' ') }}</div>
              <div class="hi-summary">推荐：{{ p.recommendedName || '无适用策略' }}</div>
              <div class="hi-meta">{{ (p.strategies || []).length }} 个策略</div>
            </div>
            <div v-if="!recoveryPlans.length" class="hint">还没有恢复方案，先点顶部「恢复方案」</div>
          </div>

          <div class="history-detail">
            <div v-if="!recoveryDetail" class="hint">点左侧记录看策略明细</div>
            <template v-else>
              <div class="summary">{{ recoveryDetail.recommendationReason }}</div>
              <div v-for="s in recoveryDetail.strategies" :key="s.type" class="strategy">
                <div class="strategy-head">
                  <b>{{ s.name }}</b>
                  <span :class="['pill', s.applicable ? 'ok' : 'no']">{{ s.applicable ? '适用' : '不适用' }}</span>
                  <span class="score">{{ s.score }}</span>
                  <span class="meta">
                    {{ s.estimatedRecoverySeconds }}s ｜ 成本 {{ s.costLevel }} ｜ 风险 {{ s.riskLevel }}
                  </span>
                </div>
                <div class="strategy-detail" v-if="!s.applicable">{{ s.inapplicableReason }}</div>
              </div>
            </template>
          </div>
        </div>

        <footer class="history-foot" v-if="historyTab === 'analysis'">
          <button :disabled="historyPage <= 1" @click="historyPage--; loadHistory()">上一页</button>
          <span class="meta">
            第 {{ historyPage }} / {{ Math.max(1, Math.ceil(historyTotal / historySize)) }} 页
          </span>
          <button :disabled="historyPage * historySize >= historyTotal"
                  @click="historyPage++; loadHistory()">下一页</button>
        </footer>
      </div>
    </div>

    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">云镜推演<span class="sub">故障推演台</span></div>

      <select class="proj" :value="projectId" @change="onSwitchProject">
        <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
      </select>
      <button @click="onCreateProject">+ 新建工程</button>
      <button @click="onDeleteProject">删除工程</button>
      <button @click="autoLayout">自动布局</button>
      <button @click="exportTopology">导出</button>
      <button @click="triggerImport">导入</button>
      <input ref="fileRef" type="file" accept="application/json,.json" style="display: none"
             @change="onImportFile" />

      <div class="spacer"></div>

      <button @click="zoomBy(0.9)">－</button>
      <span class="zoom">{{ Math.round(view.scale * 100) }}%</span>
      <button @click="zoomBy(1.1)">＋</button>
      <button @click="fitView">适应</button>

      <button class="primary" :disabled="busy || !projectId" @click="onValidate">▶ 校验可运行性</button>
      <button :disabled="busy || !projectId" @click="onAnalyze">◎ 推演分析</button>
      <button :disabled="busy || !projectId" @click="onRecovery">✚ 恢复方案</button>
      <button :disabled="busy || !projectId" @click="onInsights">⌁ 增强分析</button>
      <button :disabled="busy || !projectId" @click="onAiDiagnose">✦ AI 诊断</button>
      <button :disabled="busy || !projectId" @click="onOpenHistory">🕘 历史</button>
    </header>

    <div class="main">
      <!-- 左：节点库 -->
      <aside class="palette" :class="{ collapsed: !leftOpen }">
        <div class="panel-title">
          节点库
          <button class="collapse-btn" title="收起" @click="leftOpen = false">«</button>
        </div>
        <input v-model="paletteSearch" class="palette-search" placeholder="搜索节点类型…" />

        <div v-for="group in paletteGroups" :key="group.name" class="palette-group-wrap">
          <div class="palette-group">{{ group.name }}</div>
          <div class="palette-item" v-for="k in group.items" :key="k.kind" @click="addNode(k.kind)">
            <span class="dot" :style="{ background: k.color }"></span>
            <div>
              <div class="palette-label">{{ k.label }}</div>
              <div class="palette-hint">{{ k.hint }}</div>
            </div>
          </div>
        </div>

        <div class="panel-title" style="margin-top: 14px">连线模式</div>
        <select v-model="connectKind">
          <option v-for="e in EDGE_KINDS" :key="e.kind" :value="e.kind">
            {{ e.label }}（{{ e.hint }}）
          </option>
        </select>
        <div class="hint">点节点端口 → 点目标端口即可连线</div>
      </aside>

      <!-- 中：无限画布 -->
      <div class="canvas-wrap">
        <svg
          ref="svgRef"
          class="canvas"
          @wheel.prevent="onWheel"
          @pointerdown="onCanvasDown"
        >
          <defs>
            <!-- 点阵网格，比线条更干净 -->
            <pattern id="grid" width="24" height="24" patternUnits="userSpaceOnUse">
              <circle cx="1.2" cy="1.2" r="1.2" fill="#1e293b" />
            </pattern>

            <!-- 按类型生成节点渐变底 -->
            <linearGradient v-for="k in NODE_KINDS" :id="'grad-' + k.kind"
                            x1="0" y1="0" x2="0" y2="1" :key="'grad-' + k.kind">
              <stop offset="0%" :stop-color="k.color" stop-opacity="0.24" />
              <stop offset="100%" :stop-color="k.color" stop-opacity="0.05" />
            </linearGradient>

            <filter id="node-shadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="#000" flood-opacity="0.45" />
            </filter>
          </defs>

          <g :transform="`translate(${view.tx}, ${view.ty}) scale(${view.scale})`">
            <rect x="-200000" y="-200000" width="400000" height="400000" fill="url(#grid)" />
            <rect x="-200000" y="-200000" width="400000" height="400000" fill="transparent" />

            <!-- 连线 -->
            <g v-for="g in edgeGeoms" :key="g.id">
              <!-- 加宽的透明命中区：点得中、删得掉 -->
              <path
                class="edge-hit"
                :d="g.d"
                @pointerdown.stop="onEdgeDown(g)"
                @contextmenu.prevent.stop="openMenu($event, 'edge', g.id)"
                @pointerenter="hoverEdgeId = g.id"
                @pointerleave="hoverEdgeId = null"
              />
              <path
                :id="'edge-path-' + g.id"
                :d="g.d"
                fill="none"
                :stroke="g.problem ? '#ef4444' : g.color"
                :stroke-width="g.active ? 3 : (hoverEdgeId === g.id ? 3.2 : 2)"
                :stroke-dasharray="g.active ? '10 6' : (g.dash || null)"
                :class="{ 'edge-active': g.active, 'edge-problem': g.problem && !g.active }"
                :opacity="hoverEdgeId === g.id ? 1 : 0.9"
                pointer-events="none"
              />
              <!-- 传播路径上的流动光点 -->
              <circle v-if="g.active" r="3.5" :fill="g.color" pointer-events="none">
                <animateMotion dur="1.4s" repeatCount="indefinite" rotate="auto">
                  <mpath :href="'#edge-path-' + g.id" />
                </animateMotion>
              </circle>
              <text :x="g.mid.x" :y="g.mid.y - 4" class="edge-label"
                    :fill="g.problem ? '#ef4444' : g.color" pointer-events="none">
                {{ g.label }}
              </text>
            </g>

            <!-- 连线拖拽中的临时线 -->
            <path v-if="pending && tempPoint"
                  :d="tempEdgePath"
                  fill="none" stroke="#f59e0b" stroke-width="2"
                  stroke-dasharray="5 4" pointer-events="none" />

            <!-- 节点 -->
            <g
              v-for="n in nodes"
              :key="n.id"
              :data-node-id="n.id"
              :transform="`translate(${n.posX}, ${n.posY})`"
              @pointerdown.stop="onNodeDown($event, n)"
              @contextmenu.prevent.stop="openMenu($event, 'node', n.id)"
            >
              <rect
                :width="NODE_W" :height="NODE_H" rx="12"
                :fill="nodeFill(n)"
                :stroke="ringColor(n) || kindMeta(n.nodeKind).color"
                :stroke-width="ringColor(n) ? 2.6 : 1.3"
                filter="url(#node-shadow)"
                :class="{ 'node-fault': faultSourceIds.has(n.id), 'node-selected': selectedNode && selectedNode.id === n.id }"
              />

              <!-- 图标块 -->
              <rect x="9" y="10" width="24" height="24" rx="7"
                    :fill="kindMeta(n.nodeKind).color" opacity="0.22" />
              <text :x="21" :y="28" class="node-icon" text-anchor="middle">{{ KIND_ICON[n.nodeKind] }}</text>

              <text :x="41" :y="21" class="node-name">{{ n.name }}</text>
              <text :x="41" :y="38" class="node-meta">{{ kindMeta(n.nodeKind).label }}</text>
              <text :x="41" :y="56" class="node-meta">{{ cpuLabel(n) }} / {{ memLabel(n) }}</text>

              <!-- 左侧端口（接收方） -->
              <g v-for="(p, i) in portsOf(n, 'left')" :key="'l' + p.id"
                 class="port"
                 :data-node-id="n.id"
                 :data-port-id="p.id"
                 @pointerdown.stop="onPortDown($event, n, p)">
                <circle :cx="0" :cy="22 + i * 15" r="10" fill="transparent" />
                <circle :cx="0" :cy="22 + i * 15" r="4.5"
                        :fill="p.scope === 'public' ? '#f59e0b' : '#38bdf8'"
                        stroke="#0b1220" stroke-width="1.2" />
                <text :x="10" :y="22 + i * 15 + 3.5" class="port-label left">{{ p.port }}</text>
                <title>{{ p.port }}/{{ p.protocol }}（{{ p.scope }}）{{ p.purpose ? ' · ' + p.purpose : '' }}</title>
              </g>

              <!-- 右侧端口（调用方） -->
              <g v-for="(p, i) in portsOf(n, 'right')" :key="'r' + p.id"
                 class="port"
                 :data-node-id="n.id"
                 :data-port-id="p.id"
                 @pointerdown.stop="onPortDown($event, n, p)">
                <circle :cx="NODE_W" :cy="22 + i * 15" r="10" fill="transparent" />
                <circle :cx="NODE_W" :cy="22 + i * 15" r="4.5"
                        :fill="p.scope === 'public' ? '#f59e0b' : '#38bdf8'"
                        stroke="#0b1220" stroke-width="1.2" />
                <text :x="NODE_W - 10" :y="22 + i * 15 + 3.5" class="port-label">{{ p.port }}</text>
                <title>{{ p.port }}/{{ p.protocol }}（{{ p.scope }}）{{ p.purpose ? ' · ' + p.purpose : '' }}</title>
              </g>

              <!-- 端口超过 4 个：折叠成可点击的 +N -->
              <g v-if="hiddenPortCount(n) > 0" class="port-more-btn"
                 @pointerdown.stop="openPortPicker(n, 'source')">
                <circle :cx="NODE_W" :cy="NODE_H - 10" r="10" fill="transparent" />
                <text :x="NODE_W - 10" :y="NODE_H - 8" class="port-label">+{{ hiddenPortCount(n) }}</text>
              </g>

              <!-- 选中时右上角的删除按钮（比快捷键更好找） -->
              <g v-if="selectedNode && selectedNode.id === n.id"
                 class="node-del"
                 @pointerdown.stop="onDeleteNode">
                <circle :cx="NODE_W - 3" cy="3" r="9" fill="#7f1d1d" stroke="#ef4444" stroke-width="1" />
                <text :x="NODE_W - 3" :y="7" text-anchor="middle" fill="#fff" font-size="12">×</text>
                <title>删除节点</title>
              </g>

              <text v-if="pending && pending.nodeId === n.id" :x="NODE_W + 10" :y="12" class="pending">源</text>
            </g>
          </g>
        </svg>

        <div v-if="!nodes.length" class="empty">左侧点一个节点类型开始装配</div>
        <div class="legend">
          <span v-for="k in NODE_KINDS" :key="k.kind">
            <i :style="{ background: k.color }"></i>{{ k.label.split(' ')[0] }}
          </span>
        </div>
        <div class="canvas-tip">拖动空白平移 · 滚轮缩放 · 拖端口连线 · Delete 删除选中</div>
        <button v-if="!leftOpen" class="expand-btn left" title="展开节点库" @click="leftOpen = true">☰</button>
        <button v-if="!rightOpen" class="expand-btn right" title="展开属性" @click="rightOpen = true">▤</button>

        <!-- 右键菜单 -->
        <div v-if="contextMenu" class="ctx-menu"
             :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
          <div class="ctx-title">{{ contextMenu.type === 'node' ? '节点' : '连线' }}</div>
          <button @click="menuDelete">
            🗑 删除{{ contextMenu.type === 'node' ? '节点' : '连线' }}
          </button>
          <button v-if="contextMenu.type === 'node'" @click="menuAddPort">＋ 添加端口</button>
          <button v-if="contextMenu.type === 'node'" @click="menuInjectFault">⚡ 注入故障</button>
          <button @click="contextMenu = null">取消</button>
        </div>

        <div v-if="toast" class="toast">{{ toast }}</div>
      </div>

      <!-- 右：属性面板 -->
      <aside class="inspector" :class="{ collapsed: !rightOpen }">
        <div class="panel-title">
          属性
          <button class="collapse-btn" title="收起" @click="rightOpen = false">»</button>
        </div>

        <template v-if="selectedNode">
          <label>名称</label>
          <input v-model="selectedNode.name" />

          <label>类型</label>
          <select v-model="selectedNode.nodeKind" @change="onKindChange">
            <option v-for="k in NODE_KINDS" :key="k.kind" :value="k.kind">{{ k.label }}</option>
          </select>

          <label>型号（自动填资源）</label>
          <select v-model="selectedNode.modelId" @change="onModelChange">
            <option :value="null">不指定</option>
            <option v-for="m in modelsForKind" :key="m.id" :value="m.id">{{ m.modelName }}</option>
          </select>

          <div class="grid2">
            <div><label>CPU 核数</label><input type="number" step="0.5" v-model.number="selectedNode.cpuCores" /></div>
            <div><label>CPU 请求</label><input type="number" step="0.5" v-model.number="selectedNode.cpuRequest" /></div>
            <div><label>内存 MB</label><input type="number" v-model.number="selectedNode.memTotalMb" /></div>
            <div><label>内存请求</label><input type="number" v-model.number="selectedNode.memRequestMb" /></div>
            <div><label>磁盘 GB</label><input type="number" v-model.number="selectedNode.diskGb" /></div>
            <div><label>状态</label>
              <select v-model="selectedNode.status">
                <option value="healthy">healthy</option>
                <option value="degraded">degraded</option>
                <option value="down">down</option>
              </select>
            </div>
          </div>

          <label>端口</label>
          <div class="ports">
            <span v-for="p in selectedNode.ports" :key="p.id" class="port-chip">
              {{ p.port }}/{{ p.protocol }} <em>{{ p.scope }}</em>
            </span>
            <button class="tiny" @click="onAddPort">+ 端口</button>
          </div>

          <div class="row">
            <button class="primary" @click="onSaveNode">保存</button>
            <button class="danger" @click="onDeleteNode">删除节点</button>
          </div>

          <label>注入故障</label>
          <div class="row">
            <select v-model="faultType">
              <option value="node_down">宕机</option>
              <option value="service_unavailable">服务不可用</option>
              <option value="network_latency">网络延迟</option>
              <option value="network_unreachable">网络不可达</option>
              <option value="cpu_saturation">CPU 打满</option>
            </select>
            <button @click="onInjectFault">注入</button>
          </div>
        </template>

        <template v-else-if="selectedEdge">
          <label>连线类型</label>
          <div>{{ edgeMeta(selectedEdge.edgeKind).label }}</div>
          <label>端点</label>
          <div>{{ nodeName(selectedEdge.sourceNodeId) }} → {{ nodeName(selectedEdge.targetNodeId) }}</div>
          <div class="row" style="margin-top: 10px">
            <button class="danger" @click="onDeleteEdge">删除连线</button>
          </div>
        </template>

        <template v-else>
          <div class="hint">点画布上的节点或连线查看属性</div>
        </template>
      </aside>
    </div>

    <!-- 底部面板 -->
    <div class="dock">
      <div class="tabs">
        <button :class="{ active: dockTab === 'problems' }" @click="dockTab = 'problems'">
          问题 {{ validation ? validation.result.items.length : 0 }}
        </button>
        <button :class="{ active: dockTab === 'faults' }" @click="dockTab = 'faults'">
          故障 {{ faults.length }}
        </button>
        <button :class="{ active: dockTab === 'analysis' }" @click="dockTab = 'analysis'">推演</button>
        <button :class="{ active: dockTab === 'recovery' }" @click="dockTab = 'recovery'">恢复方案</button>
        <button :class="{ active: dockTab === 'insights' }" @click="dockTab = 'insights'">增强</button>
        <button :class="{ active: dockTab === 'ai' }" @click="dockTab = 'ai'">AI 诊断</button>

        <div class="spacer"></div>
        <span class="status">
          节点 {{ nodes.length }} ｜ 连线 {{ edges.length }}
          <template v-if="validation">
            ｜ {{ validation.result.runnable ? '可运行' : '不可运行' }} · {{ validation.result.score }} 分
            （{{ validation.result.errorCount }} 错 / {{ validation.result.warnCount }} 警）
          </template>
        </span>
      </div>

      <div class="dock-body">
        <!-- 问题 -->
        <div v-if="dockTab === 'problems'">
          <div v-if="!validation" class="hint">点顶部「校验可运行性」生成问题清单</div>
          <div v-else>
            <div class="summary">{{ validation.result.summary }}</div>
            <div class="issue" v-for="(it, i) in validation.result.items" :key="i" @click="focusNode(it.nodeIds)">
              <span class="sev" :style="{ background: SEVERITY_COLOR[it.severity] }">{{ it.severity }}</span>
              <b>{{ it.ruleCode }}</b>
              <span class="msg">{{ it.message }}</span>
              <span class="sug">{{ it.suggestion }}</span>
            </div>
            <div v-if="!validation.result.items.length" class="hint">没有发现问题 🎉</div>
          </div>
        </div>

        <!-- 故障 -->
        <div v-if="dockTab === 'faults'">
          <div v-if="!faults.length" class="hint">还没有注入故障</div>
          <div class="fault" v-for="f in faults" :key="f.id">
            <b>{{ f.nodeName }}</b> · {{ f.faultType }} · {{ f.severity }}
            <span class="sev" :style="{ background: f.status === 'active' ? '#ef4444' : '#475569' }">{{ f.status }}</span>
            <button v-if="f.status === 'active'" class="tiny" @click="onRecoverFault(f)">恢复</button>
          </div>
        </div>

        <!-- 推演 -->
        <div v-if="dockTab === 'analysis'">
          <div v-if="!analysis" class="hint">点顶部「推演分析」计算故障波及范围</div>
          <div v-else>
            <div class="summary">{{ analysis.summary }}</div>
            <div class="issue" v-for="a in analysis.affectedNodes" :key="a.nodeId" @click="focusNode([a.nodeId])">
              <span class="sev" :style="{ background: a.impactLevel === 'DIRECT' ? '#f97316' : '#eab308' }">
                {{ a.impactLevel }}
              </span>
              <b>{{ a.nodeName }}</b>
              <span class="msg">距离 {{ a.distance }}</span>
              <span class="msg">强度 {{ a.pathStrength }}</span>
              <span v-if="a.dependencyRatio < 1" class="msg">冗余度 {{ a.dependencyRatio }}</span>
              <span v-if="a.arrivalMillis" class="msg">+{{ (a.arrivalMillis / 1000).toFixed(1) }}s</span>
              <span class="sug">{{ a.path.map(p => p.nodeName).join(' → ') }}</span>
            </div>
            <div v-if="!analysis.affectedNodes.length" class="hint">没有其它节点受影响</div>
          </div>
        </div>

        <!-- 恢复 -->
        <div v-if="dockTab === 'recovery'">
          <div v-if="!recovery" class="hint">点顶部「恢复方案」生成策略对比</div>
          <div v-else>
            <div class="summary">
              推荐：<b>{{ recovery.recommendedName || '无适用策略' }}</b> ｜ {{ recovery.recommendationReason }}
            </div>
            <div class="strategy" v-for="s in recovery.strategies" :key="s.type">
              <div class="strategy-head">
                <b>{{ s.name }}</b>
                <span :class="['pill', s.applicable ? 'ok' : 'no']">{{ s.applicable ? '适用' : '不适用' }}</span>
                <span class="score">{{ s.score }}</span>
                <span class="meta">{{ s.estimatedRecoverySeconds }}s ｜ 成本{{ s.cost }} ｜ 风险{{ s.risk }}</span>
              </div>
              <div class="strategy-detail">
                <span v-if="!s.applicable">{{ s.inapplicableReason }}</span>
                <span v-else>{{ s.advantages[0] }}</span>
              </div>
            </div>
          </div>
        </div>
        <!-- AI 诊断 -->
        <div v-if="dockTab === 'ai'">
          <div class="insight-row" v-if="aiResult">
            <b>来源</b>
            <span class="chip">{{ sourceLabel(aiResult.source) }}</span>
            <span class="meta">{{ aiResult.online ? '已连接 AI 服务' : '未连接 AI 服务，使用本地规则' }}</span>
          </div>
          <pre v-if="aiResult" class="ai-text">{{ aiResult.content }}</pre>
          <div v-else class="hint">
            点顶部「✦ AI 诊断」：会带上当前故障与传播结果去问 AI；
            没启动 AI 服务（8010）时自动降级为规则诊断。
          </div>
        </div>

        <!-- 增强分析 -->
        <div v-if="dockTab === 'insights'">
          <div v-if="!insights" class="hint">点顶部「增强分析」计算关键单点 / 隔离切口 / 恢复序列</div>
          <div v-else>
            <div class="summary">{{ insights.note }}</div>
            <div class="insight-row">
              <b>关键单点</b>
              <span v-if="!insights.criticalNodes.length" class="hint">没有割点，拓扑没有单点瓶颈</span>
              <span v-for="c in insights.criticalNodes" :key="c.nodeId" class="chip"
                    @click="focusNode([c.nodeId])">
                {{ c.nodeName }}（依赖图断成 {{ c.componentsAfterRemoval }} 块）
              </span>
            </div>
            <div class="insight-row" v-if="insights.isolation">
              <b>隔离切口</b>
              <span class="chip">{{ insights.isolation.cutNodeNames.join('、') || '无需隔离' }}</span>
              <span class="meta">
                可达 {{ insights.isolation.beforeCount }} → {{ insights.isolation.afterCount }}
                （截断 {{ insights.isolation.blockedCount }}）
              </span>
            </div>
            <div class="insight-row" v-if="insights.sequence && insights.sequence.best">
              <b>恢复序列</b>
              <span class="chip">总分 {{ insights.sequence.best.score }}</span>
              <span>{{ insights.sequence.best.description }}</span>
            </div>
            <div class="insight-row" v-else-if="insights.sequence">
              <b>恢复序列</b>
              <span class="hint">{{ insights.sequence.summary }}</span>
            </div>
            <div class="insight-row" v-if="insights.cascade">
              <b>级联风险</b>
              <span class="chip">{{ insights.cascade.events.length }} 个节点被拖垮</span>
              <span class="meta">{{ insights.cascade.summary }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as api from '../api'
import { EDGE_KINDS, KIND_ICON, NODE_KINDS, SEVERITY_COLOR, edgeMeta, kindMeta } from '../constants/topology'

const NODE_W = 180
const NODE_H = 76

const projects = ref([])
const projectId = ref(null)
const nodes = ref([])
const edges = ref([])
const groups = ref([])
const models = ref([])
const faults = ref([])

const view = reactive({ scale: 1, tx: 80, ty: 60 })
const svgRef = ref(null)

const selection = ref(null)
const pending = ref(null)
const hoverEdgeId = ref(null)
const tempPoint = ref(null)
const connectKind = ref('depends_on')
const leftOpen = ref(true)
const rightOpen = ref(true)
const contextMenu = ref(null)
const dialog = ref(null)
const connectDraft = ref(null)
const portPicker = ref(null)
const paletteSearch = ref('')
const fileRef = ref(null)
let deletingEdge = false
const faultType = ref('node_down')

const validation = ref(null)
const analysis = ref(null)
const recovery = ref(null)
const insights = ref(null)
const aiResult = ref(null)
const dockTab = ref('insights')

// 历史抽屉
const historyOpen = ref(false)
const historyItems = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historySize = 8
const historyDetail = ref(null)
const historyTab = ref('analysis')
const recoveryPlans = ref([])
const recoveryDetail = ref(null)

const busy = ref(false)
const toast = ref('')
let toastTimer = null
let drag = null
let pan = null
let viewTimer = null

const selectedNode = computed(() => {
  if (!selection.value || selection.value.type !== 'node') return null
  return nodes.value.find((n) => n.id === selection.value.id) || null
})

const selectedEdge = computed(() => {
  if (!selection.value || selection.value.type !== 'edge') return null
  return edges.value.find((e) => e.id === selection.value.id) || null
})

const modelsForKind = computed(() =>
  models.value.filter((m) => m.kind === (selectedNode.value ? selectedNode.value.nodeKind : ''))
)

const currentProjectName = computed(() => {
  const p = projects.value.find((x) => x.id === projectId.value)
  return p ? p.name : 'topology'
})

/** 节点库：按分组展示 + 搜索过滤 */
const paletteGroups = computed(() => {
  const q = (paletteSearch.value || '').trim().toLowerCase()
  const filtered = NODE_KINDS.filter((k) =>
    !q || k.label.toLowerCase().includes(q) || k.kind.includes(q) || (k.hint || '').toLowerCase().includes(q)
  )
  const groups = []
  for (const k of filtered) {
    const name = k.category || '其它'
    let g = groups.find((x) => x.name === name)
    if (!g) {
      g = { name, items: [] }
      groups.push(g)
    }
    g.items.push(k)
  }
  return groups
})

const pickerNode = computed(() =>
  portPicker.value ? nodes.value.find((n) => n.id === portPicker.value.nodeId) : null
)

const pickerPorts = computed(() => (pickerNode.value ? pickerNode.value.ports : []))

const issuesByNode = computed(() => {
  const map = new Map()
  if (!validation.value) return map
  for (const item of validation.value.result.items) {
    for (const id of item.nodeIds || []) {
      if (!map.has(id)) map.set(id, [])
      map.get(id).push(item)
    }
  }
  return map
})

const affectedByNode = computed(() => {
  const map = new Map()
  if (!analysis.value) return map
  for (const a of analysis.value.affectedNodes) map.set(a.nodeId, a)
  return map
})

/** 激活故障所在节点（画布上做脉冲） */
const faultSourceIds = computed(() =>
  new Set(faults.value.filter((f) => f.status === 'active').map((f) => f.nodeId))
)

/** 端口朝向：被依赖多的放左边（接收方），发起连接多的放右边（调用方） */
const portSide = computed(() => {
  const map = new Map()
  const asSource = new Map()
  const asTarget = new Map()
  for (const e of edges.value) {
    if (e.sourcePortId) {
      asSource.set(e.sourcePortId, (asSource.get(e.sourcePortId) || 0) + 1)
    }
    if (e.targetPortId) {
      asTarget.set(e.targetPortId, (asTarget.get(e.targetPortId) || 0) + 1)
    }
  }
  for (const n of nodes.value) {
    for (const p of n.ports || []) {
      const s = asSource.get(p.id) || 0
      const t = asTarget.get(p.id) || 0
      map.set(p.id, t > s ? 'left' : 'right')
    }
  }
  return map
})

function sideOfPort(p) {
  return portSide.value.get(p.id) || 'right'
}

/** 某一侧最多画 4 个端口，多出来的折叠成 +N */
function portsOf(node, side) {
  return (node.ports || []).filter((p) => sideOfPort(p) === side).slice(0, 4)
}

function hiddenPortCount(node) {
  const shown = portsOf(node, 'left').length + portsOf(node, 'right').length
  return Math.max(0, (node.ports || []).length - shown)
}
const propagationEdgeIds = computed(() => {
  const ids = new Set()
  if (!analysis.value) return ids
  for (const a of analysis.value.affectedNodes) {
    for (const hop of a.path || []) {
      if (hop.edgeId) ids.add(hop.edgeId)
    }
  }
  return ids
})

/** 校验问题涉及的连线（红色流动告警） */
const problemEdgeIds = computed(() => {
  const ids = new Set()
  if (!validation.value) return ids
  for (const item of validation.value.result.items) {
    for (const id of item.edgeIds || []) ids.add(id)
  }
  return ids
})

const edgeGeoms = computed(() => {
  const result = []
  for (const e of edges.value) {
    const s = nodes.value.find((n) => n.id === e.sourceNodeId)
    const t = nodes.value.find((n) => n.id === e.targetNodeId)
    if (!s || !t) continue
    const a = e.sourcePortId ? portPoint(s, e.sourcePortId) : { x: s.posX + NODE_W, y: s.posY + NODE_H / 2 }
    const b = e.targetPortId ? portPoint(t, e.targetPortId) : { x: t.posX, y: t.posY + NODE_H / 2 }
    const dx = Math.max(50, Math.abs(b.x - a.x) * 0.5)
    const rightward = b.x >= a.x
    const c1x = rightward ? a.x + dx : a.x - dx
    const c2x = rightward ? b.x - dx : b.x + dx
    const meta = edgeMeta(e.edgeKind)
    result.push({
      id: e.id,
      d: `M ${a.x} ${a.y} C ${c1x} ${a.y}, ${c2x} ${b.y}, ${b.x} ${b.y}`,
      color: meta.color,
      dash: meta.dash,
      label: meta.label,
      mid: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 },
      active: propagationEdgeIds.value.has(e.id),
      problem: problemEdgeIds.value.has(e.id),
      sourceFaultId: e.sourceNodeId
    })
  }
  return result
})

/** 端口坐标：根据端口所在边（左/右）和同侧序号计算 */
function portPoint(node, portId) {
  const port = (node.ports || []).find((p) => p.id === portId)
  if (!port) {
    return { x: node.posX + NODE_W, y: node.posY + NODE_H / 2 }
  }
  const side = sideOfPort(port)
  const sameSide = (node.ports || []).filter((p) => sideOfPort(p) === side)
  const index = Math.max(0, sameSide.findIndex((p) => p.id === portId))
  return {
    x: node.posX + (side === 'left' ? 0 : NODE_W),
    y: node.posY + 22 + index * 15
  }
}

/** 连线拖拽中的临时曲线 */
const tempEdgePath = computed(() => {
  if (!pending.value || !tempPoint.value) return ''
  const node = nodes.value.find((n) => n.id === pending.value.nodeId)
  if (!node) return ''
  const a = portPoint(node, pending.value.portId)
  const b = tempPoint.value
  const dx = Math.max(40, Math.abs(b.x - a.x) * 0.5)
  const c1 = b.x >= a.x ? a.x + dx : a.x - dx
  const c2 = b.x >= a.x ? b.x - dx : b.x + dx
  return `M ${a.x} ${a.y} C ${c1} ${a.y}, ${c2} ${b.y}, ${b.x} ${b.y}`
})

/** 节点底色：按类型渐变，选中时描边发光 */
function nodeFill(n) {
  return `url(#grad-${n.nodeKind})`
}

function hexA(hex, alpha) {
  const c = hex.replace('#', '')
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function nodeName(id) {
  const n = nodes.value.find((x) => x.id === id)
  return n ? n.name : '#' + id
}

function cpuLabel(n) {
  const v = n.cpuCores || n.cpuRequest || 0
  return (Number.isInteger(v) ? v : v.toFixed(1)) + 'C'
}

function memLabel(n) {
  const mb = n.memTotalMb || n.memRequestMb || 0
  if (!mb) return '—'
  return mb >= 1024 ? Math.round(mb / 1024) + 'G' : mb + 'M'
}

function ringColor(node) {
  if (affectedByNode.value.has(node.id)) {
    const a = affectedByNode.value.get(node.id)
    return a.impactLevel === 'DIRECT' ? '#f97316' : '#eab308'
  }
  const issues = issuesByNode.value.get(node.id)
  if (issues && issues.length) {
    const worst = issues.some((i) => i.severity === 'ERROR') ? '#ef4444'
      : issues.some((i) => i.severity === 'WARN') ? '#f59e0b' : '#38bdf8'
    return worst
  }
  return null
}

function notify(message) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2600)
}

// ---------- 加载 ----------

async function loadProjects() {
  const res = await api.listProjects()
  projects.value = res.data
  if (!projects.value.length) {
    const created = await api.createProject({ name: '未命名推演' })
    projects.value = [created.data]
  }
  projectId.value = projects.value[0].id
  await loadData()
}

async function loadData() {
  if (!projectId.value) return
  const [nodeRes, edgeRes, faultRes, modelRes, groupRes] = await Promise.all([
    api.listNodes(projectId.value),
    api.listEdges(projectId.value),
    api.listFaults(projectId.value),
    api.listModels(),
    api.listGroups(projectId.value)
  ])
  nodes.value = nodeRes.data
  edges.value = edgeRes.data
  faults.value = faultRes.data
  models.value = modelRes.data
  groups.value = groupRes.data
  selection.value = null
  validation.value = null
  analysis.value = null
  recovery.value = null
  insights.value = null
  aiResult.value = null
  historyOpen.value = false
  historyItems.value = []
  historyTotal.value = 0
  historyPage.value = 1
  historyDetail.value = null
  historyTab.value = 'analysis'
  recoveryPlans.value = []
  recoveryDetail.value = null
  // 预取增强分析（E2/E3/E4），失败不影响主流程
  api.getInsights(projectId.value)
    .then((r) => { insights.value = r.data })
    .catch(() => {})
}

async function onSwitchProject(event) {
  projectId.value = Number(event.target.value)
  await loadData()
}

function onCreateProject() {
  openDialog('新建推演工程', '工程名', '新推演工程', 'project')
}

function onDeleteProject() {
  if (!projectId.value) return
  openDialog('删除工程', '输入工程名确认删除（不可恢复）', currentProjectName.value, 'delete-project')
}

async function deleteProjectConfirmed(value) {
  if ((value || '').trim() !== currentProjectName.value) {
    notify('工程名不一致，已取消删除')
    return
  }
  try {
    const deletingId = projectId.value
    await api.deleteProject(deletingId)
    projects.value = (await api.listProjects()).data
    if (!projects.value.length) {
      const created = await api.createProject({ name: '未命名推演' })
      projects.value = [created.data]
    }
    projectId.value = projects.value[0].id
    await loadData()
    notify('工程已删除')
  } catch (e) {
    notify('删除失败：' + e.message)
  }
}

/** 自动布局：按依赖深度分列，依赖在左、调用方在右 */
async function autoLayout() {
  if (!projectId.value || !nodes.value.length) return
  const deps = edges.value.filter((e) => e.edgeKind === 'depends_on')
  const byId = new Map(nodes.value.map((n) => [n.id, n]))
  const memo = new Map()
  const visiting = new Set()

  const depth = (id) => {
    if (memo.has(id)) return memo.get(id)
    if (visiting.has(id)) return 0
    visiting.add(id)
    let max = 0
    for (const e of deps) {
      if (e.sourceNodeId === id && byId.has(e.targetNodeId)) {
        max = Math.max(max, 1 + depth(e.targetNodeId))
      }
    }
    visiting.delete(id)
    memo.set(id, max)
    return max
  }

  const layers = new Map()
  for (const n of nodes.value) {
    const d = depth(n.id)
    if (!layers.has(d)) layers.set(d, [])
    layers.get(d).push(n)
  }

  const positions = []
  for (const [layer, list] of [...layers.entries()].sort((a, b) => a[0] - b[0])) {
    list.sort((a, b) => a.name.localeCompare(b.name))
    list.forEach((n, i) => {
      positions.push({ id: n.id, x: 120 + layer * 280, y: 100 + i * 130 })
    })
  }

  try {
    await api.savePositions(projectId.value, positions)
    nodes.value = (await api.listNodes(projectId.value)).data
    fitView()
    notify('已自动布局')
  } catch (e) {
    notify(e.message)
  }
}

// ---------- 拓扑导入 / 导出（真实系统迁移与备份的入口） ----------

function portNumberOf(portId) {
  if (!portId) return null
  for (const n of nodes.value) {
    const p = (n.ports || []).find((x) => x.id === portId)
    if (p) return p.port
  }
  return null
}

function exportTopology() {
  const nameOf = (id) => {
    const n = nodes.value.find((x) => x.id === id)
    return n ? n.name : null
  }
  const payload = {
    format: 'cloudmirror-topology',
    version: 1,
    name: currentProjectName.value,
    exportedAt: new Date().toISOString(),
    nodes: nodes.value.map((n) => ({
      name: n.name,
      nodeKind: n.nodeKind,
      modelId: n.modelId,
      cpuCores: n.cpuCores,
      cpuRequest: n.cpuRequest,
      memTotalMb: n.memTotalMb,
      memRequestMb: n.memRequestMb,
      diskGb: n.diskGb,
      replicas: n.replicas,
      ip: n.ip,
      runtime: n.runtime,
      status: n.status,
      posX: n.posX,
      posY: n.posY,
      description: n.description,
      ports: (n.ports || []).map((p) => ({
        port: p.port, protocol: p.protocol, scope: p.scope,
        hostPort: p.hostPort, purpose: p.purpose
      }))
    })),
    edges: edges.value.map((e) => ({
      edgeKind: e.edgeKind,
      sourceName: nameOf(e.sourceNodeId),
      targetName: nameOf(e.targetNodeId),
      sourcePort: portNumberOf(e.sourcePortId),
      targetPort: portNumberOf(e.targetPortId)
    })),
    groups: groups.value.map((g) => ({
      name: g.name,
      redundancyType: g.redundancyType,
      description: g.description,
      members: (g.members || []).map((m) => ({
        nodeName: m.nodeName, role: m.role, priority: m.priority
      }))
    }))
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = (currentProjectName.value || 'topology') + '.json'
  a.click()
  URL.revokeObjectURL(a.href)
  notify('已导出拓扑 JSON')
}

function triggerImport() {
  if (fileRef.value) fileRef.value.click()
}

async function onImportFile(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  let createdProjectId = null
  try {
    const data = JSON.parse(await file.text())
    if (!Array.isArray(data.nodes) || !data.nodes.length) {
      throw new Error('JSON 里没有 nodes')
    }
    const created = await api.createProject({ name: (data.name || '导入工程') + '-导入' })
    const pid = created.data.id
    createdProjectId = pid
    const idByName = new Map()
    const portIdByKey = new Map()

    for (const n of data.nodes) {
      const res = await api.createNode(pid, {
        name: n.name,
        nodeKind: n.nodeKind,
        cpuCores: n.cpuCores,
        cpuRequest: n.cpuRequest,
        memTotalMb: n.memTotalMb,
        memRequestMb: n.memRequestMb,
        diskGb: n.diskGb,
        replicas: n.replicas,
        ip: n.ip,
        runtime: n.runtime,
        status: n.status,
        posX: n.posX,
        posY: n.posY,
        description: n.description,
        ports: n.ports || []
      })
      idByName.set(n.name, res.data.id)
      for (const p of res.data.ports || []) {
        portIdByKey.set(n.name + ':' + p.port, p.id)
      }
    }

    let edgeCount = 0
    for (const e of data.edges || []) {
      const sid = idByName.get(e.sourceName)
      const tid = idByName.get(e.targetName)
      if (!sid || !tid) continue
      try {
        await api.createEdge(pid, {
          edgeKind: e.edgeKind,
          sourceNodeId: sid,
          targetNodeId: tid,
          sourcePortId: e.sourcePort ? (portIdByKey.get(e.sourceName + ':' + e.sourcePort) || 0) : 0,
          targetPortId: e.targetPort ? (portIdByKey.get(e.targetName + ':' + e.targetPort) || 0) : 0
        })
        edgeCount++
      } catch {
        // 单条线失败不影响整体导入
      }
    }

    let groupCount = 0
    for (const g of data.groups || []) {
      const members = (g.members || [])
        .map((m) => ({ nodeId: idByName.get(m.nodeName), role: m.role, priority: m.priority }))
        .filter((m) => m.nodeId)
      if (!members.length) continue
      try {
        await api.createGroup(pid, {
          name: g.name,
          redundancyType: g.redundancyType,
          description: g.description,
          members
        })
        groupCount++
      } catch {
        // 单个冗余组失败不影响整体导入
      }
    }

    projects.value = (await api.listProjects()).data
    projectId.value = pid
    await loadData()
    notify(`导入完成：${data.nodes.length} 节点 / ${edgeCount} 连线 / ${groupCount} 冗余组`)
  } catch (err) {
    // 导入不是数据库事务：中途失败就把刚建的工程删掉，避免留下半个拓扑
    if (createdProjectId) {
      try {
        await api.deleteProject(createdProjectId)
      } catch {}
    }
    notify('导入失败：' + err.message)
  }
}
async function createProjectWithName(name) {
  if (!name) return
  try {
    const res = await api.createProject({ name })
    projects.value = (await api.listProjects()).data
    projectId.value = res.data.id
    await loadData()
    notify('工程已创建')
  } catch (e) {
    notify(e.message)
  }
}

function openDialog(title, label, value, action) {
  dialog.value = { title, label, value, action, purpose: '' }
}

async function submitDialog() {
  const d = dialog.value
  if (!d) return
  dialog.value = null
  if (d.action === 'project') {
    await createProjectWithName((d.value || '').trim())
  } else if (d.action === 'delete-project') {
    await deleteProjectConfirmed(d.value)
  } else if (d.action === 'port') {
    await addPortWithValue(Number(d.value))
  } else if (d.action === 'node-connect') {
    await createNodeAndConnect((d.value || '').trim())
  }
}

function cancelDialog() {
  dialog.value = null
  connectDraft.value = null
  pending.value = null
  tempPoint.value = null
}

/** 端口多于 4 个时，用选择器挑端口 */
function openPortPicker(node, mode) {
  portPicker.value = { nodeId: node.id, mode, from: pending.value }
}

function pickPort(p) {
  const picker = portPicker.value
  portPicker.value = null
  if (!picker) return
  if (picker.mode === 'source') {
    pending.value = { nodeId: picker.nodeId, portId: p.id }
    notify('已选源端口 ' + p.port + '，拖到或点目标节点')
  } else if (picker.from) {
    createEdgeBetween(picker.from, { nodeId: picker.nodeId, portId: p.id })
    pending.value = null
  }
}

/** 从端口拖到空白处：新建节点并自动连线 */
async function createNodeAndConnect(name) {
  const draft = connectDraft.value
  connectDraft.value = null
  if (!draft || !name) {
    pending.value = null
    return
  }
  try {
    const res = await api.createNode(projectId.value, {
      name,
      nodeKind: 'container',
      posX: draft.x,
      posY: draft.y,
      ports: [{ port: 8080, protocol: 'tcp', scope: 'internal' }]
    })
    const created = res.data
    const newPortId = created.ports && created.ports.length ? created.ports[0].id : 0
    await api.createEdge(projectId.value, {
      edgeKind: connectKind.value,
      sourceNodeId: draft.from.nodeId,
      targetNodeId: created.id,
      sourcePortId: draft.from.portId || 0,
      targetPortId: newPortId
    })
    nodes.value = (await api.listNodes(projectId.value)).data
    edges.value = (await api.listEdges(projectId.value)).data
    pending.value = null
    notify('已新建节点并连线')
  } catch (e) {
    pending.value = null
    notify(e.message)
  }
}

// ---------- 画布交互 ----------

function onWheel(event) {
  const rect = svgRef.value.getBoundingClientRect()
  const mx = event.clientX - rect.left
  const my = event.clientY - rect.top
  const wx = (mx - view.tx) / view.scale
  const wy = (my - view.ty) / view.scale
  const next = Math.min(3, Math.max(0.15, view.scale * (event.deltaY < 0 ? 1.1 : 0.9)))
  view.scale = next
  view.tx = mx - wx * next
  view.ty = my - wy * next
  scheduleViewSave()
}

function zoomBy(factor) {
  const rect = svgRef.value.getBoundingClientRect()
  const mx = rect.width / 2
  const my = rect.height / 2
  const wx = (mx - view.tx) / view.scale
  const wy = (my - view.ty) / view.scale
  view.scale = Math.min(3, Math.max(0.15, view.scale * factor))
  view.tx = mx - wx * view.scale
  view.ty = my - wy * view.scale
  scheduleViewSave()
}

function fitView() {
  if (!nodes.value.length) return
  const xs = nodes.value.map((n) => n.posX)
  const ys = nodes.value.map((n) => n.posY)
  const minX = Math.min(...xs) - 80
  const minY = Math.min(...ys) - 80
  const maxX = Math.max(...xs) + NODE_W + 80
  const maxY = Math.max(...ys) + NODE_H + 80
  const rect = svgRef.value.getBoundingClientRect()
  const scale = Math.min(rect.width / (maxX - minX), rect.height / (maxY - minY), 1.5)
  view.scale = Math.max(0.2, scale)
  view.tx = -minX * view.scale
  view.ty = -minY * view.scale
  scheduleViewSave()
}

function onCanvasDown(event) {
  if (event.button !== 0) return
  selection.value = null
  contextMenu.value = null
  pan = { x: event.clientX, y: event.clientY, tx: view.tx, ty: view.ty }
  window.addEventListener('pointermove', onPanMove)
  window.addEventListener('pointerup', onPanUp)
}

function onPanMove(event) {
  if (!pan) return
  view.tx = pan.tx + (event.clientX - pan.x)
  view.ty = pan.ty + (event.clientY - pan.y)
}

function onPanUp() {
  pan = null
  window.removeEventListener('pointermove', onPanMove)
  window.removeEventListener('pointerup', onPanUp)
  scheduleViewSave()
}

function onNodeDown(event, node) {
  // 连线：无序端口模式时，点第二个节点直接成边
  const meta = edgeMeta(connectKind.value)
  if (!meta.needsPort && pending.value && pending.value.nodeId !== node.id) {
    createEdgeBetween(pending.value, { nodeId: node.id, portId: null })
    pending.value = null
    return
  }
  selection.value = { type: 'node', id: node.id }
  drag = { node, x: event.clientX, y: event.clientY, nx: node.posX, ny: node.posY }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragUp)
}

function onDragMove(event) {
  if (!drag) return
  drag.node.posX = Math.round((drag.nx + (event.clientX - drag.x) / view.scale) / 8) * 8
  drag.node.posY = Math.round((drag.ny + (event.clientY - drag.y) / view.scale) / 8) * 8
}

async function onDragUp() {
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragUp)
  if (drag) {
    const node = drag.node
    drag = null
    try {
      await api.savePositions(projectId.value, [{ id: node.id, x: node.posX, y: node.posY }])
    } catch (e) {
      notify(e.message)
    }
  }
}

function onPortDown(event, node, port) {
  event.stopPropagation()
  if (pending.value && pending.value.nodeId !== node.id) {
    createEdgeBetween(pending.value, { nodeId: node.id, portId: port.id })
    pending.value = null
    tempPoint.value = null
    return
  }
  pending.value = { nodeId: node.id, portId: port.id }
  // 支持「从端口拖到目标端口」
  window.addEventListener('pointermove', onConnectMove)
  window.addEventListener('pointerup', onConnectUp)
  notify('已选源端口 ' + port.port + '：拖到目标端口，或直接点目标端口')
}

function onConnectMove(event) {
  const rect = svgRef.value.getBoundingClientRect()
  tempPoint.value = {
    x: (event.clientX - rect.left - view.tx) / view.scale,
    y: (event.clientY - rect.top - view.ty) / view.scale
  }
}

function onConnectUp(event) {
  window.removeEventListener('pointermove', onConnectMove)
  window.removeEventListener('pointerup', onConnectUp)
  if (!pending.value) {
    tempPoint.value = null
    return
  }
  const el = document.elementFromPoint(event.clientX, event.clientY)
  const portEl = el && el.closest ? el.closest('[data-port-id]') : null
  const nodeEl = el && el.closest ? el.closest('[data-node-id]') : null

  if (portEl) {
    const toNode = Number(portEl.getAttribute('data-node-id'))
    const toPort = Number(portEl.getAttribute('data-port-id'))
    if (toNode !== pending.value.nodeId) {
      createEdgeBetween(pending.value, { nodeId: toNode, portId: toPort })
      pending.value = null
    }
  } else if (nodeEl) {
    // 拖到节点上：端口多就让你挑，端口少就直接用第一个
    const toNode = Number(nodeEl.getAttribute('data-node-id'))
    if (toNode && toNode !== pending.value.nodeId) {
      const target = nodes.value.find((n) => n.id === toNode)
      if (target && target.ports.length > 4) {
        portPicker.value = { nodeId: toNode, mode: 'target', from: pending.value }
        pending.value = null
      } else {
        const firstPort = target && target.ports && target.ports.length ? target.ports[0].id : null
        createEdgeBetween(pending.value, { nodeId: toNode, portId: firstPort })
        pending.value = null
      }
    }
  } else {
    // 拖到空白处：问一下要不要就地新建节点并连线
    const rect = svgRef.value.getBoundingClientRect()
    connectDraft.value = {
      x: Math.round(((event.clientX - rect.left - view.tx) / view.scale) / 8) * 8,
      y: Math.round(((event.clientY - rect.top - view.ty) / view.scale) / 8) * 8,
      from: pending.value
    }
    openDialog('在这里新建节点并连线', '节点名', '新节点', 'node-connect')
    tempPoint.value = null
    return
  }
  tempPoint.value = null
}

/** 点连线选中它（选中后右侧面板可删除，或按 Delete） */
function onEdgeDown(geom) {
  selection.value = { type: 'edge', id: geom.id }
}

/** 右键菜单 */
function openMenu(event, type, id) {
  selection.value = { type, id }
  const rect = svgRef.value.getBoundingClientRect()
  contextMenu.value = {
    x: Math.min(event.clientX - rect.left, rect.width - 150),
    y: Math.min(event.clientY - rect.top, rect.height - 110),
    type,
    id
  }
}

function menuDelete() {
  const type = contextMenu.value ? contextMenu.value.type : null
  contextMenu.value = null
  if (type === 'node') {
    onDeleteNode()
  } else if (type === 'edge') {
    onDeleteEdge()
  }
}

function menuInjectFault() {
  contextMenu.value = null
  onInjectFault()
}

function menuAddPort() {
  contextMenu.value = null
  onAddPort()
}

function onKeyDown(event) {
  const tag = (event.target && event.target.tagName) || ''
  if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return
  if (event.key === 'Delete' || event.key === 'Backspace') {
    if (selectedNode.value) {
      event.preventDefault()
      onDeleteNode()
    } else if (selectedEdge.value) {
      event.preventDefault()
      onDeleteEdge()
    }
  }
  if (event.key === 'Escape') {
    selection.value = null
    pending.value = null
    tempPoint.value = null
  }
  // [ 收起/展开节点库，] 收起/展开属性面板
  if (event.key === '[') {
    leftOpen.value = !leftOpen.value
  }
  if (event.key === ']') {
    rightOpen.value = !rightOpen.value
  }
}

async function createEdgeBetween(from, to) {
  if (from.nodeId === to.nodeId) {
    notify('不能把节点连到自己')
    return
  }
  const meta = edgeMeta(connectKind.value)
  if (meta.needsPort && (!from.portId || !to.portId)) {
    notify('「' + meta.label + '」是接口级连线，必须连到具体端口（请拖到端口圆点上）')
    return
  }
  try {
    await api.createEdge(projectId.value, {
      edgeKind: connectKind.value,
      sourceNodeId: from.nodeId,
      targetNodeId: to.nodeId,
      sourcePortId: from.portId || 0,
      targetPortId: to.portId || 0
    })
    edges.value = (await api.listEdges(projectId.value)).data
    notify('连线已创建')
  } catch (e) {
    notify(e.message)
  }
}

function focusNode(ids) {
  const id = (ids || [])[0]
  const node = nodes.value.find((n) => n.id === id)
  if (!node) return
  selection.value = { type: 'node', id }
  const rect = svgRef.value.getBoundingClientRect()
  view.tx = rect.width / 2 - (node.posX + NODE_W / 2) * view.scale
  view.ty = rect.height / 2 - (node.posY + NODE_H / 2) * view.scale
  scheduleViewSave()
}

function scheduleViewSave() {
  clearTimeout(viewTimer)
  viewTimer = setTimeout(() => {
    if (!projectId.value) return
    api.saveView(projectId.value, { scale: view.scale, offsetX: view.tx, offsetY: view.ty })
      .catch(() => {})
  }, 600)
}

// ---------- 节点操作 ----------

async function addNode(kind) {
  if (!projectId.value) return
  const rect = svgRef.value.getBoundingClientRect()
  const count = nodes.value.filter((n) => n.nodeKind === kind).length + 1
  const x = (rect.width / 2 - view.tx) / view.scale + (count % 3) * 30
  const y = (rect.height / 2 - view.ty) / view.scale + (count % 3) * 30
  // 新建节点给一套合理默认资源，避免一加进来就"0 核 0 内存"导致校验全红
  const defaults = {
    host: { cpuCores: 4, memTotalMb: 8192, diskGb: 100 },
    vm: { cpuCores: 2, memTotalMb: 4096, diskGb: 50 },
    k8s_cluster: { cpuCores: 8, memTotalMb: 16384, diskGb: 500 },
    pod: { cpuRequest: 0.5, memRequestMb: 1024 },
    container: { cpuRequest: 0.5, memRequestMb: 1024 },
    service: { cpuRequest: 1, memRequestMb: 2048 },
    middleware: { cpuRequest: 1, memRequestMb: 2048, diskGb: 100 },
    object_storage: {},
    gateway: { cpuRequest: 0.5, memRequestMb: 512 },
    ingress: { cpuRequest: 0.5, memRequestMb: 512 },
    external: {}
  }
  try {
    await api.createNode(projectId.value, {
      name: kindMeta(kind).label.split(' ')[0] + '-' + count,
      nodeKind: kind,
      posX: Math.round(x / 8) * 8,
      posY: Math.round(y / 8) * 8,
      ...defaults[kind],
      ports: kind === 'host' || kind === 'vm' || kind === 'external' || kind === 'object_storage'
        ? []
        : [{ port: 8080 + count, protocol: 'tcp', scope: 'internal' }]
    })
    nodes.value = (await api.listNodes(projectId.value)).data
  } catch (e) {
    notify(e.message)
  }
}

/** 选型号 → 立即把资源填进去（之后仍可手改） */
function onModelChange() {
  const n = selectedNode.value
  if (!n || !n.modelId) return
  const model = models.value.find((m) => m.id === n.modelId)
  if (!model) return
  const hostLike = n.nodeKind === 'host' || n.nodeKind === 'vm' || n.nodeKind === 'k8s_cluster'
  if (hostLike) {
    n.cpuCores = model.cpuCores
    n.memTotalMb = model.memMb
    n.diskGb = model.diskGb
  } else {
    n.cpuRequest = model.cpuCores
    n.memRequestMb = model.memMb
    if (model.diskGb) n.diskGb = model.diskGb
  }
  notify('已按型号填入：' + model.modelName + '（还可以再手改）')
}

/** 改类型后，如果当前型号不属于新类型就清掉，避免资源对不上 */
function onKindChange() {
  const n = selectedNode.value
  if (!n || !n.modelId) return
  const model = models.value.find((m) => m.id === n.modelId)
  if (model && model.kind !== n.nodeKind) {
    n.modelId = null
    notify('类型已变更，型号已清空，请重新选择')
  }
}

async function onSaveNode() {
  const n = selectedNode.value
  if (!n) return
  try {
    await api.updateNode(projectId.value, n.id, {
      name: n.name,
      nodeKind: n.nodeKind,
      modelId: n.modelId,
      cpuCores: n.cpuCores,
      cpuRequest: n.cpuRequest,
      memTotalMb: n.memTotalMb,
      memRequestMb: n.memRequestMb,
      diskGb: n.diskGb,
      replicas: n.replicas,
      ip: n.ip,
      runtime: n.runtime,
      status: n.status,
      posX: n.posX,
      posY: n.posY,
      description: n.description
    })
    nodes.value = (await api.listNodes(projectId.value)).data
    notify('已保存')
  } catch (e) {
    notify(e.message)
  }
}

function onAddPort() {
  if (!selectedNode.value) {
    notify('先选中一个节点')
    return
  }
  openDialog('添加端口', '端口号（1–65535）', '9090', 'port')
}

async function addPortWithValue(port, purpose) {
  const n = selectedNode.value
  if (!n) return
  if (!port || port < 1 || port > 65535) {
    notify('端口必须在 1–65535')
    return
  }
  if (n.ports.some((p) => p.port === port)) {
    notify('节点「' + n.name + '」已经有端口 ' + port + ' 了')
    return
  }
  try {
    await api.updateNode(projectId.value, n.id, {
      name: n.name,
      nodeKind: n.nodeKind,
      modelId: n.modelId,
      cpuCores: n.cpuCores,
      cpuRequest: n.cpuRequest,
      memTotalMb: n.memTotalMb,
      memRequestMb: n.memRequestMb,
      diskGb: n.diskGb,
      replicas: n.replicas,
      ip: n.ip,
      runtime: n.runtime,
      status: n.status,
      posX: n.posX,
      posY: n.posY,
      description: n.description,
      ports: [...n.ports.map((p) => ({
        port: p.port, protocol: p.protocol, scope: p.scope, hostPort: p.hostPort, purpose: p.purpose
      })), { port, protocol: 'tcp', scope: 'internal', purpose: purpose || null }]
    })
    nodes.value = (await api.listNodes(projectId.value)).data
    selection.value = { type: 'node', id: n.id }
    notify('端口已添加')
  } catch (e) {
    notify(e.message)
  }
}

async function onDeleteNode() {
  const n = selectedNode.value
  if (!n) {
    notify('先选中一个节点')
    return
  }
  // 不用 window.confirm：浏览器拦截弹窗时会直接返回 false，表现为"删不掉"
  try {
    await api.deleteNode(projectId.value, n.id)
    selection.value = null
    await loadData()
    notify('节点已删除')
  } catch (e) {
    notify('删除失败：' + e.message)
  }
}

async function onDeleteEdge() {
  const edge = selectedEdge.value
  if (!edge) {
    notify('先选中一条连线')
    return
  }
  if (deletingEdge) {
    return
  }
  deletingEdge = true
  try {
    await api.deleteEdge(projectId.value, edge.id)
    edges.value = (await api.listEdges(projectId.value)).data
    selection.value = null
    notify('连线已删除')
  } catch (err) {
    const msg = err.message || ''
    // 已经不存在（重复点击/别处删过）也当成功，避免"删了还报错"
    if (msg.includes('不存在') || msg.includes('不属于')) {
      edges.value = (await api.listEdges(projectId.value)).data
      selection.value = null
      notify('连线已删除')
    } else {
      notify('删除失败：' + msg)
    }
  } finally {
    deletingEdge = false
  }
}

// ---------- 算法 ----------

async function onValidate() {
  if (!projectId.value) return
  busy.value = true
  try {
    const res = await api.runValidate(projectId.value)
    validation.value = res.data
    analysis.value = null
    dockTab.value = 'problems'
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}

async function onInjectFault() {
  const n = selectedNode.value
  if (!n) return
  try {
    await api.injectFault(projectId.value, {
      nodeId: n.id,
      faultType: faultType.value,
      severity: 'high',
      description: '界面注入'
    })
    faults.value = (await api.listFaults(projectId.value)).data
    dockTab.value = 'faults'
    notify('故障已注入，可点「推演分析」')
  } catch (e) {
    notify(e.message)
  }
}

async function onRecoverFault(fault) {
  try {
    await api.recoverFault(projectId.value, fault.id)
    faults.value = (await api.listFaults(projectId.value)).data
    notify('故障已恢复')
  } catch (e) {
    notify(e.message)
  }
}

async function onAnalyze() {
  if (!projectId.value) return
  busy.value = true
  try {
    const res = await api.runAnalysis(projectId.value)
    analysis.value = res.data.result
    dockTab.value = 'analysis'
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}

async function onRecovery() {
  if (!projectId.value) return
  busy.value = true
  try {
    const res = await api.runRecovery(projectId.value)
    recovery.value = res.data.result
    dockTab.value = 'recovery'
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}

async function onInsights() {
  if (!projectId.value) return
  busy.value = true
  try {
    const res = await api.getInsights(projectId.value)
    insights.value = res.data
    dockTab.value = 'insights'
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}

// ---------- AI 诊断 ----------

async function onAiDiagnose() {
  if (!projectId.value) return
  busy.value = true
  try {
    const res = await api.diagnose(projectId.value)
    aiResult.value = res.data
    dockTab.value = 'ai'
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}

function sourceLabel(source) {
  const map = {
    llm: '大模型',
    fallback: 'AI 服务规则降级',
    'local-fallback': '本地规则（AI 未连接）'
  }
  return map[source] || source
}

// ---------- 历史推演 ----------

async function onOpenHistory() {
  historyOpen.value = true
  historyPage.value = 1
  historyTab.value = 'analysis'
  await loadHistory()
}

async function loadHistory() {
  if (!projectId.value) return
  try {
    const res = await api.listAnalyses(projectId.value, historyPage.value, historySize)
    historyItems.value = res.data.items || []
    historyTotal.value = res.data.total || 0
    historyDetail.value = null
    if (historyItems.value.length) {
      await loadHistoryDetail(historyItems.value[0].id)
    }
  } catch (e) {
    notify(e.message)
  }
}

async function loadHistoryDetail(id) {
  try {
    const res = await api.getAnalysis(projectId.value, id)
    historyDetail.value = res.data
  } catch (e) {
    notify(e.message)
  }
}

async function switchHistoryTab(tab) {
  historyTab.value = tab
  if (tab === 'recovery') {
    await loadRecoveryPlans()
  }
}

async function loadRecoveryPlans() {
  if (!projectId.value) return
  try {
    const res = await api.listRecoveryPlans(projectId.value, 20)
    recoveryPlans.value = res.data || []
    recoveryDetail.value = recoveryPlans.value.length ? recoveryPlans.value[0] : null
  } catch (e) {
    notify(e.message)
  }
}

// ---------- 生命周期 ----------

onMounted(async () => {
  window.addEventListener('keydown', onKeyDown)
  try {
    await loadProjects()
    const params = new URLSearchParams(window.location.search)
    const wanted = params.get('project')
    if (wanted && projects.value.some((p) => String(p.id) === wanted)) {
      projectId.value = Number(wanted)
      await loadData()
    }
    if (params.get('panel') === 'history') {
      await onOpenHistory()
    }
    if (params.get('panel') === 'ai') {
      await onAiDiagnose()
    }
    if (params.get('panel') === 'analysis') {
      await onAnalyze()
    }
    // ?select=节点ID 直接选中该节点（演示/截图用）
    const selectId = params.get('select')
    if (selectId) {
      const node = nodes.value.find((n) => n.id === Number(selectId))
      if (node) selection.value = { type: 'node', id: node.id }
    }
  } catch (e) {
    notify('连接后端失败：' + e.message)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragUp)
  window.removeEventListener('pointermove', onPanMove)
  window.removeEventListener('pointerup', onPanUp)
  window.removeEventListener('pointermove', onConnectMove)
  window.removeEventListener('pointerup', onConnectUp)
})
</script>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  /* 拖拽时不要选中文字 */
  user-select: none;
  -webkit-user-select: none;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #0f172a;
  border-bottom: 1px solid #1e293b;
}

.brand {
  font-weight: 700;
  font-size: 15px;
  color: #38bdf8;
  letter-spacing: 1px;
}

.brand .sub {
  font-weight: 400;
  font-size: 11px;
  color: #64748b;
  margin-left: 8px;
}

.proj {
  width: 200px;
}

.spacer {
  flex: 1;
}

.zoom {
  width: 46px;
  text-align: center;
  color: #94a3b8;
}

.main {
  flex: 1;
  display: flex;
  min-height: 0;
}

.palette {
  width: 190px;
  padding: 10px;
  background: #0f172a;
  border-right: 1px solid #1e293b;
  overflow: auto;
  transition: width 0.18s ease, padding 0.18s ease;
}

.palette.collapsed {
  width: 0;
  padding: 0;
  border-right: none;
  overflow: hidden;
}

.collapse-btn {
  float: right;
  border: none;
  background: transparent;
  color: #64748b;
  padding: 0 4px;
  font-size: 13px;
  line-height: 1;
}

.collapse-btn:hover {
  color: #e2e8f0;
}

.panel-title {
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
}

.palette-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 7px 8px;
  border-radius: 7px;
  cursor: pointer;
  border: 1px solid transparent;
}

.palette-item:hover {
  background: #1e293b;
  border-color: #334155;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.palette-label {
  font-size: 12px;
}

.palette-hint {
  font-size: 10px;
  color: #64748b;
}

.hint {
  color: #64748b;
  font-size: 11px;
  margin-top: 6px;
  line-height: 1.6;
}

.canvas-wrap {
  flex: 1;
  position: relative;
  min-width: 0;
}

.canvas {
  width: 100%;
  height: 100%;
  display: block;
  background: #0b1220;
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
}

/* 连线的透明命中区：加宽到 16px，点得中才能选中删除 */
.edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 16;
  cursor: pointer;
  pointer-events: stroke;
}

.port {
  cursor: crosshair;
}

.port-label {
  fill: #64748b;
  font-size: 9px;
  text-anchor: end;
  pointer-events: none;
}

.port-label.left {
  text-anchor: start;
}

.port-more {
  fill: #94a3b8;
  font-weight: 600;
  font-size: 10px;
}

.port-more-btn {
  cursor: pointer;
}

.port-dialog {
  width: 360px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.port-list {
  overflow: auto;
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.port-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  text-align: left;
  background: #16243b;
  border: 1px solid #27405f;
  border-radius: 7px;
  padding: 7px 9px;
}

.port-item b {
  color: #38bdf8;
  font-size: 13px;
  min-width: 46px;
}

.port-item span {
  color: #94a3b8;
  font-size: 11px;
}

.port-item em {
  color: #64748b;
  font-style: normal;
  font-size: 11px;
  margin-left: auto;
}

.port-item:hover {
  border-color: #38bdf8;
}

.node-icon {
  font-size: 14px;
}

.node-del {
  cursor: pointer;
}

.ctx-menu {
  position: absolute;
  min-width: 130px;
  background: #111c2e;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 5px;
  z-index: 20;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ctx-menu .ctx-title {
  font-size: 10px;
  color: #64748b;
  padding: 2px 6px;
}

.ctx-menu button {
  text-align: left;
  background: transparent;
  border: none;
  padding: 5px 8px;
  border-radius: 5px;
  font-size: 12px;
}

.ctx-menu button:hover {
  background: #1e293b;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
}

.dialog {
  width: 320px;
  background: #111c2e;
  border: 1px solid #334155;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.5);
}

.dialog-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.node-selected {
  filter: drop-shadow(0 0 7px rgba(56, 189, 248, 0.55));
}

.legend {
  position: absolute;
  left: 12px;
  bottom: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 60%;
  color: #64748b;
  font-size: 10px;
  pointer-events: none;
}

.legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.canvas-tip {
  position: absolute;
  right: 12px;
  bottom: 12px;
  color: #475569;
  font-size: 10px;
  pointer-events: none;
}

.canvas:active {
  cursor: grabbing;
}

.node-name {
  fill: #e2e8f0;
  font-size: 12px;
  font-weight: 600;
}

.node-meta {
  fill: #64748b;
  font-size: 10px;
}

.edge-label {
  font-size: 9px;
  opacity: 0.8;
}

.pending {
  fill: #f59e0b;
  font-size: 10px;
}

.empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #475569;
  pointer-events: none;
}

.toast {
  position: absolute;
  left: 50%;
  bottom: 18px;
  transform: translateX(-50%);
  background: #1e293b;
  border: 1px solid #334155;
  padding: 7px 14px;
  border-radius: 8px;
  color: #e2e8f0;
  font-size: 12px;
  max-width: 70%;
}

.inspector {
  width: 260px;
  padding: 10px;
  background: #0f172a;
  border-left: 1px solid #1e293b;
  overflow: auto;
  transition: width 0.18s ease, padding 0.18s ease;
}

.inspector.collapsed {
  width: 0;
  padding: 0;
  border-left: none;
  overflow: hidden;
}

.expand-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 26px;
  height: 46px;
  background: #0f172a;
  border: 1px solid #1e293b;
  color: #94a3b8;
  border-radius: 6px;
  z-index: 5;
  padding: 0;
  font-size: 13px;
}

.expand-btn:hover {
  color: #e2e8f0;
  border-color: #38bdf8;
}

.expand-btn.left {
  left: 8px;
}

.expand-btn.right {
  right: 8px;
}

.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 8px;
}

.ports {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  margin-top: 4px;
}

.port-chip {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 5px;
  padding: 2px 6px;
  font-size: 11px;
}

.port-chip em {
  color: #64748b;
  font-style: normal;
}

.row {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}

.tiny {
  padding: 2px 7px;
  font-size: 11px;
}

.dock {
  height: 190px;
  background: #0f172a;
  border-top: 1px solid #1e293b;
  display: flex;
  flex-direction: column;
}

.tabs {
  display: flex;
  gap: 6px;
  padding: 6px 10px;
  border-bottom: 1px solid #1e293b;
  align-items: center;
}

.tabs button.active {
  background: #0284c7;
  border-color: #0ea5e9;
}

.status {
  color: #94a3b8;
  font-size: 11px;
}

.dock-body {
  flex: 1;
  overflow: auto;
  padding: 8px 12px;
}

.summary {
  color: #cbd5e1;
  margin-bottom: 8px;
  font-size: 12px;
}

.issue,
.fault,
.strategy {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 5px 6px;
  border-radius: 6px;
  cursor: pointer;
}

.issue:hover,
.fault:hover {
  background: #1e293b;
}

.sev {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 4px;
  color: #fff;
  flex: 0 0 auto;
}

.msg {
  color: #cbd5e1;
}

.sug {
  color: #64748b;
  font-size: 11px;
  margin-left: auto;
}

.strategy {
  flex-direction: column;
  align-items: stretch;
  border: 1px solid #1e293b;
  margin-bottom: 6px;
  padding: 8px;
  cursor: default;
}

.strategy-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pill {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.pill.ok {
  background: #14532d;
  color: #86efac;
}

.pill.no {
  background: #450a0a;
  color: #fca5a5;
}

.score {
  color: #38bdf8;
  font-weight: 700;
}

.meta {
  color: #64748b;
  font-size: 11px;
  margin-left: auto;
}

.strategy-detail {
  color: #94a3b8;
  font-size: 11px;
  margin-top: 4px;
}

.insight-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 5px 0;
  border-bottom: 1px dashed #1e293b;
}

.insight-row b {
  flex: 0 0 64px;
  color: #94a3b8;
  font-weight: 500;
  font-size: 11px;
}

.chip {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 5px;
  padding: 2px 7px;
  font-size: 11px;
  cursor: pointer;
}

.chip:hover {
  border-color: #38bdf8;
}

.meta {
  color: #64748b;
  font-size: 11px;
}

/* ---------- 历史抽屉 ---------- */

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.history-modal {
  width: 980px;
  max-width: 92vw;
  height: 620px;
  max-height: 86vh;
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-head,
.history-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #1e293b;
}

.history-foot {
  border-bottom: none;
  border-top: 1px solid #1e293b;
  justify-content: center;
}

.history-tabs {
  display: flex;
  gap: 6px;
  padding: 8px 14px 0;
}

.history-tabs button {
  border-radius: 999px;
  padding: 4px 12px;
  background: #131f33;
  border-color: #22304a;
}

.history-tabs button.active {
  background: linear-gradient(180deg, #0ea5e9, #0369a1);
  border-color: #0ea5e9;
}

.history-body {
  flex: 1;
  display: grid;
  grid-template-columns: 320px 1fr;
  min-height: 0;
}

.history-list {
  border-right: 1px solid #1e293b;
  overflow: auto;
  padding: 8px;
}

.history-item {
  padding: 8px;
  border-radius: 7px;
  cursor: pointer;
  border: 1px solid transparent;
}

.history-item:hover {
  background: #1e293b;
}

.history-item.active {
  background: #1e3a5f;
  border-color: #0ea5e9;
}

.hi-time {
  color: #38bdf8;
  font-size: 11px;
}

.hi-summary {
  font-size: 12px;
  margin: 3px 0;
}

.hi-meta {
  color: #64748b;
  font-size: 11px;
}

.history-detail {
  overflow: auto;
  padding: 12px 14px;
}

.ai-text {
  white-space: pre-wrap;
  font-family: inherit;
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1.75;
  margin: 8px 0;
}

/* ---------- 动效：传播流动 / 故障脉冲 ---------- */

.edge-active {
  stroke-linecap: round;
  animation: dashFlow 1s linear infinite;
  filter: drop-shadow(0 0 5px rgba(56, 189, 248, 0.65));
}

.edge-problem {
  stroke-dasharray: 6 5;
  animation: dashFlow 1.4s linear infinite;
  filter: drop-shadow(0 0 4px rgba(239, 68, 68, 0.55));
}

@keyframes dashFlow {
  to {
    stroke-dashoffset: -32;
  }
}

.node-fault {
  animation: faultPulse 1.4s ease-in-out infinite;
}

@keyframes faultPulse {
  0%,
  100% {
    filter: drop-shadow(0 0 0 rgba(239, 68, 68, 0));
  }
  50% {
    filter: drop-shadow(0 0 12px rgba(239, 68, 68, 0.95));
  }
}

/* ---------- 视觉统一调整 ---------- */

.topbar {
  background: linear-gradient(180deg, #0f1a2e, #0b1424);
  border-bottom: 1px solid #1c2a41;
  padding: 10px 14px;
}

.brand {
  font-size: 16px;
  letter-spacing: 0.5px;
}

.brand::before {
  content: '◆';
  color: #38bdf8;
  margin-right: 8px;
}

.topbar button {
  border-radius: 8px;
  padding: 6px 11px;
}

.topbar button.primary {
  background: linear-gradient(180deg, #0ea5e9, #0284c7);
  border-color: #0ea5e9;
  font-weight: 600;
}

.palette,
.inspector {
  background: linear-gradient(180deg, #0d1729, #0a1322);
}

.palette-item {
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 8px;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.palette-search {
  margin-bottom: 8px;
  height: 28px;
}

.palette-group {
  font-size: 10px;
  color: #475569;
  letter-spacing: 1px;
  margin: 8px 0 2px;
}

.palette-group-wrap {
  margin-bottom: 4px;
}

.palette-item:hover {
  background: #16243b;
  border-color: #27405f;
  transform: translateX(2px);
}

.palette .dot {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  opacity: 0.85;
}

.inspector input,
.inspector select {
  height: 30px;
  border-radius: 7px;
}

.inspector label {
  margin-top: 10px;
  letter-spacing: 0.4px;
}

.canvas {
  background: radial-gradient(1200px 600px at 50% 0%, #0d1a2e 0%, #0a1220 62%, #080e1a 100%);
}

.dock {
  background: #0c1526;
}

.tabs button {
  border-radius: 999px;
  padding: 4px 12px;
  background: #131f33;
  border-color: #22304a;
}

.tabs button.active {
  background: linear-gradient(180deg, #0ea5e9, #0369a1);
  border-color: #0ea5e9;
}

.panel-title {
  letter-spacing: 1.2px;
}

.node-name {
  font-size: 12.5px;
}

.node-meta {
  font-size: 10px;
}
</style>

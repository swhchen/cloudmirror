/**
 * 云镜推演 · UI 端到端冒烟脚本（零依赖，用 Node 22 自带 fetch + WebSocket 驱动 Edge CDP）
 *
 * 前置：Edge 以 --remote-debugging-port=9222 打开 http://127.0.0.1:4173/
 * 运行：node tools/ui-e2e.mjs
 */
import fs from 'node:fs'

const DEBUG_PORT = 9222
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function findPageTarget() {
  for (let i = 0; i < 30; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)).json()
      const page = list.find((t) => t.type === 'page' && t.url.includes('127.0.0.1:4173'))
      if (page) return page
    } catch {
      // 等 Edge 起来
    }
    await sleep(500)
  }
  throw new Error('没找到 Edge 页面，检查 9222 端口')
}

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl)
    let id = 0
    const pending = new Map()
    ws.onopen = () => resolve({ send, close: () => ws.close() })
    ws.onerror = (e) => reject(new Error('WebSocket 错误 ' + e.message))
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && pending.has(msg.id)) {
        const { resolve: res, reject: rej } = pending.get(msg.id)
        pending.delete(msg.id)
        if (msg.error) rej(new Error(JSON.stringify(msg.error)))
        else res(msg.result)
      }
    }
    function send(method, params = {}) {
      const msgId = ++id
      return new Promise((res, rej) => {
        pending.set(msgId, { resolve: res, reject: rej })
        ws.send(JSON.stringify({ id: msgId, method, params }))
      })
    }
  })
}

const HELPERS = `
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const txt = (el) => (el ? (el.textContent || '').trim() : '');
const all = (sel) => Array.from(document.querySelectorAll(sel));
const byText = (sel, t) => all(sel).find(e => txt(e).includes(t));
const clickBtn = (t) => { const b = byText('button', t); if (!b) throw new Error('找不到按钮: ' + t); b.click(); return true; };
const pd = (el) => {
  const r = el.getBoundingClientRect();
  const x = r.x + r.width / 2, y = r.y + r.height / 2;
  el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 }));
  return { x, y };
};
const setInput = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); };
`

async function main() {
  const target = await findPageTarget()
  const client = await connect(target.webSocketDebuggerUrl)
  await client.send('Runtime.enable')
  await client.send('Page.enable')
  // 强制刷新，确保加载的是最新构建
  await client.send('Page.navigate', { url: 'http://127.0.0.1:4173/' })
  await sleep(3000)

  const results = []
  const evalJs = async (body, label) => {
    const expression = `(async () => { ${HELPERS} ${body} })()`
    const res = await client.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true
    })
    if (res.exceptionDetails) {
      throw new Error(res.exceptionDetails.exception?.description || res.exceptionDetails.text)
    }
    const value = res.result.value
    results.push({ label, ok: true, detail: value })
    console.log(`✅ ${label}`, JSON.stringify(value))
    return value
  }

  const step = async (label, body) => {
    try {
      return await evalJs(body, label)
    } catch (e) {
      results.push({ label, ok: false, detail: String(e.message) })
      console.log(`❌ ${label} — ${e.message}`)
      return null
    }
  }

  // 0. 页面就绪
  await step('页面加载', `
    for (let i = 0; i < 40; i++) { if (document.querySelector('.canvas')) break; await sleep(250); }
    return { canvas: !!document.querySelector('.canvas'), projects: all('.proj option').length };
  `)

  // 1. 新建工程
  await step('新建工程', `
    clickBtn('新建工程'); await sleep(250);
    const input = document.querySelector('.dialog input');
    if (!input) throw new Error('没有弹出新建工程对话框');
    setInput(input, 'E2E-' + Date.now());
    clickBtn('确定'); await sleep(1500);
    return { current: document.querySelector('.proj').selectedOptions[0].textContent };
  `)

  // 2. 加三个节点
  await step('添加节点', `
    const add = async (label) => { const item = byText('.palette-item', label); if (!item) throw new Error('节点库没有 ' + label); item.click(); await sleep(700); };
    await add('应用服务'); await add('中间件'); await add('物理机');
    return { nodes: all('.node-name').length };
  `)

  // 3. 改型号 → 资源字段应自动填充
  await step('改型号自动填资源', `
    const hostNode = all('g[data-node-id]').find(g => txt(g).includes('物理机'));
    if (!hostNode) throw new Error('没找到宿主机节点');
    pd(hostNode); await sleep(400);
    const selects = all('.inspector select');
    const modelSelect = selects[1];
    if (!modelSelect) throw new Error('属性面板没有型号下拉');
    const option = [...modelSelect.options].find(o => txt(o) !== '不指定' && o.value !== '');
    if (!option) throw new Error('没有可选型号');
    const before = document.querySelectorAll('.inspector input')[1].value;
    modelSelect.value = option.value;
    modelSelect.dispatchEvent(new Event('change', { bubbles: true }));
    await sleep(300);
    const after = document.querySelectorAll('.inspector input')[1].value;
    return { model: txt(option), cpuBefore: before, cpuAfter: after };
  `)

  // 4. 连线（点端口两次）
  await step('端口连线', `
    const ports = all('g.port[data-port-id]');
    if (ports.length < 2) throw new Error('端口不足，无法连线：' + ports.length);
    const byNode = new Map();
    ports.forEach(p => {
      const id = p.getAttribute('data-node-id');
      if (!byNode.has(id)) byNode.set(id, []);
      byNode.get(id).push(p);
    });
    const keys = [...byNode.keys()];
    if (keys.length < 2) throw new Error('可连线的节点不足');
    pd(byNode.get(keys[0])[0]); await sleep(200);
    pd(byNode.get(keys[1])[0]); await sleep(1200);
    return { edges: all('.edge-hit').length, toast: txt(document.querySelector('.toast')) };
  `)

  // 4. 校验
  await step('校验可运行性', `
    clickBtn('校验可运行性'); await sleep(1600);
    return {
      status: txt(document.querySelector('.status')),
      issues: all('.issue').length,
      summary: txt(document.querySelector('.summary'))
    };
  `)

  // 5. 选中节点 + 注入故障
  await step('注入故障', `
    const node = document.querySelector('g[data-node-id]');
    if (!node) throw new Error('画布上没有节点');
    pd(node); await sleep(400);
    const inject = byText('button', '注入');
    if (!inject) throw new Error('属性面板没有注入按钮');
    inject.click(); await sleep(1400);
    return { faults: txt(document.querySelector('.dock-body')) .slice(0, 60) };
  `)

  // 6. 推演分析
  await step('推演分析', `
    clickBtn('推演分析'); await sleep(1600);
    return {
      summary: txt(document.querySelector('.dock-body .summary')),
      affected: all('.dock-body .issue').length
    };
  `)

  // 7. 恢复方案
  await step('恢复方案', `
    clickBtn('恢复方案'); await sleep(1600);
    return {
      summary: txt(document.querySelector('.dock-body .summary')).slice(0, 80),
      strategies: all('.strategy').length
    };
  `)

  // 8. 增强分析
  await step('增强分析', `
    clickBtn('增强分析'); await sleep(1600);
    return { rows: all('.insight-row').length, text: txt(document.querySelector('.dock-body')).slice(0, 80) };
  `)

  // 9. AI 诊断
  await step('AI 诊断', `
    clickBtn('AI 诊断'); await sleep(2500);
    const pre = document.querySelector('.ai-text');
    return { chars: pre ? txt(pre).length : 0, source: txt(document.querySelector('.dock-body .chip')) };
  `)

  // 10. 历史
  await step('历史抽屉', `
    clickBtn('历史'); await sleep(1500);
    const items = all('.history-item').length;
    byText('button', '关闭')?.click();
    await sleep(300);
    return { items };
  `)

  // 11. 删除连线
  await step('删除连线', `
    const edge = document.querySelector('.edge-hit');
    if (!edge) throw new Error('没有连线可删');
    pd(edge); await sleep(300);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Delete', bubbles: true }));
    await sleep(1400);
    return { edges: all('.edge-hit').length, toast: txt(document.querySelector('.toast')) };
  `)

  // 12. 删除节点
  await step('删除节点', `
    const node = document.querySelector('g[data-node-id]');
    if (!node) throw new Error('没有节点可删');
    pd(node); await sleep(400);
    const del = document.querySelector('.node-del');
    if (!del) throw new Error('选中节点后没有出现 × 删除按钮');
    pd(del); await sleep(1500);
    return { nodes: all('.node-name').length, toast: txt(document.querySelector('.toast')) };
  `)

  // 截图留证
  const shot = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('docs/screenshots/e2e.png', Buffer.from(shot.data, 'base64'))

  const failed = results.filter((r) => !r.ok)
  console.log(`\n===== 结果：${results.length - failed.length}/${results.length} 通过 =====`)
  if (failed.length) {
    console.log('失败项：')
    failed.forEach((f) => console.log(` - ${f.label}: ${f.detail}`))
    process.exitCode = 1
  }
  client.close()
}

main().catch((e) => {
  console.error('E2E 失败：', e.message)
  process.exit(1)
})

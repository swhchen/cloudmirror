/**
 * 云镜推演 · UI 进阶冒烟：端口选择器 / 右键菜单 / Esc 取消 / 空白处建节点 / 历史恢复 tab / 自动布局 / 删除工程
 * 前置：Edge --remote-debugging-port=9222，页面 http://127.0.0.1:4173/
 */
import fs from 'node:fs'

const PORT = 9222
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function findPage() {
  for (let i = 0; i < 30; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const p = list.find((t) => t.type === 'page' && t.url.includes('127.0.0.1:4173'))
      if (p) return p
    } catch {}
    await sleep(500)
  }
  throw new Error('没找到页面')
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url)
    let id = 0
    const pending = new Map()
    const errors = []
    ws.onopen = () => resolve({ send, close: () => ws.close(), errors })
    ws.onerror = (e) => reject(new Error('WS ' + e.message))
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.method === 'Runtime.exceptionThrown') {
        errors.push(msg.params.exceptionDetails?.exception?.description || msg.params.text)
      }
      if (msg.id && pending.has(msg.id)) {
        const { res, rej } = pending.get(msg.id)
        pending.delete(msg.id)
        msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result)
      }
    }
    function send(method, params = {}) {
      const i = ++id
      return new Promise((res, rej) => {
        pending.set(i, { res, rej })
        ws.send(JSON.stringify({ id: i, method, params }))
      })
    }
  })
}

const HELPERS = `
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const txt = (el) => (el ? (el.textContent || '').trim() : '');
const all = (sel) => Array.from(document.querySelectorAll(sel));
const byText = (sel, t) => all(sel).find(e => txt(e).includes(t));
const clickBtn = (t) => { const b = byText('button', t); if (!b) throw new Error('找不到按钮: ' + t); b.click(); };
const pd = (el, x, y) => {
  const r = el.getBoundingClientRect();
  const px = x === undefined ? r.x + r.width / 2 : x;
  const py = y === undefined ? r.y + r.height / 2 : y;
  el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, clientX: px, clientY: py, button: 0 }));
  return { x: px, y: py };
};
const setInput = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); };
`

async function main() {
  const page = await findPage()
  const c = await connect(page.webSocketDebuggerUrl)
  await c.send('Runtime.enable')
  await c.send('Page.enable')
  await c.send('Page.navigate', { url: 'http://127.0.0.1:4173/' })
  await sleep(3000)

  const results = []
  const step = async (label, body) => {
    const expression = `(async () => { ${HELPERS} ${body} })()`
    try {
      const res = await c.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (res.exceptionDetails) {
        throw new Error(res.exceptionDetails.exception?.description || res.exceptionDetails.text)
      }
      results.push({ label, ok: true, detail: res.result.value })
      console.log('✅', label, JSON.stringify(res.result.value))
      return res.result.value
    } catch (e) {
      results.push({ label, ok: false, detail: String(e.message) })
      console.log('❌', label, '—', e.message)
      return null
    }
  }

  await step('新建工程', `
    clickBtn('新建工程'); await sleep(300);
    const input = document.querySelector('.dialog input');
    if (!input) throw new Error('没弹出对话框');
    setInput(input, 'Extra-' + Date.now());
    clickBtn('确定'); await sleep(1500);
    return { current: document.querySelector('.proj').selectedOptions[0].textContent };
  `)

  await step('加一个宿主机', `
    byText('.palette-item', '物理机').click(); await sleep(900);
    return { nodes: all('.node-name').length };
  `)

  await step('右键菜单加 5 个端口', `
    const node = document.querySelector('g[data-node-id]');
    if (!node) throw new Error('没有节点');
    for (const port of [9104, 9105, 9106, 9107, 9108]) {
      const r = node.getBoundingClientRect();
      node.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX: r.x + 40, clientY: r.y + 30 }));
      await sleep(200);
      const add = byText('.ctx-menu button', '添加端口');
      if (!add) throw new Error('右键菜单里没有「添加端口」');
      add.click(); await sleep(250);
      const input = document.querySelector('.dialog input');
      if (!input) throw new Error('添加端口没有弹窗');
      setInput(input, String(port));
      clickBtn('确定'); await sleep(700);
    }
    const nodeText = txt(document.querySelector('g[data-node-id]'));
    return { portsShown: (nodeText.match(/\\d{4}/g) || []).Length, hasMore: !!document.querySelector('.port-more-btn') };
  `)

  await step('点 +N 打开端口选择器', `
    const more = document.querySelector('.port-more-btn');
    if (!more) throw new Error('没有 +N 折叠按钮');
    pd(more);
    await sleep(300);
    const dlg = document.querySelector('.port-dialog');
    if (!dlg) throw new Error('端口选择器没打开');
    const items = all('.port-dialog .port-item');
    items[0].click();
    await sleep(300);
    return { items: items.length, closed: !document.querySelector('.port-dialog') };
  `)

  await step('Esc 取消连线', `
    const port = document.querySelector('g.port[data-port-id]');
    if (!port) throw new Error('没有端口');
    pd(port); await sleep(200);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await sleep(300);
    return { edges: all('.edge-hit').length };
  `)

  await step('拖到空白处新建节点并连线', `
    const port = document.querySelector('g.port[data-port-id]');
    if (!port) throw new Error('没有端口');
    const before = all('.node-name').length;
    pd(port); await sleep(150);
    const svg = document.querySelector('.canvas');
    const sr = svg.getBoundingClientRect();
    const x = sr.x + sr.width * 0.75, y = sr.y + sr.height * 0.5;
    window.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, clientX: x, clientY: y }));
    await sleep(400);
    const dlg = document.querySelector('.dialog');
    if (!dlg) throw new Error('空白处松手没有弹出新建对话框');
    const title = txt(dlg.querySelector('.dialog-title'));
    const input = dlg.querySelector('input');
    setInput(input, '空白节点');
    clickBtn('确定');
    await sleep(1600);
    return { title, nodesBefore: before, nodesAfter: all('.node-name').length, edges: all('.edge-hit').length };
  `)

  await step('历史 → 恢复方案 tab', `
    clickBtn('历史'); await sleep(1200);
    const tab = byText('.history-tabs button', '恢复方案');
    if (!tab) throw new Error('历史里没有「恢复方案」tab');
    tab.click(); await sleep(1200);
    const items = all('.history-item').length;
    byText('button', '关闭').click(); await sleep(300);
    return { items };
  `)

  await step('自动布局', `
    const before = all('g[data-node-id]').map(g => Math.round(g.getBoundingClientRect().x));
    clickBtn('自动布局'); await sleep(1600);
    const after = all('g[data-node-id]').map(g => Math.round(g.getBoundingClientRect().x));
    const toast = txt(document.querySelector('.toast'));
    const distinct = new Set(after).size;
    return { toast, distinctX: distinct, nodes: after.length };
  `)

  await step('删除工程（输入工程名确认）', `
    const before = all('.proj option').length;
    const name = document.querySelector('.proj').selectedOptions[0].textContent.trim();
    clickBtn('删除工程'); await sleep(300);
    const input = document.querySelector('.dialog input');
    if (!input) throw new Error('删除工程没有弹窗');
    setInput(input, name);
    clickBtn('确定'); await sleep(1800);
    return { before, after: all('.proj option').length, current: document.querySelector('.proj').selectedOptions[0].textContent };
  `)

  const shot = await c.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('docs/screenshots/e2e-extra.png', Buffer.from(shot.data, 'base64'))

  console.log('JS 异常：', c.errors.length ? c.errors.slice(0, 3) : '无')
  const failed = results.filter((r) => !r.ok)
  console.log(`\n===== 结果：${results.length - failed.length}/${results.length} 通过 =====`)
  if (failed.length) {
    failed.forEach((f) => console.log(' -', f.label, ':', f.detail))
    process.exitCode = 1
  }
  c.close()
}

main().catch((e) => {
  console.error('失败', e.message)
  process.exit(1)
})

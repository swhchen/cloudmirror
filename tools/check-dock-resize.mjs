/** 验证底部面板拖拽：拖动变高、双击还原、刷新后保持 */
import fs from 'node:fs'

const PORT = 9222
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
const page = list.find((t) => t.type === 'page' && t.url.includes('127.0.0.1:4173'))
if (!page) throw new Error('no page')
const ws = new WebSocket(page.webSocketDebuggerUrl)
let id = 0
const pending = new Map()
const send = (method, params = {}) => new Promise((res, rej) => {
  const i = ++id
  pending.set(i, { res, rej })
  ws.send(JSON.stringify({ id: i, method, params }))
})
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data)
  if (m.id && pending.has(m.id)) {
    const { res, rej } = pending.get(m.id)
    pending.delete(m.id)
    m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result)
  }
}
await new Promise((r) => (ws.onopen = r))
await send('Page.enable')
await send('Page.navigate', { url: 'http://127.0.0.1:4173/?project=37' })
await sleep(3500)

const evaluate = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })).result.value

const before = await evaluate(`document.querySelector('.dock').offsetHeight`)
console.log('初始高度', before)

const rect = await evaluate(`(()=>{const r=document.querySelector('.dock-resizer').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`)
await evaluate(`(()=>{
  const r = document.querySelector('.dock-resizer');
  const y = r.getBoundingClientRect().y + 2;
  r.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,clientX:200,clientY:y}));
  window.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,clientX:200,clientY:y-220}));
  window.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,clientX:200,clientY:y-220}));
  return true;
})()`)
await sleep(400)
const afterDrag = await evaluate(`document.querySelector('.dock').offsetHeight`)
console.log('拖动后高度', afterDrag)

await evaluate(`(()=>{const r=document.querySelector('.dock-resizer');r.dispatchEvent(new MouseEvent('dblclick',{bubbles:true}));return true})()`)
await sleep(300)
const afterReset = await evaluate(`document.querySelector('.dock').offsetHeight`)
console.log('双击还原后', afterReset)

// 再拖一次，刷新验证持久化
await evaluate(`(()=>{
  const r = document.querySelector('.dock-resizer');
  const y = r.getBoundingClientRect().y + 2;
  r.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,clientX:200,clientY:y}));
  window.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,clientX:200,clientY:y-150}));
  window.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,clientX:200,clientY:y-150}));
  return true;
})()`)
await sleep(400)
const saved = await evaluate(`localStorage.getItem('cm.dockHeight')`)
await send('Page.reload')
await sleep(3000)
const afterReload = await evaluate(`document.querySelector('.dock').offsetHeight`)
console.log('localStorage', saved, '刷新后高度', afterReload)

const ok = afterDrag > before + 100 && Math.abs(afterReset - 190) <= 2 && Math.abs(afterReload - Number(saved)) <= 2
console.log(ok ? '✅ 拖拽 / 还原 / 持久化 正常' : '❌ 异常')
ws.close()

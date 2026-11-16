/**
 * 为 PPT 素材包批量截图：校验 / 推演 / 恢复 / 增强 / AI / 历史
 * 前置：Edge --remote-debugging-port=9222，工程 37 已注入 MySQL-主 故障
 */
import fs from 'node:fs'

const PORT = 9222
const OUT = 'C:/Users/swhc6/Desktop/AI编程专属文件/云镜推演/docs/screenshots'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
const page = list.find((t) => t.type === 'page' && t.url.includes('127.0.0.1:4173'))
if (!page) throw new Error('没找到页面')
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
await send('Runtime.enable')
await send('Page.navigate', { url: 'http://127.0.0.1:4173/?project=37' })
await sleep(4000)

const click = (text) => send('Runtime.evaluate', {
  expression: `(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.textContent.trim().includes(${JSON.stringify(text)}));if(!b)throw new Error('没找到按钮 '+${JSON.stringify(text)});b.click();return true})()`,
  awaitPromise: true,
  returnByValue: true
})
const shot = async (name) => {
  const r = await send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${OUT}/${name}`, Buffer.from(r.data, 'base64'))
  console.log('saved', name)
}

// 1. 校验
await click('校验可运行性')
await sleep(2000)
await shot('ppt-validate.png')

// 2. 推演
await click('推演分析')
await sleep(2000)
await shot('ppt-analysis.png')

// 3. 恢复
await click('恢复方案')
await sleep(2000)
await shot('ppt-recovery.png')

// 4. 增强（含级联）
await click('增强')
await sleep(2000)
await shot('ppt-insights.png')

// 5. AI 诊断
await click('AI 诊断')
await sleep(3500)
await shot('ppt-ai.png')

// 6. 历史
await click('历史')
await sleep(2000)
await shot('ppt-history.png')

console.log('done')
ws.close()

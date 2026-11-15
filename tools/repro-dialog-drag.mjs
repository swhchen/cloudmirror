/**
 * 复现：打开「新建推演工程」弹窗后，按住鼠标拖到弹窗外再松开，页面是否异常。
 * 用法：node tools/repro-dialog-drag.mjs
 */
import fs from 'node:fs'

const DEBUG_PORT = 9222
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function findPage() {
  for (let i = 0; i < 20; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)).json()
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
    const events = []
    ws.onopen = () => resolve({ send, close: () => ws.close(), events })
    ws.onerror = (e) => reject(new Error('WS ' + e.message))
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.method === 'Runtime.exceptionThrown') events.push(msg.params)
      if (msg.method === 'Log.entryAdded') events.push(msg.params)
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

const main = async () => {
  const page = await findPage()
  const c = await connect(page.webSocketDebuggerUrl)
  await c.send('Runtime.enable')
  await c.send('Page.enable')
  await c.send('Log.enable')
  await c.send('Page.navigate', { url: 'http://127.0.0.1:4173/' })
  await sleep(3000)

  // 打开新建工程弹窗
  await c.send('Runtime.evaluate', {
    expression: `(async()=>{const b=[...document.querySelectorAll('button')].find(x=>x.textContent.includes('新建工程'));b.click();await new Promise(r=>setTimeout(r,400));return true})()`,
    awaitPromise: true
  })

  const pos = await c.send('Runtime.evaluate', {
    expression: `(()=>{const i=document.querySelector('.dialog input');if(!i)return null;const r=i.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`,
    returnByValue: true
  })
  const { x, y } = pos.result.value
  console.log('输入框坐标', x, y)

  // 真实鼠标：按下 → 拖到画布外（左上角）→ 松开
  await c.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 })
  await sleep(100)
  for (const step of [0.2, 0.5, 0.8, 1]) {
    await c.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: x + (10 - x) * step,
      y: y + (10 - y) * step,
      button: 'left',
      buttons: 1
    })
    await sleep(80)
  }
  await c.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 10, y: 10, button: 'left' })
  await sleep(600)

  const state = await c.send('Runtime.evaluate', {
    expression: `(()=>{
      const bar = document.querySelector('.topbar');
      const r = bar ? bar.getBoundingClientRect() : null;
      return {
        dialog: !!document.querySelector('.dialog'),
        canvas: !!document.querySelector('.canvas'),
        scrollY: window.scrollY,
        docScrollTop: document.scrollingElement ? document.scrollingElement.scrollTop : -1,
        topbarTop: r ? Math.round(r.top) : null,
        topbarVisible: !!(r && r.top >= 0 && r.height > 10),
        url: location.href
      };
    })()`,
    returnByValue: true
  })
  console.log('页面状态', JSON.stringify(state.result.value, null, 2))

  // 再测：点在遮罩空白处应当关闭弹窗
  await c.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 40, y: 300, button: 'left', clickCount: 1 })
  await c.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 40, y: 300, button: 'left' })
  await sleep(400)
  const closed = await c.send('Runtime.evaluate', {
    expression: `!!document.querySelector('.dialog')`,
    returnByValue: true
  })
  console.log('点遮罩后弹窗还在？', closed.result.value)

  const shot = await c.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('docs/screenshots/repro-dialog-drag.png', Buffer.from(shot.data, 'base64'))
  console.log('异常事件数', c.events.length)
  c.events.slice(0, 3).forEach((e) => console.log(JSON.stringify(e).slice(0, 300)))
  c.close()
}

main().catch((e) => {
  console.error('复现失败', e.message)
  process.exit(1)
})

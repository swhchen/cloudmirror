import { createApp } from 'vue'
import App from './App.vue'
import './styles.css'

const app = createApp(App)

/** 兜底：界面出错时不让用户对着白屏，给一条可操作的提示 */
function showFatalBanner(message) {
  if (document.getElementById('fatal-banner')) return
  const el = document.createElement('div')
  el.id = 'fatal-banner'
  el.style.cssText = [
    'position:fixed', 'left:50%', 'bottom:18px', 'transform:translateX(-50%)',
    'z-index:9999', 'max-width:80vw', 'padding:10px 14px', 'border-radius:8px',
    'background:#7f1d1d', 'border:1px solid #ef4444', 'color:#fee2e2',
    'font:13px/1.5 "Microsoft YaHei",system-ui,sans-serif',
    'box-shadow:0 8px 24px rgba(0,0,0,.45)', 'display:flex', 'gap:10px', 'align-items:center'
  ].join(';')
  const text = document.createElement('span')
  text.textContent = '界面出错了：' + message + '（可刷新页面恢复）'
  const close = document.createElement('button')
  close.textContent = '关闭'
  close.style.cssText = 'border:1px solid #fca5a5;background:transparent;color:#fee2e2;border-radius:6px;padding:2px 8px;cursor:pointer'
  close.onclick = () => el.remove()
  el.appendChild(text)
  el.appendChild(close)
  document.body.appendChild(el)
}

app.config.errorHandler = (err, _instance, info) => {
  console.error('[vue error]', info, err)
  showFatalBanner((err && err.message) || String(err))
}

window.addEventListener('unhandledrejection', (event) => {
  console.error('[unhandled rejection]', event.reason)
  showFatalBanner((event.reason && event.reason.message) || String(event.reason))
})

app.mount('#app')

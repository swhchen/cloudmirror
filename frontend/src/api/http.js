import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (resp) => resp.data,
  (error) => {
    const status = error.response && error.response.status
    const payload = error.response && error.response.data
    let message = payload && payload.message ? payload.message : error.message
    if (status === 403 && (!payload || !payload.message)) {
      message = '跨域被拒绝（403）：后端 CORS 未放行当前页面端口，请检查 CorsConfig'
    }
    const traceId = error.response && error.response.headers['x-trace-id']
    return Promise.reject(new Error(traceId ? `${message}（traceId ${traceId}）` : message))
  }
)

export default http

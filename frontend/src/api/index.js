import http from './http'

// ---------- 工程 ----------
export const listProjects = () => http.get('/projects')
export const createProject = (body) => http.post('/projects', body)
export const getProject = (id) => http.get(`/projects/${id}`)
export const updateProject = (id, body) => http.put(`/projects/${id}`, body)
export const deleteProject = (id) => http.delete(`/projects/${id}`)
export const saveView = (id, body) => http.put(`/projects/${id}/view`, body)

// ---------- 节点 / 端口 ----------
export const listNodes = (id) => http.get(`/projects/${id}/nodes`)
export const createNode = (id, body) => http.post(`/projects/${id}/nodes`, body)
export const updateNode = (id, nodeId, body) => http.put(`/projects/${id}/nodes/${nodeId}`, body)
export const deleteNode = (id, nodeId) => http.delete(`/projects/${id}/nodes/${nodeId}`)
export const savePositions = (id, positions) =>
  http.put(`/projects/${id}/nodes/positions`, { positions })

// ---------- 连线 ----------
export const listEdges = (id) => http.get(`/projects/${id}/edges`)
export const createEdge = (id, body) => http.post(`/projects/${id}/edges`, body)
export const deleteEdge = (id, edgeId) => http.delete(`/projects/${id}/edges/${edgeId}`)

// ---------- 型号 ----------
export const listModels = (kind) =>
  http.get('/node-models', { params: kind ? { kind } : {} })

// ---------- 校验 ----------
export const runValidate = (id) => http.post(`/projects/${id}/validate`)
export const latestValidate = (id) => http.get(`/projects/${id}/validations/latest`)

// ---------- 故障 ----------
export const listFaults = (id) => http.get(`/projects/${id}/faults`)
export const injectFault = (id, body) => http.post(`/projects/${id}/faults`, body)
export const recoverFault = (id, faultId) =>
  http.post(`/projects/${id}/faults/${faultId}/recover`)

// ---------- 传播 ----------
export const runAnalysis = (id) => http.post(`/projects/${id}/analyses`)
export const listAnalyses = (id, page = 1, size = 10) =>
  http.get(`/projects/${id}/analyses`, { params: { page, size } })
export const getAnalysis = (id, analysisId) => http.get(`/projects/${id}/analyses/${analysisId}`)

// ---------- 恢复 ----------
export const runRecovery = (id, analysisId) =>
  http.post(`/projects/${id}/recovery/recommend`, null, {
    params: analysisId ? { analysisId } : {}
  })
export const listRecoveryPlans = (id, limit = 20) =>
  http.get(`/projects/${id}/recovery/plans`, { params: { limit } })

// ---------- 增强分析（E2 割点 / E3 隔离切口 / E4 恢复序列） ----------
export const getInsights = (id) => http.get(`/projects/${id}/insights`)

// ---------- AI 诊断 ----------
export const diagnose = (id) => http.post(`/projects/${id}/ai/diagnose`)

// ---------- 冗余组 ----------
export const listGroups = (id) => http.get(`/projects/${id}/redundancy-groups`)
export const createGroup = (id, body) => http.post(`/projects/${id}/redundancy-groups`, body)
export const deleteGroup = (id, groupId) =>
  http.delete(`/projects/${id}/redundancy-groups/${groupId}`)

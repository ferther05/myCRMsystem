import { http } from './http'

// BASE-01 · docs/api/01-基础健康检查.md
export const getHealth = () => http.get('/health', { public: true })

/* 文件作用：封装基础依赖健康检查接口。 */
import { http } from './http';

// BASE-01 · docs/api/01-基础健康检查.md
/** 检查后端、MySQL 和 Redis 健康状态。 */
export const getHealth = () => http.get('/health', { public: true });

/* 文件作用：获取授权范围内的数据看板及负责人筛选项。 */
import { http } from './http';
/** 管理员当前资源与治理统计，独立于经营日期筛选。 */
export const adminResources = () => http.get('/analytics/admin-resources');
/** 查询看板，refresh只绕过统计缓存。 */
export const overview = (params) => http.get('/analytics/overview', { params });
/** 获取本人、本团队或管理员允许选择的负责人。 */
export const owners = () => http.get('/analytics/owners');

/* 文件作用：封装客户回收预览、预警、开关与延期审批接口。 */
import { http } from './http';
/** 管理员分页预览。 */
export const preview = (params) => http.get('/recycle/preview', { params });
/** 获取管理员开关和版本。 */
export const setting = () => http.get('/recycle/setting');
/** 显式启用或关闭自动回收。 */
export const saveSetting = (data) => http.put('/recycle/setting', data);
/** 扫描当天业务，关闭时不改客户。 */
export const scan = () => http.post('/recycle/scan', null, { timeout: 60000 });
/** 查询私海客户回收规则与审批历史。 */
export const detail = (id) => http.get(`/recycle/customers/${id}`);
/** 管理员明确向单客户发送预警。 */
export const warn = (id, version) => http.post(`/recycle/customers/${id}/warning`, { version });
/** 负责人提出延期原因与计划。 */
export const appeal = (id, data) => http.post(`/recycle/customers/${id}/appeals`, data);
/** 非申请人经理或管理员核验延期。 */
export const review = (id, data) => http.post(`/recycle/appeals/${id}/review`, data);

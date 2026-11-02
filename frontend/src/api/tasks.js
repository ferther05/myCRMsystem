/* 文件作用：统一封装待办、操作记录和管理员扫描管理接口。 */
import { http } from './http';

/** 分页查询本人待办。 */
export const listTodos = (params) => http.get('/tasks/todos', { params });
/** 已读仅更新当前接收人的阅读标记。 */
export const readTodo = (id, version) => http.post(`/tasks/todos/${id}/read`, { version });
/** 点击时重新授权并定位关联业务。 */
export const todoTarget = (id) => http.get(`/tasks/todos/${id}/target`);
/** 查询当前业务范围内的操作摘要。 */
export const listOperations = (params) => http.get('/tasks/operations', { params });
/** 读取已授权日志的脱敏字段对比。 */
export const operationDetail = (id) => http.get(`/tasks/operations/${id}`);
/** 管理员分页查询执行尝试。 */
export const listJobs = (params) => http.get('/tasks/jobs', { params });
/** 管理员执行当天的白名单扫描。 */
export const scanJob = (jobName) => http.post('/tasks/jobs/scan', { jobName }, { timeout: 60000 });
/** 只补跑所选最新失败尝试的失败对象。 */
export const retryJob = (id) => http.post(`/tasks/jobs/${id}/retry`, null, { timeout: 60000 });

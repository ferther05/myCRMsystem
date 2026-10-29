/* 文件作用：封装商机、阶段证据、协作、活跃度与漏斗接口。 */
import { http } from './http';

/** 分页查询商机。 */
export const listOpportunities = (params) => http.get('/opportunities', { params });
/** 读取商机详情及阶段门槛。 */
export const getOpportunity = (id) => http.get(`/opportunities/${id}`);
/** 由管理员创建并分配商机。 */
export const createOpportunity = (payload) => http.post('/opportunities', payload);
/** 保存商机基本字段，不直接推进阶段。 */
export const editOpportunity = (id, payload) => http.put(`/opportunities/${id}`, payload);
/** 提交管理员带原因的阶段修正。 */
export const changeOpportunityStage = (id, payload) =>
  http.patch(`/opportunities/${id}/stage`, payload);
/** 更新当前商机的活跃度阈值。 */
export const updateOpportunityThreshold = (id, payload) =>
  http.patch(`/opportunities/${id}/threshold`, payload);
/** 添加当前商机协作人。 */
export const addOpportunityCollaborator = (id, payload) =>
  http.post(`/opportunities/${id}/collaborators`, payload);
/** 撤销当前商机的指定协作授权。 */
export const removeOpportunityCollaborator = (id, collaboratorId, version) =>
  http.delete(`/opportunities/${id}/collaborators/${collaboratorId}`, { params: { version } });
/** 查询阶段漏斗的数量和预计金额。 */
export const getOpportunityFunnel = (params) => http.get('/opportunities/funnel', { params });
/** 登记报价或客户合作确认证据。 */
export const recordOpportunityEvidence = (id, payload) =>
  http.post(`/opportunities/${id}/evidence`, payload);
/** 查询当前商机可添加的团队成员。 */
export const listOpportunityMembers = (id) => http.get(`/opportunities/${id}/members`);
/** 提交关联当前商机的有效跟进。 */
export const addOpportunityFollow = (id, payload) =>
  http.post(`/opportunities/${id}/follow-records`, payload);
/** 提交待关闭商机裁决。 */
export const decideOpportunityClose = (id, payload) =>
  http.post(`/opportunities/${id}/close-decision`, payload);
/** 请求管理员手动扫描商机活跃度。 */
export const scanOpportunityActivity = () => http.post('/opportunities/activity-scan');
/** 查询商机可选的负责人。 */
export const listOpportunityOwners = () => http.get('/opportunities/owners');

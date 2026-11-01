/* 文件作用：封装合同登记、签署进度与附件版本接口。 */
import axios from 'axios';
import { http } from './http';
import { readToken } from '@/utils/session';

/** 分页查询当前可见合同。 */
export const listContracts = (params) => http.get('/contracts', { params });
/** 分页查询可登记合同的成交商机。 */
export const listRegistrationOpportunities = (params) =>
  http.get('/contracts/registration-opportunities', { params });
/** 读取合同详情及版本历史。 */
export const getContract = (id) => http.get(`/contracts/${id}`);
/** 由成交商机创建合同登记草稿。 */
export const createContractFromOpportunity = (opportunityId) =>
  http.post('/contracts/from-opportunity', { opportunityId });
/** 保存合同登记字段。 */
export const editContract = (id, payload) => http.put(`/contracts/${id}`, payload);
/** 提交在线登记表供经理或管理员核对，无需草稿文件。 */
export const submitContractDraft = (id, payload) => http.post(`/contracts/${id}/draft-submit`, payload);
/** 负责人撤回待审登记，重新开放表单修改。 */
export const withdrawContractDraft = (id, payload) => http.post(`/contracts/${id}/draft-withdraw`, payload);
/** 提交合同资料供内部核验。 */
export const submitContract = (id, payload) => http.post(`/contracts/${id}/submit`, payload);
/** 通过或退回合同资料。 */
export const reviewContract = (id, payload) => http.post(`/contracts/${id}/review`, payload);
/** 提交合同签署节点及操作原因。 */
export const updateSigningProgress = (id, payload) =>
  http.patch(`/contracts/${id}/signing-progress`, payload);
/** 上传资料并创建合同附件新版本。 */
export const uploadContractAttachment = (id, formData) =>
  http.post(`/contracts/${id}/attachments`, formData);

/** 下载受权限保护的指定附件。 */
export async function downloadContractAttachment(id, attachmentId, fileName) {
  const response = await axios.get(`/api/contracts/${id}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
    headers: readToken() ? { Authorization: `Bearer ${readToken()}` } : {},
  });
  const url = URL.createObjectURL(response.data);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName || 'contract-file';
  anchor.click();
  URL.revokeObjectURL(url);
}

/* 文件作用：封装客户档案、联系人、跟进、公海领取和字典接口。 */
import { http } from './http';

/** 分页查询客户档案。 */
export const listCustomers = (params) => http.get('/customers', { params });
/** 读取客户详情和允许的操作。 */
export const getCustomer = (id) => http.get(`/customers/${id}`);
/** 由管理员创建并分配客户。 */
export const createCustomer = (payload) => http.post('/customers', payload);
/** 保存客户基本档案。 */
export const editCustomer = (id, payload) => http.put(`/customers/${id}`, payload);
/** 保存客户等级和标签。 */
export const classifyCustomer = (id, payload) =>
  http.patch(`/customers/${id}/classification`, payload);
/** 按版本逻辑删除客户。 */
export const deleteCustomer = (id, version) =>
  http.delete(`/customers/${id}`, { params: { version } });
/** 查询可分配的启用经理和专员。 */
export const listCustomerOwners = () => http.get('/customers/owners');
/** 新增客户联系人。 */
export const addContact = (id, payload) => http.post(`/customers/${id}/contacts`, payload);
/** 保存指定客户联系人的资料。 */
export const editContact = (id, contactId, payload) =>
  http.put(`/customers/${id}/contacts/${contactId}`, payload);
/** 删除指定客户联系人。 */
export const deleteContact = (id, contactId, customerVersion) =>
  http.delete(`/customers/${id}/contacts/${contactId}`, { params: { customerVersion } });
/** 提交结构化客户跟进。 */
export const addFollowRecord = (id, payload) =>
  http.post(`/customers/${id}/follow-records`, payload);
/** 提交跟进确认或打回意见。 */
export const reviewFollowRecord = (id, followId, payload) =>
  http.patch(`/customers/${id}/follow-records/${followId}/review`, payload);
/** 按当前版本申领公海客户。 */
export const claimCustomer = (id, version) =>
  http.post(`/customers/${id}/claim?version=${version}`);
/** 提交客户负责人转交。 */
export const transferCustomer = (id, payload) => http.patch(`/customers/${id}/ownership`, payload);
/** 查询客户分类字典。 */
export const listCustomerDictionaries = () => http.get('/customer-dictionaries');
/** 新增客户分类字典项。 */
export const createCustomerDictionary = (payload) => http.post('/customer-dictionaries', payload);
/** 更新客户分类字典项。 */
export const editCustomerDictionary = (id, payload) =>
  http.put(`/customer-dictionaries/${id}`, payload);

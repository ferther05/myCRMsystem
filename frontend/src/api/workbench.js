/* 文件作用：查询当前身份工作台的实时汇总及分页事项。 */
import { http } from './http';
/** 汇总不接受客户端指定人员或团队。 */
export const overview = () => http.get('/workbench/overview');
/** 每页固定十条，分类由后端按角色白名单校验。 */
export const items = (params) => http.get('/workbench/items', { params });

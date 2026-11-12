/* 文件作用：封装登录、当前身份、改密、退出和个人资料接口。 */
import { http } from './http';

// AUTH-01 / AUTH-02 / AUTH-03 / AUTH-04 · docs/api/02-账号认证与密码.md
/** 提交账号密码取得登录令牌。 */
export const login = (payload) => http.post('/auth/login', payload, { public: true });
/** 查询当前登录用户的最新资料。 */
export const currentUser = () => http.get('/auth/me');
/** 提交旧密码与新密码完成改密。 */
export const changePassword = (payload) => http.post('/auth/password', payload);
/** 明确选择跳过首次改密提醒，更新会话。 */
export const skipPassword = () => http.post('/auth/password/skip');
/** 请求撤销服务端会话。 */
export const logout = () => http.post('/auth/logout');
/** 提交本人资料并取得刷新后的令牌。 */
export const saveProfile = (payload) => http.put('/account/profile', payload);

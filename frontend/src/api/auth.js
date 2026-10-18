import { http } from './http'

// AUTH-01 / AUTH-02 / AUTH-03 / AUTH-04 · docs/api/02-账号认证与密码.md
export const login = (payload) => http.post('/auth/login', payload, { public: true })
export const currentUser = () => http.get('/auth/me')
export const changePassword = (payload) => http.post('/auth/password', payload)
export const logout = () => http.post('/auth/logout')
export const saveProfile = (payload) => http.put('/account/profile', payload)

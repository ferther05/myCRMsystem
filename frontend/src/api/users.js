import { http } from './http'

export const listUsers = (params) => http.get('/users', { params })
export const getUser = (id) => http.get(`/users/${id}`)
export const listTeams = () => http.get('/teams')
export const createUser = (payload) => http.post('/users', payload)
export const changeAccess = (id, payload) => http.put(`/users/${id}/access`, payload)
export const resetPassword = (id, payload) => http.post(`/users/${id}/reset-password`, payload)
export const listApplications = (params) => http.get('/account-applications', { params })
export const applyAccount = (payload) => http.post('/account-applications', payload)
export const reviewApplication = (id, payload) => http.post(`/account-applications/${id}/review`, payload)
export const resubmitApplication = (id, version) => http.post(`/account-applications/${id}/resubmit`, null, { params: { version } })

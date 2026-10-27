import axios from 'axios'
import { http } from './http'
import { readToken } from '@/utils/session'

export const listContracts = (params) => http.get('/contracts', { params })
export const listRegistrationOpportunities = (params) => http.get('/contracts/registration-opportunities', { params })
export const getContract = (id) => http.get(`/contracts/${id}`)
export const createContractFromOpportunity = (opportunityId) => http.post('/contracts/from-opportunity', { opportunityId })
export const editContract = (id, payload) => http.put(`/contracts/${id}`, payload)
export const submitContract = (id, payload) => http.post(`/contracts/${id}/submit`, payload)
export const reviewContract = (id, payload) => http.post(`/contracts/${id}/review`, payload)
export const updateSigningProgress = (id, payload) => http.patch(`/contracts/${id}/signing-progress`, payload)
export const uploadContractAttachment = (id, formData) => http.post(`/contracts/${id}/attachments`, formData)

export async function downloadContractAttachment(id, attachmentId, fileName) {
  const response = await axios.get(`/api/contracts/${id}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
    headers: readToken() ? { Authorization: `Bearer ${readToken()}` } : {},
  })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = fileName || 'contract-file'; anchor.click()
  URL.revokeObjectURL(url)
}

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/api/auth'
import { readToken, saveToken, removeToken } from '@/utils/session'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readToken())
  const user = ref(null)
  const loadedToken = ref('')
  let pending = null
  const authenticated = computed(() => Boolean(token.value && user.value))

  function clear(expectedToken) {
    if (expectedToken && readToken() !== expectedToken) { syncFromStorage(); return }
    token.value = ''
    user.value = null
    loadedToken.value = ''
    pending = null
    removeToken()
  }

  function syncFromStorage() {
    token.value = readToken()
    user.value = null
    loadedToken.value = ''
    pending = null
  }

  async function signIn(payload) {
    clear()
    const data = await api.login(payload)
    saveToken(data.accessToken)
    token.value = data.accessToken
    user.value = data.user
    loadedToken.value = data.accessToken
    return data.user
  }

  async function ensureUser(force = false) {
    const current = readToken()
    if (!current) { clear(); return null }
    if (token.value !== current) user.value = null
    token.value = current
    if (!force && loadedToken.value === current && user.value) return user.value
    if (pending?.token === current) return pending.promise
    const promise = api.currentUser().then((data) => {
      if (readToken() === current) {
        user.value = data
        loadedToken.value = current
      }
      return readToken() === current ? data : null
    }).finally(() => { if (pending?.token === current) pending = null })
    pending = { token: current, promise }
    return promise
  }

  async function signOut() {
    // Keep the session when the network fails so logout can actually be retried server-side.
    const current = readToken()
    await api.logout()
    clear(current)
  }

  async function updateProfile(payload) {
    const current = readToken()
    const data = await api.saveProfile(payload)
    if (readToken() !== current) throw new Error('账号会话已变化，请刷新后重试')
    saveToken(data.accessToken)
    token.value = data.accessToken
    user.value = data.user
    loadedToken.value = data.accessToken
    return data.user
  }

  return { token, user, authenticated, clear, syncFromStorage, signIn, ensureUser, signOut, updateProfile }
})

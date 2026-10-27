import { ref } from 'vue'
import { defineStore } from 'pinia'
import { listCustomerDictionaries } from '@/api/customers'
import { readToken } from '@/utils/session'
import { customerDictionaryLabel } from '@/utils/customer-rules'

export const useCustomerDictionaries = defineStore('customer-dictionaries', () => {
  const rows = ref([]), loadedToken = ref('')
  async function load() {
    const token = readToken()
    if (loadedToken.value !== token) rows.value = []
    const next = await listCustomerDictionaries()
    if (readToken() === token) { rows.value = next; loadedToken.value = token }
  }
  const label = (group, code) => customerDictionaryLabel(rows.value, group, code)
  function options(group, existing = []) {
    const values = Array.isArray(existing) ? existing : [existing]
    const list = rows.value.filter((row) => row.group === group && (row.enabled || values.includes(row.code)))
    // Preserve legacy demonstration codes and disabled historical values when editing.
    for (const code of values.filter(Boolean)) {
      if (!list.some((row) => row.code === code)) list.push({ code, label: label(group, code), enabled: false })
    }
    return list
  }
  return { rows, load, label, options }
})

/* 文件作用：缓存客户字典并提供中文名称及可选项。 */
import { ref } from 'vue';
import { defineStore } from 'pinia';
import { listCustomerDictionaries } from '@/api/customers';
import { readToken } from '@/utils/session';
import { customerDictionaryLabel } from '@/utils/customer-rules';

export const useCustomerDictionaries = defineStore(
  'customer-dictionaries',
  /* 创建客户字典状态及加载、展示方法。 */ () => {
    const rows = ref([]),
      loadedToken = ref('');
    /** 加载当前页面所需的数据并维护加载和错误状态。 */
    async function load() {
      const token = readToken();
      if (loadedToken.value !== token) rows.value = [];
      const next = await listCustomerDictionaries();
      if (readToken() === token) {
        rows.value = next;
        loadedToken.value = token;
      }
    }
    /** 根据字典类型和代码返回中文名称。 */
    const label = (group, code) => customerDictionaryLabel(rows.value, group, code);
    /** 生成可选字典项并保留当前已停用值的展示。 */
    function options(group, existing = []) {
      const values = Array.isArray(existing) ? existing : [existing];
      const list = rows.value.filter(
        /* 按当前业务条件筛选记录。 */ (row) =>
          row.group === group && (row.enabled || values.includes(row.code)),
      );
      // 编辑时保留历史演示代码及已停用值，避免旧记录显示丢失。
      for (const code of values.filter(Boolean)) {
        if (!list.some(/* 判断集合中是否存在满足当前条件的条目。 */ (row) => row.code === code))
          list.push({ code, label: label(group, code), enabled: false });
      }
      return list;
    }
    return { rows, load, label, options };
  },
);

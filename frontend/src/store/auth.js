/* 文件作用：集中管理登录令牌、当前身份和会话同步。 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as api from '@/api/auth';
import { readToken, saveToken, removeToken } from '@/utils/session';

export const useAuthStore = defineStore(
  'auth',
  /* 创建认证状态及登录、身份同步方法。 */ () => {
    const token = ref(readToken());
    const user = ref(null);
    const loadedToken = ref('');
    let pending = null;
    const authenticated = computed(
      /* 判断当前令牌及身份是否齐备。 */ () => Boolean(token.value && user.value),
    );

    /** 清理指定会话的本地令牌和身份，避免误删新会话。 */
    function clear(expectedToken) {
      if (expectedToken && readToken() !== expectedToken) {
        syncFromStorage();
        return;
      }
      token.value = '';
      user.value = null;
      loadedToken.value = '';
      pending = null;
      removeToken();
    }

    /** 从浏览器存储同步令牌并清除旧身份缓存。 */
    function syncFromStorage() {
      token.value = readToken();
      user.value = null;
      loadedToken.value = '';
      pending = null;
    }

    /** 登录并同步令牌、身份和已加载标记。 */
    async function signIn(payload) {
      clear();
      const data = await api.login(payload);
      saveToken(data.accessToken);
      token.value = data.accessToken;
      user.value = data.user;
      loadedToken.value = data.accessToken;
      return data.user;
    }

    /** 复用并发身份查询并防止旧响应覆盖新会话。 */
    async function ensureUser(force = false) {
      const current = readToken();
      if (!current) {
        clear();
        return null;
      }
      if (token.value !== current) user.value = null;
      token.value = current;
      if (!force && loadedToken.value === current && user.value) return user.value;
      if (pending?.token === current) return pending.promise;
      const promise = api
        .currentUser()
        .then(
          /* 仅在令牌仍匹配时更新当前身份，忽略旧会话响应。 */ (data) => {
            if (readToken() === current) {
              user.value = data;
              loadedToken.value = current;
            }
            return readToken() === current ? data : null;
          },
        )
        .finally(
          /* 请求结束后清理对应的进行中标记。 */ () => {
            if (pending?.token === current) pending = null;
          },
        );
      pending = { token: current, promise };
      return promise;
    }

    /** 服务端退出成功后再清理当前本地会话。 */
    async function signOut() {
      // 退出请求失败时保留当前会话，便于重试服务端退出。
      const current = readToken();
      await api.logout();
      clear(current);
    }

    /** 保存本人资料并更新令牌，拒绝旧会话响应覆盖当前身份。 */
    async function updateProfile(payload) {
      const current = readToken();
      const data = await api.saveProfile(payload);
      if (readToken() !== current) throw new Error('账号会话已变化，请刷新后重试');
      saveToken(data.accessToken);
      token.value = data.accessToken;
      user.value = data.user;
      loadedToken.value = data.accessToken;
      return data.user;
    }

    return {
      token,
      user,
      authenticated,
      clear,
      syncFromStorage,
      signIn,
      ensureUser,
      signOut,
      updateProfile,
    };
  },
);

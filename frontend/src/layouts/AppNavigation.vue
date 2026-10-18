<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { modules, canVisit } from '@/router/modules'
import AppIcon from '@/components/AppIcon.vue'
const props = defineProps({ compact: Boolean })
const emit = defineEmits(['navigate'])
const route = useRoute()
const auth = useAuthStore()
const groups = computed(() => {
  const items = modules.filter((item) => canVisit(item, auth.user?.roleCode))
  return [...new Set(items.map((item) => item.group))].map((title) => ({ title, items: items.filter((item) => item.group === title) }))
})
</script>
<template>
  <nav aria-label="主导航">
    <el-menu :default-active="route.path" :collapse="props.compact" :collapse-transition="false" router class="sidebar-menu" @select="emit('navigate')">
      <template v-for="group in groups" :key="group.title">
        <div v-if="!compact" class="nav-group">{{ group.title }}</div>
        <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path"><AppIcon :name="item.icon" /><template #title><span>{{ item.title }}</span><span v-if="!item.ready" class="pending-dot" aria-label="待开发" /></template></el-menu-item>
      </template>
    </el-menu>
  </nav>
</template>
<style scoped>
.sidebar-menu { --el-menu-bg-color: transparent; --el-menu-text-color: #c0cde0; --el-menu-hover-bg-color: #223b5c; --el-menu-active-color: #fff; border: 0; width: 100%; }
.nav-group { font-size: 10px; color: #9aacc6; padding: 21px 23px 8px; letter-spacing: 1.5px; }.nav-group:first-child { padding-top: 10px; }
.el-menu-item { height: 44px; line-height: 44px; margin: 3px 11px; border-radius: 6px; padding: 0 15px !important; font-size: 13px; }
.el-menu-item.is-active { background: #245ee5; }.el-menu-item .el-icon { font-size: 18px; margin-right: 11px; }
.pending-dot { width: 4px; height: 4px; border-radius: 50%; background: #9eb0c9; margin-left: auto; }.is-active .pending-dot { background: white; }
:deep(.el-menu-tooltip__trigger) { padding: 0 15px !important; }
</style>

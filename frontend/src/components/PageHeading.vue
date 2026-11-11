<!-- 文件作用：展示业务图标、中文分类、页面标题及操作区域。 -->
<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppIcon from './AppIcon.vue';
import { pagePresentation } from '@/utils/page-presentation';
defineProps({ title: String, description: String, eyebrow: String });
const route = useRoute();
const presentation = computed(() => pagePresentation(route.path));
</script>
<template>
  <div class="page-heading business-heading" :style="{ '--page-accent': presentation.color, '--page-soft': presentation.soft }">
    <div class="heading-content">
      <span class="business-icon" aria-hidden="true"><AppIcon :name="presentation.icon" /></span>
      <div class="heading-copy">
        <span class="business-label">{{ presentation.label }}</span>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.default" class="page-actions"><slot /></div>
  </div>
</template>
<style scoped>
.business-heading { padding:20px 22px; border:1px solid var(--crm-border); border-left:4px solid var(--page-accent); border-radius:10px; background:var(--page-soft); align-items:center; }
.heading-content { display:flex; gap:16px; min-width:0; align-items:flex-start; }
.business-icon { display:grid; place-items:center; width:46px; height:46px; flex-shrink:0; border-radius:12px; background:white; color:var(--page-accent); font-size:24px; }
.heading-copy { min-width:0; }
.business-label { display:block; font-size:12px; font-weight:600; color:var(--page-accent); margin-bottom:3px; letter-spacing:1px; }
.business-heading h1 { font-size:25px; margin-bottom:5px; }
.business-heading p { font-size:13px; line-height:1.7; }
.page-actions { flex-shrink:0; justify-content:flex-end; max-width:46%; }
@media(max-width:1100px){.business-heading{align-items:flex-start;flex-wrap:wrap}.page-actions{max-width:100%;justify-content:flex-start;}}
@media(max-width:600px){.business-heading{padding:16px 14px;width:100%}.heading-content{gap:10px}.business-icon{width:36px;height:36px;font-size:20px;border-radius:9px}.business-heading h1{font-size:22px}.page-actions{width:100%;}}
</style>

/* 文件作用：客户与商机详情共用窄屏导航切换及监听器清理。 */
import { onMounted, onBeforeUnmount, ref } from 'vue';

/** 桌面使用左侧导航，700px及以下改为顶部导航。 */
export function useDetailViewport() {
  const narrowDetail = ref(false);
  let media;
  const update = () => { narrowDetail.value = media.matches; };
  onMounted(() => {
    media = window.matchMedia('(max-width: 700px)');
    update();
    media.addEventListener('change', update);
  });
  onBeforeUnmount(() => media?.removeEventListener('change', update));
  return narrowDetail;
}

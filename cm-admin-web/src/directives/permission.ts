import type { Directive } from 'vue';
import { useAppStore } from '@/stores/app';

/** 按钮级权限：无权限操作默认隐藏，与后端权限码使用同一套判断。 */
export const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const appStore = useAppStore();
    if (!appStore.hasPermission(binding.value)) {
      el.style.display = 'none';
    }
  },
};

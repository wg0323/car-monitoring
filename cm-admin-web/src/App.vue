<template>
  <!-- 会话与权限由路由守卫在首次导航时装载（stores/app.ts 的 loadSession），这里不再写死占位权限。 -->
  <RouterView />
</template>

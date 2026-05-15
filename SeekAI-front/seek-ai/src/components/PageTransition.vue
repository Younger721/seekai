<template>
  <router-view v-slot="{ Component, route }">
    <transition
      :name="transitionName"
      mode="out-in"
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const transitionName = ref('slide-left')

// 记录路由历史，判断是前进还是后退
const historyStack = ref([])

watch(() => router.currentRoute.value, (to, from) => {
  const toIndex = to.meta.index || 0
  const fromIndex = from.meta.index || 0

  if (toIndex > fromIndex) {
    // 进入子页面 - 右进
    transitionName.value = 'slide-left'
  } else if (toIndex < fromIndex) {
    // 返回 - 左进
    transitionName.value = 'slide-right'
  } else {
    // 同级页面 - 淡入淡出
    transitionName.value = 'fade'
  }
})

// 动效钩子
const onBeforeEnter = (el) => {
  el.style.opacity = '0'
  el.style.transform = 'translateX(30px)'
}

const onEnter = (el, done) => {
  el.style.transition = 'all 0.35s cubic-bezier(0.4, 0, 0.2, 1)'
  el.style.opacity = '1'
  el.style.transform = 'translateX(0)'
  setTimeout(done, 350)
}

const onLeave = (el, done) => {
  el.style.transition = 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)'
  el.style.opacity = '0'
  el.style.transform = 'translateX(-20px)'
  setTimeout(done, 250)
}
</script>

<style>
/* 右进左出 - 进入子页面 */
.slide-left-enter-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-left-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-left-enter-from {
  opacity: 0;
  transform: translateX(30px);
}
.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 左进右出 - 返回 */
.slide-right-enter-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}
.slide-right-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

/* 淡入淡出 - 同级页面 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
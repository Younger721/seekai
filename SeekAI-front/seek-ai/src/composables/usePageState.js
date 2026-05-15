import { onMounted, onUnmounted } from 'vue'

/**
 * 页面状态保持 composable
 * - 保存/恢复滚动位置
 * - 保存/恢复表单数据
 */
export function usePageState(key) {
  const storageKey = `seekai_page_state_${key}`

  // 保存页面状态
  const saveState = (state) => {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(state))
    } catch (e) {
      console.warn('Failed to save page state:', e)
    }
  }

  // 恢复页面状态
  const restoreState = () => {
    try {
      const saved = sessionStorage.getItem(storageKey)
      return saved ? JSON.parse(saved) : null
    } catch (e) {
      console.warn('Failed to restore page state:', e)
      return null
    }
  }

  // 清除页面状态
  const clearState = () => {
    try {
      sessionStorage.removeItem(storageKey)
    } catch (e) {
      console.warn('Failed to clear page state:', e)
    }
  }

  // 滚动位置保持
  const saveScrollPosition = (scrollTop) => {
    const state = restoreState() || {}
    state.scrollTop = scrollTop
    saveState(state)
  }

  // 恢复滚动位置
  const restoreScrollPosition = () => {
    const state = restoreState()
    return state?.scrollTop || 0
  }

  // 自动保持滚动位置的 hook
  const keepScrollPosition = (scrollContainerRef) => {
    onMounted(() => {
      if (scrollContainerRef?.value) {
        // 恢复到之前的位置
        const savedPosition = restoreScrollPosition()
        if (savedPosition > 0) {
          scrollContainerRef.value.scrollTop = savedPosition
        }

        // 监听滚动事件保存位置
        const handleScroll = () => {
          if (scrollContainerRef?.value) {
            saveScrollPosition(scrollContainerRef.value.scrollTop)
          }
        }

        scrollContainerRef.value.addEventListener('scroll', handleScroll, { passive: true })

        // 在组件卸载时不需要特殊处理，因为已经通过 sessionStorage 持久化了
      }
    })
  }

  return {
    saveState,
    restoreState,
    clearState,
    saveScrollPosition,
    restoreScrollPosition,
    keepScrollPosition
  }
}

export default usePageState
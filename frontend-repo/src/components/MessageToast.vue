<template>
  <transition name="toast-fade">
    <div 
      v-if="visible" 
      class="message-toast"
      :class="type"
    >
      <div class="toast-content">
        <!-- <div class="toast-icon">
          <svg v-if="type === 'success'" viewBox="0 0 24 24" fill="currentColor">
            <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
          </svg>
          <svg v-else-if="type === 'error'" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
          </svg>
        </div> -->
        <div class="toast-message">
          <div class="toast-title">{{ title }}</div>
          <div class="toast-description" v-if="description">{{ description }}</div>
        </div>
        <div class="toast-actions">
          <button 
            v-if="showUndo" 
            @click="handleUndo" 
            class="undo-btn"
            title="撤回操作"
          >
            撤回
          </button>
          <button 
            @click="handleClose" 
            class="close-btn"
            title="关闭"
          >
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
          </button>
        </div>
      </div>
      <!-- <div class="toast-progress" v-if="autoClose && duration > 0">
        <div class="progress-bar" :style="{ width: progressWidth + '%' }"></div>
      </div> -->
    </div>
  </transition>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'

// Props
const props = defineProps({
  // 消息类型：success, error, warning, info
  type: {
    type: String,
    default: 'info',
    validator: value => ['success', 'error', 'warning', 'info'].includes(value)
  },
  // 标题
  title: {
    type: String,
    required: true
  },
  // 描述信息
  description: {
    type: String,
    default: ''
  },
  // 是否显示撤回按钮
  showUndo: {
    type: Boolean,
    default: false
  },
  // 是否自动关闭
  autoClose: {
    type: Boolean,
    default: true
  },
  // 自动关闭的延迟时间（毫秒）
  duration: {
    type: Number,
    default: 3000
  },
  // 是否可见
  visible: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['close', 'undo'])

// Reactive data
const progressWidth = ref(100)
let progressTimer = null
let closeTimer = null

// Methods
const startProgress = () => {
  if (!props.autoClose || props.duration <= 0) return
  
  progressWidth.value = 100
  const step = 100 / (props.duration / 16) // 16ms per frame for 60fps
  
  progressTimer = setInterval(() => {
    progressWidth.value -= step
    if (progressWidth.value <= 0) {
      handleClose()
    }
  }, 16)
  
  closeTimer = setTimeout(() => {
    handleClose()
  }, props.duration)
}

const stopProgress = () => {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
}

const handleClose = () => {
  stopProgress()
  emit('close')
}

const handleUndo = () => {
  emit('undo')
  handleClose()
}

// Watch
watch(() => props.visible, (newVal) => {
  if (newVal) {
    startProgress()
  } else {
    stopProgress()
  }
})

// Lifecycle
onBeforeUnmount(() => {
  stopProgress()
})
</script>

<style scoped>
.message-toast {
  position: fixed;
  top: 40px;
  right: 30px;
  z-index: 9999;
  min-width: 300px;
  max-width: 400px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border-left: 4px solid;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.message-toast.success {
  border-left-color: #52c41a;
}

.message-toast.error {
  border-left-color: #ff4d4f;
}

.message-toast.warning {
  border-left-color: #faad14;
}

.message-toast.info {
  border-left-color: #1890ff;
}

.toast-content {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  gap: 12px;
}

.toast-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 2px;
}

.toast-icon svg {
  width: 100%;
  height: 100%;
}

.message-toast.success .toast-icon {
  color: #52c41a;
}

.message-toast.error .toast-icon {
  color: #ff4d4f;
}

.message-toast.warning .toast-icon {
  color: #faad14;
}

.message-toast.info .toast-icon {
  color: #1890ff;
}

.toast-message {
  flex: 1;
  min-width: 0;
}

.toast-title {
  font-weight: 500;
  font-size: 14px;
  line-height: 1.4;
  color: #262626;
  margin-bottom: 4px;
}

.toast-description {
  font-size: 13px;
  line-height: 1.4;
  color: #8c8c8c;
}

.toast-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.undo-btn {
  padding: 4px 8px;
  font-size: 12px;
  color: #1890ff;
  background: transparent;
  border: 1px solid #1890ff;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.undo-btn:hover {
  background: #1890ff;
  color: white;
}

.close-btn {
  padding: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 4px;
  color: #8c8c8c;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #262626;
}

.close-btn svg {
  width: 16px;
  height: 16px;
}

.toast-progress {
  height: 2px;
  background: #f0f0f0;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #1890ff, #40a9ff);
  transition: width 0.016s linear;
}

/* 动画效果 */
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.3s ease;
}

.toast-fade-enter {
  opacity: 0;
  transform: translateX(100%);
}

.toast-fade-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message-toast {
    top: 10px;
    right: 10px;
    left: 10px;
    min-width: auto;
    max-width: none;
  }
  
  .toast-content {
    padding: 12px;
  }
  
  .toast-title {
    font-size: 13px;
  }
  
  .toast-description {
    font-size: 12px;
  }
}
</style>

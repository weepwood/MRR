<template>
  <div class="demo-container">
    <h1>MessageToast 组件演示</h1>
    
    <div class="demo-buttons">
      <button @click="showSuccessToast" class="demo-btn success">成功提示</button>
      <button @click="showErrorToast" class="demo-btn error">错误提示</button>
      <button @click="showWarningToast" class="demo-btn warning">警告提示</button>
      <button @click="showInfoToast" class="demo-btn info">信息提示</button>
      <button @click="showUndoToast" class="demo-btn undo">带撤回的提示</button>
    </div>
    
    <MessageToast
      :visible="toastVisible"
      :type="toastType"
      :title="toastTitle"
      :description="toastDescription"
      :show-undo="toastShowUndo"
      @close="hideToast"
      @undo="handleUndo"
    />
  </div>
</template>

<script>
import MessageToast from './MessageToast.vue'

export default {
  name: 'MessageToastDemo',
  components: {
    MessageToast
  },
  data() {
    return {
      toastVisible: false,
      toastType: 'success',
      toastTitle: '',
      toastDescription: '',
      toastShowUndo: false
    }
  },
  methods: {
    showSuccessToast() {
      this.toastType = 'success'
      this.toastTitle = '操作成功'
      this.toastDescription = '图片类型切换成功，已切换到病案首页'
      this.toastShowUndo = false
      this.toastVisible = true
    },
    
    showErrorToast() {
      this.toastType = 'error'
      this.toastTitle = '操作失败'
      this.toastDescription = '切换过程中发生错误，请重试'
      this.toastShowUndo = false
      this.toastVisible = true
    },
    
    showWarningToast() {
      this.toastType = 'warning'
      this.toastTitle = '注意事项'
      this.toastDescription = '切换类型后，当前图片索引将重置为0'
      this.toastShowUndo = false
      this.toastVisible = true
    },
    
    showInfoToast() {
      this.toastType = 'info'
      this.toastTitle = '提示信息'
      this.toastDescription = '您可以选择不同的病案类型进行筛选'
      this.toastShowUndo = false
      this.toastVisible = true
    },
    
    showUndoToast() {
      this.toastType = 'success'
      this.toastTitle = '类型切换成功'
      this.toastDescription = '已切换到病程记录，您可以撤回此操作'
      this.toastShowUndo = true
      this.toastVisible = true
    },
    
    hideToast() {
      this.toastVisible = false
    },
    
    handleUndo() {
      // 这里可以添加撤回逻辑
      this.hideToast()
    }
  }
}
</script>

<style scoped>
.demo-container {
  padding: 40px;
  max-width: 800px;
  margin: 0 auto;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

h1 {
  text-align: center;
  color: #333;
  margin-bottom: 40px;
}

.demo-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
}

.demo-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 120px;
}

.demo-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.demo-btn.success {
  background: #52c41a;
  color: white;
}

.demo-btn.success:hover {
  background: #389e0d;
}

.demo-btn.error {
  background: #ff4d4f;
  color: white;
}

.demo-btn.error:hover {
  background: #cf1322;
}

.demo-btn.warning {
  background: #faad14;
  color: white;
}

.demo-btn.warning:hover {
  background: #d48806;
}

.demo-btn.info {
  background: #1890ff;
  color: white;
}

.demo-btn.info:hover {
  background: #096dd9;
}

.demo-btn.undo {
  background: #722ed1;
  color: white;
}

.demo-btn.undo:hover {
  background: #531dab;
}

@media (max-width: 768px) {
  .demo-container {
    padding: 20px;
  }
  
  .demo-buttons {
    flex-direction: column;
    align-items: center;
  }
  
  .demo-btn {
    width: 100%;
    max-width: 200px;
  }
}
</style>

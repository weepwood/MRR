<template>
  <div class="admin-page">
    <div class="content-wrapper">
      <!-- 页面标题 -->
      <!-- <div class="page-header">
        <h1 class="page-title">后台管理</h1>
        <p class="page-subtitle">欢迎使用病案管理系统</p>
      </div> -->

      <!-- 欢迎提示卡片 -->
      <el-card class="welcome-card" shadow="hover">
        <div class="welcome-content">
          <div class="welcome-icon">
            <el-icon><Operation /></el-icon>
          </div>
          <div class="welcome-text">
            <h3>病案翻拍后台管理</h3>
            <p>您已成功登录到后台管理系统，可以开始管理各项功能</p>
          </div>
        </div>
      </el-card>

      <!-- 功能卡片网格 -->
      <div class="features-grid">
        <el-card class="feature-card" shadow="hover" @click="goToDashboard">
          <div class="feature-icon dashboard">
            <el-icon><DataBoard /></el-icon>
          </div>
          <h3 class="feature-title">管理面板</h3>
          <p class="feature-description">访问完整的后台管理界面，包含仪表板、用户管理、系统监控等功能</p>
          <!-- <div class="feature-action">
            <el-button type="primary" class="action-btn">进入面板 →</el-button>
          </div> -->
        </el-card>
        
        <el-card class="feature-card" shadow="hover" @click="goToStatistics">
          <div class="feature-icon statistics">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <h3 class="feature-title">病案统计</h3>
          <p class="feature-description">查看病案数据统计与分析图表</p>
          <!-- <div class="feature-action">
            <el-button class="action-btn">开发中...</el-button>
          </div> -->
        </el-card>
        
        <el-card class="feature-card" shadow="hover">
          <div class="feature-icon user">
            <el-icon><User /></el-icon>
          </div>
          <h3 class="feature-title">用户管理</h3>
          <p class="feature-description">管理系统用户权限和信息</p>
          <!-- <div class="feature-action">
            <el-button class="action-btn">开发中...</el-button>
          </div> -->
        </el-card>
        
        <el-card class="feature-card" shadow="hover">
          <div class="feature-icon record">
            <el-icon><Document /></el-icon>
          </div>
          <h3 class="feature-title">病案管理</h3>
          <p class="feature-description">查看和管理所有病案信息</p>
          <!-- <div class="feature-action">
            <el-button class="action-btn">开发中...</el-button>
          </div> -->
        </el-card>
        
        <el-card class="feature-card" shadow="hover">
          <div class="feature-icon setting">
            <el-icon><Setting /></el-icon>
          </div>
          <h3 class="feature-title">系统设置</h3>
          <p class="feature-description">配置系统参数和功能</p>
          <!-- <div class="feature-action">
            <el-button class="action-btn">开发中...</el-button>
          </div> -->
        </el-card>
      </div>
      
      <!-- 退出登录按钮 -->
      <div class="logout-section">
        <el-button type="danger" @click="handleLogout" class="logout-btn">
          <el-icon><SwitchButton /></el-icon> &nbsp;&nbsp;退出登录        
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
// 导入需要使用的图标组件
import { User, Document, Setting, SwitchButton, DataBoard, TrendCharts } from '@element-plus/icons-vue'

const router = useRouter()

const goToDashboard = () => {
  router.push('/admin-dashboard')
}

const goToStatistics = () => {
  router.push('/admin/statistics')
}

const handleLogout = () => {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '确认退出',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      // 清除本地存储的 token
      localStorage.removeItem('token')
      // 跳转到登录页
      router.push('/login')
      ElMessage.success('已退出登录')
    })
    .catch(() => {
      // 用户取消操作
    })
}
</script>

<style scoped>
/* 页面整体布局 */
.admin-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 40px 20px;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
}

/* 页面标题 */
.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0 0 8px 0;
  letter-spacing: -0.02em;
}

.page-subtitle {
  font-size: 17px;
  color: #86868b;
  margin: 0;
  font-weight: 400;
}

/* 欢迎卡片 */
.welcome-card {
  border-radius: 18px;
  background: #ffffff;
  margin-bottom: 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.welcome-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.welcome-content {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 24px;
}

.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(94, 193, 230, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #184bc0;
  font-size: 32px;
  flex-shrink: 0;
}

.welcome-text h3 {
  font-size: 21px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0 0 8px 0;
  letter-spacing: -0.02em;
}

.welcome-text p {
  font-size: 15px;
  color: #86868b;
  margin: 0;
  line-height: 1.5;
}

/* 功能卡片网格 */
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 40px;
}

.feature-card {
  border-radius: 18px;
  background: #ffffff;
  padding: 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.feature-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-4px);
}

.feature-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: 18px 18px 0 0;
}

.feature-card.dashboard::before {
  background: linear-gradient(90deg, #667eea, #764ba2);
}

.feature-card.user::before {
  background: linear-gradient(90deg, #f093fb, #f5576c);
}

.feature-card.statistics::before {
  background: linear-gradient(90deg, #34c759, #28a745);
}

.feature-card.record::before {
  background: linear-gradient(90deg, #4facfe, #00f2fe);
}

.feature-card.setting::before {
  background: linear-gradient(90deg, #ff9a9e, #fecfef);
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.feature-icon.dashboard {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.feature-icon.user {
  background: rgba(240, 147, 251, 0.1);
  color: #f093fb;
}

.feature-icon.statistics {
  background: rgba(52, 199, 89, 0.1);
  color: #34c759;
}

.feature-icon.record {
  background: rgba(79, 172, 254, 0.1);
  color: #4facfe;
}

.feature-icon.setting {
  background: rgba(255, 154, 158, 0.1);
  color: #ff9a9e;
}

.feature-title {
  font-size: 21px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0 0 12px 0;
  letter-spacing: -0.02em;
}

.feature-description {
  font-size: 15px;
  color: #86868b;
  line-height: 1.5;
  margin: 0 0 24px 0;
}

.feature-action {
  display: flex;
}

.action-btn {
  border-radius: 980px;
  padding: 12px 24px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateX(4px);
}

/* 退出登录区域 */
.logout-section {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.logout-btn {
  border-radius: 980px;
  padding: 14px 32px;
  font-size: 15px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.logout-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 59, 48, 0.3);
}

.logout-btn :deep(.el-icon) {
  font-size: 18px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-page {
    padding: 20px;
  }
  
  .page-title {
    font-size: 36px;
  }
  
  .page-subtitle {
    font-size: 15px;
  }
  
  .welcome-content {
    flex-direction: column;
    text-align: center;
  }
  
  .welcome-icon {
    width: 56px;
    height: 56px;
    font-size: 28px;
  }
  
  .welcome-text h3 {
    font-size: 19px;
  }
  
  .welcome-text p {
    font-size: 14px;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .feature-card {
    padding: 24px;
  }
  
  .feature-title {
    font-size: 19px;
  }
  
  .feature-description {
    font-size: 14px;
  }
}
</style>
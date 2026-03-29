<template>
  <div class="print-page">
    <!-- 页面头部 -->
    <header class="print-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">
            <el-icon class="title-icon"><Document /></el-icon>
            病案打印
          </h1>
          <div class="record-info" v-if="recordInfo">
            <div class="info-item">
              <span class="info-label">病案号:</span>
              <span class="info-value">{{ recordInfo.bah }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">患者:</span>
              <span class="info-value">{{ recordInfo.name }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">科室:</span>
              <span class="info-value">{{ recordInfo.department }}</span>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <el-button class="action-btn back-btn" @click="goBack" :icon="ArrowLeft">
            返回
          </el-button>
          <el-button class="action-btn print-btn" type="primary" @click="handlePrint" :disabled="isPrinting" :loading="isPrinting" :icon="Printer">
            {{ isPrinting ? '打印中...' : '打印' }}
          </el-button>
          <!-- <el-button class="action-btn download-btn" @click="downloadPDF" :disabled="isGeneratingPDF" :loading="isGeneratingPDF" :icon="Download">
            {{ isGeneratingPDF ? '生成中...' : '下载PDF' }}
          </el-button> -->
        </div>
      </div>
    </header>

    <!-- 主要内容区域 -->
    <main class="main-content">
      <!-- 左侧设置面板 -->
      <aside class="settings-panel">
        <div class="panel-header">
          <h3 class="panel-title">
            <el-icon><Setting /></el-icon>
            打印设置
          </h3>
        </div>
        
        <div class="settings-content">
          <!-- 页面设置 -->
          <div class="setting-group">
            <h4 class="group-title">页面设置</h4>
            <div class="setting-item">
              <label class="setting-label">纸张大小</label>
              <el-select v-model="printSettings.paperSize" class="setting-input">
                <el-option label="A4" value="A4" />
                <!-- <el-option label="A3" value="A3" />
                <el-option label="Letter" value="Letter" /> -->
              </el-select>
            </div>
            <!-- <div class="setting-item">
              <label class="setting-label">页面方向</label>
              <el-radio-group v-model="printSettings.orientation" class="setting-input">
                <el-radio label="portrait">纵向</el-radio>
                <el-radio label="landscape">横向</el-radio>
              </el-radio-group>
            </div> -->
            <!-- <div class="setting-item">
              <label class="setting-label">每页图片数</label>
              <el-select v-model="printSettings.imagesPerPage" class="setting-input">
                <el-option label="1张" :value="1" />
                <el-option label="2张" :value="2" />
                <el-option label="4张" :value="4" />
              </el-select>
            </div> -->
            <div class="setting-item">
              <label class="setting-label">页边距 (mm)</label>
              <el-input-number 
                v-model="printSettings.pageMargin" 
                :min="0" 
                :max="20" 
                :step="1"
                controls-position="right" 
                class="setting-input"
              />
            </div>
          </div>

          <!-- 显示设置 -->
          <div class="setting-group">
            <h4 class="group-title">显示设置</h4>
            <!-- <div class="setting-item">
              <el-checkbox v-model="printSettings.showImageInfo" class="setting-checkbox">
                显示图片信息
              </el-checkbox>
            </div> -->
            <div class="setting-item">
              <el-checkbox v-model="printSettings.showPageNumbers" class="setting-checkbox">
                显示页码
              </el-checkbox>
              <el-checkbox v-model="printSettings.autoRotateLandscape" class="setting-checkbox">
                自动旋转横放图片
              </el-checkbox>
            </div>

          </div>

          <!-- 图片旋转设置 -->
          <div class="setting-group">

            <div class="setting-item">
              <label class="setting-label">手动旋转角度</label>
              <el-select v-model="printSettings.rotationAngle" class="setting-input">
                <el-option label="0° (不旋转)" :value="0" />
                <el-option label="90° (顺时针)" :value="90" />
                <el-option label="180° (倒置)" :value="180" />
                <el-option label="270° (逆时针)" :value="270" />
              </el-select>
            </div>
          </div>

          <!-- 预览信息 -->
          <div class="preview-stats">
            <div class="stat-item">
              <span class="stat-label">总图片数:</span>
              <span class="stat-value">{{ selectedImages.length }}</span>
            </div>
            <!-- <div class="stat-item">
              <span class="stat-label">预计页数:</span>
              <span class="stat-value">{{ Math.ceil(selectedImages.length / printSettings.imagesPerPage) }}</span>
            </div> -->
          </div>
        </div>
      </aside>

      <!-- 右侧预览区域 -->
      <section class="preview-section">
        <div class="preview-header">
          <div class="preview-title">
            <el-icon><View /></el-icon>
            预览
          </div>
        </div>

        <div class="preview-content" ref="printPreview">
          <!-- 图片网格 -->
          <div class="images-grid" :class="`grid-${printSettings.imagesPerPage}`">
            <div 
              v-for="(img, index) in selectedImages" 
              :key="img.id || index" 
              class="print-image-item"
              :class="{ 'loading': !img.loaded }"
            >
              <div class="image-container" :class="getImageRotationClass(img)">
                <el-image 
                  :src="img.blobUrl || img.cx" 
                  class="print-image" 
                  :class="getImageRotationClass(img)"
                  :style="{ transform: `rotate(${getImageRotation(img)}deg)` }"
                  fit="contain" 
                  :loading="'eager'"
                  @load="onImageLoad(img, index)" 
                  @error="onImageError(img, index)"
                />
                <div v-if="!img.loaded" class="image-loading">
                  <div class="loading-spinner"></div>
                  <span>加载中...</span>
                </div>
                <!-- 显示旋转信息 -->
                <div v-if="img.orientation && printSettings.showImageInfo" class="rotation-info">
                  <span v-if="img.orientation.isLandscape" class="landscape-indicator">横放</span>
                  <span v-if="getImageRotation(img) > 0" class="rotation-indicator">
                    {{ getImageRotation(img) }}°
                  </span>
                </div>
              </div>
              <div v-if="printSettings.showImageInfo" class="image-info">
                <!-- <div class="info-row">
                  <span class="page-num">P{{ img.pages }}</span>
                  <span class="type-name">{{ getTypeName(img.btype) }}</span>
                </div> -->
                <div v-if="printSettings.showPageNumbers" class="page-number">
                  {{ index + 1 }} / {{ selectedImages.length }}
                </div>
              </div>
            </div>
          </div>
          
          <!-- 打印专用页面结构 - 默认隐藏，打印时显示 -->
          <div class="print-pages">
            <div 
              v-for="(img, index) in selectedImages" 
              :key="`print-${img.id || index}`" 
              class="print-page"
            >
              <div class="image-container" :class="getImageRotationClass(img)">
                <img 
                  :src="img.blobUrl || img.cx" 
                  class="print-image"
                  :style="getPrintImageStyle(img)"
                  :alt="`图片 P${img.pages}`"
                />
                <div v-if="printSettings.showPageNumbers" class="image-info">
                  <div class="page-number">第 {{ index + 1 }} 页 / 共 {{ selectedImages.length }} 页</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- 打印状态遮罩 -->
    <div v-if="isPrinting" class="print-status">
      <div class="status-content">
        <div class="status-spinner"></div>
        <p>正在准备打印...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { getTypeName } from '../utils/printUtils'

// 响应式数据
const selectedImages = ref([])
const recordInfo = ref(null)
const isPrinting = ref(false)
const isGeneratingPDF = ref(false)
const printPreview = ref(null)

// 计算每张图片的旋转角度
const getImageRotation = (img) => {
  let rotation = printSettings.value.rotationAngle
  
  // 如果启用自动旋转且图片是横放的，则旋转90度
  if (printSettings.value.autoRotateLandscape && img.orientation?.isLandscape) {
    rotation = (rotation + 90) % 360
  }
  
  return rotation
}

// 获取图片旋转的CSS类名
const getImageRotationClass = (img) => {
  const rotation = getImageRotation(img)
  
  if (rotation === 0) {
    return ''
  }
  
  let classes = ['rotated']
  
  if (rotation === 90 || rotation === 270) {
    classes.push(`rotated-${rotation}`)
  } else if (rotation === 180) {
    classes.push('rotated-180')
  }
  
  return classes
}

// 获取打印时的图片样式
const getPrintImageStyle = (img) => {
  const imageSize = calculateImageSize(img)
  const rotation = getImageRotation(img)
  
  return {
    width: `${imageSize.width}mm`,
    height: `${imageSize.height}mm`,
    transform: `rotate(${rotation}deg)`,
    transformOrigin: 'center'
  }
}

// 打印设置
const printSettings = ref({
  paperSize: 'A4',
  // orientation: 'portrait',
  imagesPerPage: 1,
  pageMargin: 3,
  showPageNumbers: false,
  showImageInfo: true,
  autoRotateLandscape: true, // 自动旋转横放图片
  rotationAngle: 0 // 手动旋转角度
})

// 加载图片数据
const loadPrintData = () => {
  try {
    const imagesData = sessionStorage.getItem('selectedImagesForPrint')
    const bahData = sessionStorage.getItem('printBah')
    const recordData = sessionStorage.getItem('printRecord')
    
    
    if (imagesData) {
      const parsedImages = JSON.parse(imagesData)
      
      selectedImages.value = parsedImages.map(img => ({
        ...img,
        loaded: false
      }))
      
    }
    
    if (recordData) {
      recordInfo.value = JSON.parse(recordData)
    }
    
    if (!selectedImages.value.length) {
      ElMessage.error('没有找到要打印的图片')
      goBack()
    } else {
    }
  } catch (error) {
    console.error('加载打印数据失败:', error)
    ElMessage.error('加载打印数据失败')
    goBack()
  }
}

// 图片方向检测
const detectImageOrientation = (img) => {
  return new Promise((resolve) => {
    const image = new Image()
    image.onload = () => {
      const isLandscape = image.naturalWidth > image.naturalHeight
      resolve({
        isLandscape,
        width: image.naturalWidth,
        height: image.naturalHeight,
        aspectRatio: image.naturalWidth / image.naturalHeight
      })
    }
    image.onerror = () => {
      resolve({
        isLandscape: false,
        width: 0,
        height: 0,
        aspectRatio: 1
      })
    }
    image.src = img.blobUrl || img.cx
  })
}

// 图片加载事件
const onImageLoad = async (img, index) => {
  img.loaded = true
  
  // 检测图片方向
  const orientation = await detectImageOrientation(img)
  img.orientation = orientation
  
}

const onImageError = (img, index) => {
  img.loaded = true
  console.error(`图片 P${img.pages} 加载失败:`, img.blobUrl || img.cx)
  ElMessage.warning(`图片 P${img.pages} 加载失败`)
}

// 返回上一页
const goBack = () => {
  window.close()
}

// 计算A4纸张的有效打印区域
const getA4PrintArea = () => {
  const margin = printSettings.value.pageMargin || 5 // mm
  const a4Width = 210 // A4宽度 mm
  const a4Height = 297 // A4高度 mm
  
  return {
    width: a4Width - (margin * 2),
    height: a4Height - (margin * 2),
    margin
  }
}

// 计算图片在A4纸上的最佳尺寸，保持宽高比
const calculateImageSize = (img) => {
  const printArea = getA4PrintArea()
  const rotation = getImageRotation(img)
  
  // 获取图片的原始尺寸
  let imageWidth = img.orientation?.width || 1
  let imageHeight = img.orientation?.height || 1
  
  // 如果图片被旋转90度或270度，交换宽高
  if (rotation === 90 || rotation === 270) {
    [imageWidth, imageHeight] = [imageHeight, imageWidth]
  }
  
  const imageAspectRatio = imageWidth / imageHeight
  const printAspectRatio = printArea.width / printArea.height
  
  let finalWidth, finalHeight
  
  if (imageAspectRatio > printAspectRatio) {
    // 图片更宽，以宽度为准
    finalWidth = printArea.width
    finalHeight = printArea.width / imageAspectRatio
  } else {
    // 图片更高，以高度为准
    finalHeight = printArea.height
    finalWidth = printArea.height * imageAspectRatio
  }
  
  return {
    width: finalWidth,
    height: finalHeight,
    aspectRatio: imageAspectRatio,
    rotation
  }
}

// 打印窗口 - 重新设计为专注于A4纸张和正确宽高比
const previewPrint = () => {
  // 创建一个新的预览窗口
  const printWindow = window.open('', '_blank', 'width=1200,height=800')
  
  if (!printWindow) {
    ElMessage.error('无法打开打印窗口，请检查浏览器弹窗设置')
    return
  }
  
  const printDocument = printWindow.document
  
  // 使用更安全的方法初始化打印文档
  const initPrintDocument = () => {
    try {
      const printArea = getA4PrintArea()
      
      // 创建完整的HTML文档结构，专注于A4纸张和正确宽高比
      const htmlContent = `
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>病案打印 - ${recordInfo.value?.name || '患者'} (${recordInfo.value?.bah || '病案号'})</title>
          <style>
            @page {
              margin: ${printSettings.value.pageMargin}mm;
            }
            
            * {
              -webkit-print-color-adjust: exact !important;
              print-color-adjust: exact !important;
              box-sizing: border-box;
            }
            
            body {
              margin: 0;
              padding: 0;
              background: white;
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            }
            
            .print-page {
              width: 100%;
              height: 100vh;
              display: flex;
              flex-direction: column;
              justify-content: center;
              align-items: center;
              page-break-after: always;
              break-after: page;
              position: relative;
              background: white;
            }
            
            .print-page:last-child {
              page-break-after: avoid;
              break-after: auto;
            }
            
            .image-container {
              width: 100%;
              height: 100%;
              display: flex;
              align-items: center;
              justify-content: center;
              position: relative;
              padding: 10mm;
            }
            
            .print-image {
              object-fit: contain;
              object-position: center;
              transform-origin: center;
            }
            
            /* 旋转图片的特殊处理 */
            .print-image.rotated-90,
            .print-image.rotated-270 {
              /* 90度和270度旋转时，需要特殊处理容器尺寸 */
              width: 100vh !important;
              height: 100vh !important;
              /* 确保旋转后的图片能够完全显示在容器内 */
              object-fit: contain;
            }
            
            .print-image.rotated-0,
            .print-image.rotated-180 {
              /* 0度和180度旋转时，正常显示 */
              max-width: 100%;
              max-height: 100%;
              object-fit: contain;
            }
            
            /* 针对旋转图片的容器优化 */
            .image-container.rotated-90,
            .image-container.rotated-270 {
              /* 当包含90度或270度旋转的图片时，确保容器足够大 */
              overflow: visible;
            }
            
            .image-info {
              position: absolute;
              bottom: 5mm;
              left: 90%;
              transform: translateX(-50%);
              background: rgba(255, 255, 255, 0.9);
              padding: 2px 8px;
              border-radius: 4px;
              font-size: 12px;
              color: #666;
              border: 1px solid #ddd;
            }
            
            .page-number {
              text-align: center;
              font-weight: 500;
            }
            
            /* 确保图片在A4纸上正确显示 */
            @media print {
              .print-page {
                width: 100%;
                height: 100vh;
                margin: 0;
                padding: 0;
              }
              
              .image-container {
                width: calc(100% - ${printSettings.value.pageMargin * 2}mm);
                height: calc(100vh - ${printSettings.value.pageMargin * 2}mm);
                padding: 0;
                display: flex;
                align-items: center;
                justify-content: center;
              }
              
              /* 打印时旋转图片的特殊处理 */
              .print-image.rotated-90,
              .print-image.rotated-270 {
                /* 确保旋转后的图片能够完全显示 */
                max-width: none;
                max-height: none;
                object-fit: contain;
              }
              
              .print-image.rotated-0,
              .print-image.rotated-180 {
                max-width: 100%;
                max-height: 100%;
                object-fit: contain;
              }
            }
          </style>
        </head>
        <body>
          ${selectedImages.value.map((img, index) => {
            const imageSize = calculateImageSize(img);
            const rotation = getImageRotation(img);
            
            // 根据旋转角度确定CSS类名
            let rotationClass = '';
            if (rotation === 90) rotationClass = 'rotated-90';
            else if (rotation === 180) rotationClass = 'rotated-180';
            else if (rotation === 270) rotationClass = 'rotated-270';
            else rotationClass = 'rotated-0';
            
            // 计算旋转后的实际显示尺寸
            let displayWidth, displayHeight;
            
            if (rotation === 90 || rotation === 270) {
              // 90度和270度旋转时，使用计算出的精确尺寸
              displayWidth = `${imageSize.width}mm`;
              displayHeight = `${imageSize.height}mm`;
            } else {
              // 0度和180度旋转时，使用百分比
              displayWidth = '100%';
              displayHeight = '100%';
            }
            
            return `
              <div class="print-page">
                <div class="image-container">
                  <img 
                    src="${img.blobUrl || img.cx}" 
                    class="print-image ${rotationClass}"
                    style="transform: rotate(${rotation}deg); width: ${displayWidth}; height: ${displayHeight};"
                    alt="图片 P${img.pages || index + 1}"
                    onerror="console.error('图片 ${index + 1} 加载失败')"
                  />
                  ${printSettings.value.showPageNumbers ? `
                    <div class="image-info">
                      <div class="page-number"> ${index + 1} / ${selectedImages.value.length} </div>
                    </div>
                  ` : ''}
                </div>
              </div>
            `;
          }).join('')}
        </body>
        </html>
      `
      
      // 使用document.write写入内容
      printDocument.open()
      printDocument.write(htmlContent)
      printDocument.close()
      
      // 等待内容加载完成后打印
      printWindow.onload = () => {
        printWindow.focus()
        setTimeout(() => {
          printWindow.print()
        }, 1000) // 增加等待时间确保图片加载完成
      }
      
    } catch (error) {
      console.error('打印窗口初始化失败:', error)
      ElMessage.error('打印窗口初始化失败')
    }
  }
  
  // 确保窗口完全加载后再初始化
  if (printDocument.readyState === 'loading') {
    printWindow.onload = initPrintDocument
  } else {
    initPrintDocument()
  }
}

// 处理打印
const handlePrint = async () => {
  try {
    isPrinting.value = true
    
    // 检查是否有未加载的图片
    const unloadedImages = selectedImages.value.filter(img => !img.loaded)
    if (unloadedImages.length > 0) {
      try {
        await ElMessageBox.confirm(
          `还有 ${unloadedImages.length} 张图片未加载完成，是否继续打印？`,
          '提示',
          {
            confirmButtonText: '继续',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
      } catch (cancelError) {
        // 用户取消
        return
      }
    }
    
    // 使用打印窗口的逻辑
    previewPrint()
    
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('打印失败')
      console.error('打印错误:', error)
    }
  } finally {
    isPrinting.value = false
  }
}

// 下载PDF
const downloadPDF = async () => {
  try {
    isGeneratingPDF.value = true
    ElMessage.info('PDF生成功能开发中...')
    // TODO: 实现PDF生成功能
  } catch (error) {
    ElMessage.error('PDF生成失败')
  } finally {
    isGeneratingPDF.value = false
  }
}

// 组件挂载
onMounted(() => {
  loadPrintData()
})
</script>

<style scoped>
/* 主容器 - 使用现代布局 */
.print-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--gray-50, #f9fafb);
  overflow: hidden;
}

/* 页面头部 */
.print-header {
  background: var(--white, #ffffff);
  border-bottom: 1px solid var(--gray-200, #e5e7eb);
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(0, 0, 0, 0.1));
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.header-left {
  display: flex;
  flex-direction: row;
  gap: 8px;
  flex: 1;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary, #111827);
}

.title-icon {
  color: var(--primary-color, #667eea);
  font-size: 28px;
}

.record-info {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--gray-100, #f3f4f6);
  border-radius: 8px;
  font-size: 14px;
}

.info-label {
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
}

.info-value {
  color: var(--text-primary, #111827);
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 0.2s ease;
  border: none;
  cursor: pointer;
}

.back-btn {
  background: var(--gray-100, #f3f4f6);
  color: var(--text-primary, #374151);
}

.back-btn:hover {
  background: var(--gray-200, #e5e7eb);
  transform: translateY(-1px);
}

.print-btn {
  background: var(--primary-color, #667eea);
  color: white;
}

.print-btn:hover:not(:disabled) {
  background: var(--primary-dark, #4f46e5);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md, 0 4px 12px rgba(102, 126, 234, 0.4));
}

.download-btn {
  background: var(--white, #ffffff);
  color: var(--text-primary, #374151);
  border: 1px solid var(--gray-300, #d1d5db);
}

.download-btn:hover:not(:disabled) {
  background: var(--gray-50, #f9fafb);
  border-color: var(--gray-400, #9ca3af);
  transform: translateY(-1px);
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

/* 左侧设置面板 */
.settings-panel {
  background: var(--white, #ffffff);
  border-radius: 12px;
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(0, 0, 0, 0.1));
  border: 1px solid var(--gray-200, #e5e7eb);
  height: fit-content;
  position: sticky;
}

.panel-header {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--gray-200, #e5e7eb);
  background: var(--gray-50, #f9fafb);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #111827);
}

.settings-content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.setting-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #111827);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--gray-200, #e5e7eb);
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #374151);
}

.setting-input {
  width: 100%;
}

.setting-checkbox {
  width: 100%;
}

.preview-stats {
  background: var(--gray-50, #f9fafb);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid var(--gray-200, #e5e7eb);
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.stat-item:not(:last-child) {
  border-bottom: 1px solid var(--gray-200, #e5e7eb);
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
}

.stat-value {
  font-size: 16px;
  color: var(--primary-color, #667eea);
  font-weight: 700;
}

/* 右侧预览区域 */
.preview-section {
  background: var(--white, #ffffff);
  border-radius: 12px;
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(0, 0, 0, 0.1));
  border: 1px solid var(--gray-200, #e5e7eb);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.preview-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--gray-200, #e5e7eb);
  background: var(--gray-50, #f9fafb);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #111827);
}

.preview-actions {
  display: flex;
  gap: 12px;
}

.preview-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
}

/* 图片网格 */
.images-grid {
  display: grid;
  gap: 24px;
}

/* 打印专用页面结构 */
.print-pages {
  display: none; /* 默认隐藏 */
}

.print-pages .print-page {
  page-break-after: always;
  break-after: page;
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
}

.print-pages .print-page:last-child {
  page-break-after: avoid;
  break-after: auto;
}

.print-pages .image-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9f9f9;
  position: relative;
}

.print-pages .print-image {
  object-fit: contain;
  transform-origin: center;
}

.images-grid.grid-1 {
  grid-template-columns: 1fr;
}

.images-grid.grid-2 {
  grid-template-columns: repeat(2, 1fr);
}

.images-grid.grid-4 {
  grid-template-columns: repeat(2, 1fr);
}

.print-image-item {
  border: 1px solid var(--gray-200, #e5e7eb);
  border-radius: 12px;
  /* overflow: hidden; */
  background: var(--white, #ffffff);
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(0, 0, 0, 0.1));
}

.print-image-item:hover {
  box-shadow: var(--shadow-md, 0 4px 12px rgba(0, 0, 0, 0.15));
  transform: translateY(-2px);
}

.print-image-item.loading {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-container {
  position: relative;
  width: 100%;
  height: 100%;
  background: var(--gray-50, #f9fafb);
  display: flex;
  align-items: center;
  justify-content: center;
}

.print-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 8px;
  transition: transform 0.3s ease;
}

.print-image.rotated {
  transform-origin: center;
}

/* 针对不同旋转角度的预览样式 */
.print-image.rotated-90,
.print-image.rotated-270 {
  /* 90度和270度旋转时，图片的宽高比会互换，需要特殊处理尺寸 */
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.print-image.rotated-180 {
  /* 180度旋转时保持原有尺寸 */
  max-width: 100%;
  max-height: 100%;
}

.image-loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.95);
  color: var(--text-secondary, #6b7280);
  backdrop-filter: blur(4px);
}

.rotation-info {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  gap: 8px;
  z-index: 2;
}

.landscape-indicator {
  background: var(--warning-color, #f59e0b);
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
}

.rotation-indicator {
  background: var(--primary-color, #667eea);
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--gray-200, #e5e7eb);
  border-top: 3px solid var(--primary-color, #667eea);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.image-info {
  padding: 16px;
  background: var(--gray-50, #f9fafb);
  border-top: 1px solid var(--gray-200, #e5e7eb);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.page-num {
  font-weight: 700;
  color: var(--primary-color, #667eea);
  font-size: 16px;
}

.type-name {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
}

.page-number {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
}

/* 打印状态遮罩 */
.print-status {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay, rgba(0, 0, 0, 0.5));
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(4px);
}

.status-content {
  background: var(--white, #ffffff);
  padding: 40px;
  border-radius: 16px;
  text-align: center;
  box-shadow: var(--shadow-xl, 0 20px 40px rgba(0, 0, 0, 0.3));
  border: 1px solid var(--gray-200, #e5e7eb);
}

.status-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid var(--gray-200, #e5e7eb);
  border-top: 4px solid var(--primary-color, #667eea);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

.status-content p {
  margin: 0;
  color: var(--text-primary, #111827);
  font-size: 18px;
  font-weight: 600;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .main-content {
    grid-template-columns: 280px 1fr;
    gap: 20px;
    padding: 20px;
  }
}

@media (max-width: 768px) {
  .print-page {
    overflow-x: hidden;
  }
  
  .header-content {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
    padding: 16px 20px;
  }
  
  .header-actions {
    justify-content: center;
    flex-wrap: wrap;
  }
  
  .main-content {
    grid-template-columns: 1fr;
    gap: 16px;
    padding: 16px;
  }
  
  .settings-panel {
    position: static;
    order: 2;
  }
  
  .preview-section {
    order: 1;
  }
  
  .images-grid.grid-2,
  .images-grid.grid-4 {
    grid-template-columns: 1fr;
  }
  
  .image-container {
    height: 400px;
  }
  
  .record-info {
    flex-direction: column;
    gap: 8px;
  }
}

@media (max-width: 480px) {
  .header-content {
    padding: 12px 16px;
  }
  
  .main-content {
    padding: 12px;
    gap: 12px;
  }
  
  .settings-content {
    padding: 16px;
  }
  
  .preview-content {
    padding: 16px;
  }
  
  .image-container {
    height: 300px;
  }
}

</style>

// 打印相关工具函数

/**
 * 获取病案类型名称
 * @param {number} type - 类型编号
 * @returns {string} 类型名称
 */
export const getTypeName = (type) => {
  const typeNames = {
    1: '01-病案首页',
    2: '02-病程记录', 
    3: '03-手术记录',
    4: '04-术后病程录',
    5: '05-护理记录',
    6: '06-会诊单',
    7: '07-特殊检查',
    8: '08-检验单',
    9: '09-医嘱',
    10: '10-体温单',
    12: '12-出院记录',
    13: '13-大病历',
    14: '14-其它'
  }
  return typeNames[type] || `类型${type}`
}

/**
 * 格式化日期
 * @param {string} dateString - 日期字符串
 * @returns {string} 格式化后的日期
 */
export const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

/**
 * 生成打印预览数据
 * @param {Array} images - 图片数组
 * @param {Object} settings - 打印设置
 * @returns {Array} 分页后的数据
 */
export const generatePrintPages = (images, settings) => {
  const { imagesPerPage } = settings
  const pages = []
  
  for (let i = 0; i < images.length; i += imagesPerPage) {
    const pageImages = images.slice(i, i + imagesPerPage)
    pages.push({
      pageNumber: Math.floor(i / imagesPerPage) + 1,
      images: pageImages
    })
  }
  
  return pages
}

/**
 * 验证打印数据
 * @param {Array} images - 图片数组
 * @returns {Object} 验证结果
 */
export const validatePrintData = (images) => {
  if (!Array.isArray(images) || images.length === 0) {
    return {
      valid: false,
      message: '没有可打印的图片'
    }
  }
  
  const invalidImages = images.filter(img => !img.cx && !img.blobUrl)
  if (invalidImages.length > 0) {
    return {
      valid: false,
      message: `有 ${invalidImages.length} 张图片缺少有效地址`
    }
  }
  
  return {
    valid: true,
    message: '数据验证通过'
  }
}

/**
 * 计算打印页数
 * @param {number} imageCount - 图片数量
 * @param {number} imagesPerPage - 每页图片数
 * @returns {number} 总页数
 */
export const calculatePageCount = (imageCount, imagesPerPage) => {
  return Math.ceil(imageCount / imagesPerPage)
}

/**
 * 生成打印标题
 * @param {Object} recordInfo - 病案信息
 * @returns {string} 打印标题
 */
export const generatePrintTitle = (recordInfo) => {
  if (!recordInfo) return '病案打印'
  
  const { bah, name, department } = recordInfo
  return `病案打印 - ${bah} - ${name} - ${department}`
}

/**
 * 导出为PDF (需要配合第三方库使用)
 * @param {Array} images - 图片数组
 * @param {Object} settings - 打印设置
 * @param {Object} recordInfo - 病案信息
 * @returns {Promise<Blob>} PDF文件
 */
export const exportToPDF = async (images, settings, recordInfo) => {
  // 这里需要集成PDF生成库，如jsPDF或PDFKit
  // 暂时返回一个占位符
  throw new Error('PDF导出功能需要集成PDF生成库')
}

/**
 * 获取打印样式
 * @param {Object} settings - 打印设置
 * @returns {string} CSS样式字符串
 */
export const getPrintStyles = (settings) => {
  const { paperSize, orientation, imagesPerPage } = settings
  
  return `
    @page {
      size: ${paperSize} ${orientation};
      margin: 1cm;
    }
    
    .print-container {
      font-family: 'Microsoft YaHei', sans-serif;
      line-height: 1.4;
    }
    
    .print-header {
      text-align: center;
      margin-bottom: 20px;
      border-bottom: 2px solid #333;
      padding-bottom: 10px;
    }
    
    .print-images {
      display: grid;
      grid-template-columns: ${imagesPerPage === 1 ? '1fr' : imagesPerPage === 2 ? '1fr 1fr' : '1fr 1fr'};
      gap: 20px;
    }
    
    .print-image {
      width: 100%;
      height: auto;
      border: 1px solid #ddd;
      page-break-inside: avoid;
    }
    
    .print-image-info {
      text-align: center;
      margin-top: 5px;
      font-size: 12px;
      color: #666;
    }
  `
}

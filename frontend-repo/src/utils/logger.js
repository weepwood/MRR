/**
 * 系统日志服务
 * 提供统一的日志记录和管理功能
 */

class Logger {
  constructor() {
    this.logs = []
    this.maxLogs = 1000 // 最大日志条数
    this.logLevel = 'info' // 默认日志级别
  }

  /**
   * 设置日志级别
   * @param {string} level - 日志级别: debug, info, warn, error
   */
  setLevel(level) {
    this.logLevel = level
  }

  /**
   * 检查是否应该记录该级别的日志
   * @param {string} level - 日志级别
   * @returns {boolean}
   */
  shouldLog(level) {
    const levels = ['debug', 'info', 'warn', 'error']
    const currentLevelIndex = levels.indexOf(this.logLevel)
    const messageLevelIndex = levels.indexOf(level)
    return messageLevelIndex >= currentLevelIndex
  }

  /**
   * 添加日志
   * @param {string} level - 日志级别
   * @param {string} message - 日志消息
   * @param {string} details - 详细信息
   * @param {object} context - 上下文信息
   */
  addLog(level, message, details = '', context = {}) {
    if (!this.shouldLog(level)) {
      return
    }

    const log = {
      id: Date.now() + Math.random(),
      time: new Date().toLocaleString('zh-CN'),
      level,
      message,
      details,
      context,
      timestamp: Date.now()
    }

    this.logs.unshift(log) // 新日志添加到开头

    // 限制日志数量
    if (this.logs.length > this.maxLogs) {
      this.logs = this.logs.slice(0, this.maxLogs)
    }

    // 输出到控制台
    this.consoleLog(level, message, details, context)

    // 触发日志更新事件
    this.emitLogUpdate()
  }

  /**
   * 控制台输出
   */
  consoleLog(level, message, details, context) {
    const timestamp = new Date().toLocaleTimeString()
    const prefix = `[${timestamp}] [${level.toUpperCase()}]`
    
    switch (level) {
      case 'error':
        console.error(prefix, message, details, context)
        break
      case 'warn':
        console.warn(prefix, message, details, context)
        break
      case 'info':
        console.info(prefix, message, details, context)
        break
      case 'debug':
        console.debug(prefix, message, details, context)
        break
      default:
        console.log(prefix, message, details, context)
    }
  }

  /**
   * 触发日志更新事件
   */
  emitLogUpdate() {
    // 可以在这里添加事件发射逻辑，通知组件更新
    if (typeof window !== 'undefined' && window.dispatchEvent) {
      window.dispatchEvent(new CustomEvent('logUpdate', { 
        detail: { logs: this.logs } 
      }))
    }
  }

  /**
   * 获取日志
   * @param {string} level - 可选的日志级别过滤
   * @returns {Array}
   */
  getLogs(level = null) {
    if (level) {
      return this.logs.filter(log => log.level === level)
    }
    return [...this.logs]
  }

  /**
   * 清空日志
   */
  clearLogs() {
    this.logs = []
    this.emitLogUpdate()
  }

  /**
   * 导出日志
   * @param {string} format - 导出格式: json, txt
   * @returns {string}
   */
  exportLogs(format = 'json') {
    if (format === 'json') {
      return JSON.stringify(this.logs, null, 2)
    } else if (format === 'txt') {
      return this.logs.map(log => 
        `[${log.time}] [${log.level.toUpperCase()}] ${log.message}${log.details ? ' - ' + log.details : ''}`
      ).join('\n')
    }
    return ''
  }

  // 便捷方法
  debug(message, details = '', context = {}) {
    this.addLog('debug', message, details, context)
  }

  info(message, details = '', context = {}) {
    this.addLog('info', message, details, context)
  }

  warn(message, details = '', context = {}) {
    this.addLog('warn', message, details, context)
  }

  error(message, details = '', context = {}) {
    this.addLog('error', message, details, context)
  }
}

// 创建全局日志实例
const logger = new Logger()

// 监听全局错误
if (typeof window !== 'undefined') {
  window.addEventListener('error', (event) => {
    logger.error('JavaScript错误', event.message, {
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
      error: event.error
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    logger.error('未处理的Promise拒绝', event.reason, {
      promise: event.promise
    })
  })
}

export default logger

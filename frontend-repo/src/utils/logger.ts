/**
 * 绯荤粺鏃ュ織鏈嶅姟
 * 鎻愪緵缁熶竴鐨勬棩蹇楄褰曞拰绠＄悊鍔熻兘
 */

class Logger {
  logs = []
  maxLogs = 1000 // 鏈€澶ф棩蹇楁潯鏁?
  logLevel = 'info' // 榛樿鏃ュ織绾у埆

  /**
   * 璁剧疆鏃ュ織绾у埆
   * @param {string} level - 鏃ュ織绾у埆: debug, info, warn, error
   */
  setLevel(level) {
    this.logLevel = level
  }

  /**
   * 妫€鏌ユ槸鍚﹀簲璇ヨ褰曡绾у埆鐨勬棩蹇?
   * @param {string} level - 鏃ュ織绾у埆
   * @returns {boolean}
   */
  shouldLog(level) {
    const levels = ['debug', 'info', 'warn', 'error']
    const currentLevelIndex = levels.indexOf(this.logLevel)
    const messageLevelIndex = levels.indexOf(level)
    return messageLevelIndex >= currentLevelIndex
  }

  /**
   * 娣诲姞鏃ュ織
   * @param {string} level - 鏃ュ織绾у埆
   * @param {string} message - 鏃ュ織娑堟伅
   * @param {string} details - 璇︾粏淇℃伅
   * @param {object} context - 涓婁笅鏂囦俊鎭?
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

    this.logs.unshift(log)

    if (this.logs.length > this.maxLogs) {
      this.logs = this.logs.slice(0, this.maxLogs)
    }

    this.consoleLog(level, message, details, context)
    this.emitLogUpdate()
  }

  /**
   * 鎺у埗鍙拌緭鍑?
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
   * 瑙﹀彂鏃ュ織鏇存柊浜嬩欢
   */
  emitLogUpdate() {
    if (typeof window !== 'undefined' && window.dispatchEvent) {
      window.dispatchEvent(new CustomEvent('logUpdate', {
        detail: { logs: this.logs }
      }))
    }
  }

  /**
   * 鑾峰彇鏃ュ織
   * @param {string} level - 鍙€夌殑鏃ュ織绾у埆杩囨护
   * @returns {Array}
   */
  getLogs(level = null) {
    if (level) {
      return this.logs.filter(log => log.level === level)
    }
    return [...this.logs]
  }

  /**
   * 娓呯┖鏃ュ織
   */
  clearLogs() {
    this.logs = []
    this.emitLogUpdate()
  }

  /**
   * 瀵煎嚭鏃ュ織
   * @param {string} format - 瀵煎嚭鏍煎紡: json, txt
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

const logger = new Logger()

if (typeof window !== 'undefined') {
  window.addEventListener('error', (event) => {
    logger.error('JavaScript閿欒', event.message, {
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
      error: event.error
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    logger.error('鏈鐞嗙殑Promise鎷掔粷', event.reason, {
      promise: event.promise
    })
  })
}

export default logger

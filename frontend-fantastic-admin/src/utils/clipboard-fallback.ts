function restoreSelection(selection: Selection | null, range: Range | null) {
  if (!selection || !range) {
    return
  }
  selection.removeAllRanges()
  selection.addRange(range)
}

function legacyWriteText(text: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const textarea = document.createElement('textarea')
    const selection = document.getSelection()
    const selectedRange = selection?.rangeCount ? selection.getRangeAt(0) : null

    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.setAttribute('aria-hidden', 'true')
    textarea.style.position = 'fixed'
    textarea.style.top = '0'
    textarea.style.left = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)

    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, textarea.value.length)

    try {
      if (!document.execCommand('copy')) {
        throw new Error('浏览器拒绝复制操作')
      }
      resolve()
    }
    catch (error) {
      reject(error instanceof Error ? error : new Error('复制失败'))
    }
    finally {
      textarea.remove()
      restoreSelection(selection, selectedRange)
    }
  })
}

/**
 * 普通内网 HTTP 页面不属于安全上下文，Chromium 会隐藏 navigator.clipboard。
 * 仅在原生 Clipboard API 不可用时安装 writeText 降级实现。
 */
export function installClipboardFallback() {
  if (window.isSecureContext && typeof navigator.clipboard?.writeText === 'function') {
    return
  }

  const fallbackClipboard = {
    writeText: legacyWriteText,
  } as Clipboard

  try {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: fallbackClipboard,
    })
  }
  catch {
    // 极少数浏览器不允许重定义 Navigator 属性，此时保留原行为并由页面提示复制失败。
  }
}

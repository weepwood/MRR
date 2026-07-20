/* eslint-disable no-console */
if (import.meta.env.PROD) {
  const copyright_common_style = 'font-size: 14px; margin-bottom: 2px; padding: 6px 8px; color: #fff;'
  const copyright_main_style = `${copyright_common_style} background: #e24329;`
  if ((navigator.language).toLowerCase() === 'zh-cn') {
    console.info('%cMRR 管理系统', copyright_main_style)
  }
  else {
    console.info('%cMRR Medical Record Repository', copyright_main_style)
  }
}

export {}

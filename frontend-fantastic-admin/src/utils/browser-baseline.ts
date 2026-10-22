import pkg from '../../package.json'

// browserslist 是构建目标、启动入口和客户端诊断的共同来源。
export const browserMinimumVersions: Record<string, string> = Object.fromEntries(
  pkg.browserslist.map((target) => {
    const match = /^(Chrome|Edge|Firefox|Safari) >= (\d+(?:\.\d+)*)$/.exec(target)
    if (!match) {
      throw new Error(`不支持的浏览器基线声明：${target}`)
    }
    return [match[1], match[2]]
  }),
)

export const browserRequirements = `最低支持：${Object.entries(browserMinimumVersions)
  .map(([browser, version]) => `${browser} ${version}`)
  .join('、')}。`

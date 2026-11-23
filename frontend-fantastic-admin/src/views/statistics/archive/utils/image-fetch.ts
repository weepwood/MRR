/**
 * 客户端取图（打印外的 PDF / ZIP 合成）的 fetch 参数。
 *
 * 内容网关模式（ADR-0009）下影像地址与页面同源，凭 HttpOnly 影像访问 Cookie 认证，必须携带同源凭据；
 * 直连模式的影像源 Nginx 或 OSS 预签名地址通常跨域，保持 omit，不把任何凭据发往影像源。
 */
export function imageFetchInit(resolvedUrl: URL, pageOrigin = window.location.origin): RequestInit {
  const sameOrigin = resolvedUrl.origin === pageOrigin
  return {
    method: 'GET',
    mode: sameOrigin ? 'same-origin' : 'cors',
    credentials: sameOrigin ? 'same-origin' : 'omit',
  }
}

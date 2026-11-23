# MRR 安全响应头（唯一写入点）。
#
# Nginx 的 add_header 不是增量继承：location 内一旦出现任意一条 add_header，
# 该 location 就完全丢弃 server 级继承来的全部 add_header。历史配置因此在
# SPA 根路径、/status、/healthz.txt、维护页上丢失了这三个安全头。
#
# 因此：server 级 include 一次；凡是 location 内自行声明 add_header 的地方，
# 必须再 include 一次。scripts/tests/test_windows_bundled_nginx.py 会守护
# 这个不变量，新增 location 时请同步引入本文件。
add_header X-Content-Type-Options nosniff always;
add_header X-Frame-Options SAMEORIGIN always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
# CSP 先只启用不依赖脚本、样式和影像来源的指令，可安全全局生效：禁止插件对象、
# 篡改 <base>、被第三方页面嵌入和向外部地址提交表单。script-src / img-src 需在确认
# 影像源（内网 HTTP 节点、OSS 域名）和 VitePress 内联脚本后再收紧，见
# docs/engineering/optimization-plan-20261123.md。
add_header Content-Security-Policy "object-src 'none'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;

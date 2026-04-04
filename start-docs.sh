#!/bin/bash
# VitePress 文档开发服务器启动脚本

echo ""
echo "========================================"
echo "   MRR 文档系统 - VitePress"
echo "========================================"
echo ""

cd vitepress-doc

# 检查 node_modules 是否存在
if [ ! -d "node_modules" ]; then
    echo "[INFO] 首次运行，正在安装依赖..."
    echo ""
    npm install
    echo ""
    echo "[SUCCESS] 依赖安装完成！"
    echo ""
fi

echo "[INFO] 启动开发服务器..."
echo "[INFO] 访问地址: http://localhost:5173"
echo "[INFO] 按 Ctrl+C 停止服务器"
echo ""

npm run docs:dev

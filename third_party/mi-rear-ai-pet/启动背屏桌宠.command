#!/bin/zsh
cd "$(dirname "$0")"
echo "正在启动小米 17 Pro Max 背屏 AI 桌宠服务（关闭此窗口即停止）..."
exec /usr/bin/python3 server.py --open

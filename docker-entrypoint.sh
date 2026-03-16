#!/bin/bash
set -e

# 启动后端（后台运行）
echo "Starting OrangeTV backend..."
java -jar /app/app.jar &

# 启动 nginx（前台运行，保持容器存活）
echo "Starting Nginx..."
nginx -g 'daemon off;'

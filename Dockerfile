# ============ 运行阶段：Nginx + JRE ============
FROM eclipse-temurin:17-jre

# 安装 Nginx
RUN apt-get update && apt-get install -y nginx dos2unix && rm -rf /var/lib/apt/lists/*

# 从宿主机复制文件（假设文件已在 /home/orangetv-java 目录）
WORKDIR /app

# 复制后端 JAR 包
COPY app.jar /app/app.jar

# 复制前端 dist 文件到 Nginx 目录
COPY dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -f /etc/nginx/sites-enabled/default

# 创建上传目录
RUN mkdir -p /app/uploads

# 启动脚本
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN dos2unix /docker-entrypoint.sh && chmod +x /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]

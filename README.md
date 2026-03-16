# OrangeTV

<div align="center">
  <img src="orangetv-vue/public/logo.png" alt="OrangeTV Logo" width="120">
</div>

> 🎬 **OrangeTV** 是一个开箱即用的、跨平台的影视聚合播放器。它基于 **Spring Boot 3** + **Vue 3** + **TypeScript** 构建，支持多资源搜索、在线播放、收藏同步、播放记录、用户管理、直播源管理，让你可以随时随地畅享海量免费影视内容。

<div align="center">

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot)
![Vue.js](https://img.shields.io/badge/Vue.js-3-4FC08D?logo=vuedotjs)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript)
![License](https://img.shields.io/badge/License-MIT-green)
![Docker Ready](https://img.shields.io/badge/Docker-ready-blue?logo=docker)

</div>

---

## ✨ 功能特性

- 🔍 **多源聚合搜索**：一次搜索立刻返回全源结果，支持流式输出
- 📄 **丰富详情页**：支持剧集列表、演员、年份、简介等完整信息展示
- ▶️ **流畅在线播放**：集成 HLS.js & ArtPlayer，支持多种视频格式
- ❤️ **收藏 + 继续观看**：支持 MySQL/Redis 存储，多端同步进度
- 👥 **用户系统**：完整的用户注册、登录、权限管理
- 🔐 **设备绑定**：支持机器码绑定，提升账户安全性
- 💬 **社交功能**：好友系统、聊天、一起看
- 🌗 **响应式布局**：桌面侧边栏 + 移动底部导航，自适应各种屏幕尺寸
- 🔌 **LinuxDo OAuth**：支持 LinuxDo 社区 OAuth 登录

### 注意：部署后项目为空壳项目，无内置播放源和直播源，需要自行收集

### 请不要在 B站、小红书、微信公众号、抖音、今日头条或其他中国大陆社交平台发布视频或文章宣传本项目，不授权任何"科技周刊/月刊"类项目或站点收录本项目。

## 🗺 目录

- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [部署](#部署)
- [配置说明](#配置说明)
- [环境变量](#环境变量)
- [功能说明](#功能说明)
- [常见问题](#常见问题)
- [安全与隐私提醒](#安全与隐私提醒)
- [License](#license)
- [致谢](#致谢)

## 技术栈

| 分类      | 主要依赖                                                                                              |
| --------- | ----------------------------------------------------------------------------------------------------- |
| 后端框架  | [Spring Boot 3](https://spring.io/projects/spring-boot) · Java 17                                     |
| 前端框架  | [Vue 3](https://vuejs.org/) · Composition API                                                          |
| UI & 样式 | [Tailwind CSS 3](https://tailwindcss.com/)                                                            |
| 语言      | Java 17 · TypeScript 5                                                                                |
| 数据库    | MySQL 8 · Redis                                                                                       |
| 播放器    | [ArtPlayer](https://github.com/zhw2590582/ArtPlayer) · [HLS.js](https://github.com/video-dev/hls.js/) |
| 部署      | Docker · Docker Compose                                                                               |

## 快速开始

### 前置要求

- Docker 和 Docker Compose
- MySQL 8.x
- Redis

### 部署步骤

1. **准备文件**

在服务器上创建部署目录：
```bash
mkdir -p /home/orangetv-java
cd /home/orangetv-java
```

需要准备以下文件：
- `app.jar` - 后端 JAR 包（通过 `mvn package` 构建）
- `dist/` - 前端构建产物（通过 `npm run build` 构建）
- `docker-compose.yml` - Docker Compose 配置
- `Dockerfile` - Docker 镜像构建文件
- `docker-entrypoint.sh` - 容器启动脚本
- `nginx.conf` - Nginx 配置文件
- `.env` - 环境变量配置文件

2. **配置环境变量**

创建 `.env` 文件（可以从 `.env.example` 复制）：
```bash
cp .env.example .env
```

编辑 `.env` 文件，配置数据库和 Redis 连接信息：
```bash
# 数据库配置
DB_URL=jdbc:mysql://your-mysql-host:3306/orangetv?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&useUnicode=true
DB_USERNAME=root
DB_PASSWORD=your_database_password

# Redis 配置
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
REDIS_DATABASE=0

# JWT 密钥（请修改为随机字符串）
JWT_SECRET=your-random-secret-key-at-least-32-characters

# 管理员账号（首次登录后请修改）
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin123456
```

3. **构建后端**
```bash
cd orangetv-backend
mvn clean package -DskipTests
# 将生成的 JAR 包复制到部署目录并重命名为 app.jar
cp target/orangetv-backend-*.jar /home/orangetv-java/app.jar
```

4. **构建前端**
```bash
cd orangetv-vue
npm install
npm run build
# 将生成的 dist 目录复制到部署目录
cp -r dist /home/orangetv-java/
```

5. **复制配置文件**
```bash
# 复制必要的配置文件到部署目录
cp docker-compose.yml /home/orangetv-java/
cp Dockerfile /home/orangetv-java/
cp docker-entrypoint.sh /home/orangetv-java/
cp nginx.conf /home/orangetv-java/
cp .env.example /home/orangetv-java/
```

6. **转换脚本换行符（重要）**
```bash
cd /home/orangetv-java
# 转换为 Unix 格式，避免 Docker 启动错误
sed -i 's/\r$//' docker-entrypoint.sh
chmod +x docker-entrypoint.sh
```

7. **启动服务**
```bash
docker-compose up -d --build
```

8. **访问应用**
```
http://服务器IP:3000
```

使用 `.env` 文件中配置的管理员账号登录。

## 部署

### 文件结构

部署目录 `/home/orangetv-java` 需要包含以下文件：

```
/home/orangetv-java/
├── app.jar                 # 后端 JAR 包
├── dist/                   # 前端构建产物
│   ├── index.html
│   ├── assets/
│   └── ...
├── docker-compose.yml      # Docker Compose 配置
├── Dockerfile              # Docker 镜像构建文件
├── docker-entrypoint.sh    # 容器启动脚本
├── nginx.conf              # Nginx 配置文件
└── uploads/                # 上传文件目录（自动创建）
```

### Docker Compose 配置

`docker-compose.yml` 示例：

```yaml
services:
  orangetv:
    image: orangetv:latest
    build:
      context: /home/orangetv-java
      dockerfile: Dockerfile
    container_name: orangetv
    restart: unless-stopped
    ports:
      - "3000:80"
    volumes:
      - /home/orangetv-java/uploads:/app/uploads
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m
```

### 启动服务

```bash
cd /home/orangetv-java
docker-compose up -d --build
```

### 查看日志

```bash
docker-compose logs -f
```

### 停止服务

```bash
docker-compose down
```

### 重启服务

```bash
docker-compose restart
```

## 配置说明

### 后端配置（application.yml）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/orangetv
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379
      password: your_password

jwt:
  secret: your-secret-key
  expiration: 86400000

orangetv:
  site-name: OrangeTV
  require-device-code: true
  allow-registration: true

oauth:
  linuxdo:
    client-id: your-client-id
    client-secret: your-client-secret
    redirect-uri: http://your-domain/api/auth/linuxdo/callback
```

### 视频源配置

在管理后台的"视频源管理"中添加视频源：

```json
{
  "name": "示例资源",
  "apiUrl": "http://xxx.com/api.php/provide/vod",
  "enabled": true
}
```

支持标准的苹果 CMS V10 API 格式。

### 直播源配置

在管理后台的"直播源管理"中添加直播源：

- 支持 M3U 格式
- 支持 EPG 节目单
- 支持自定义 User-Agent

### 自定义分类

在管理后台的"站点配置"中配置自定义分类：

```json
{
  "CustomCategories": [
    {
      "name": "华语电影",
      "type": "movie",
      "query": "华语"
    },
    {
      "name": "美剧",
      "type": "tv",
      "query": "美剧"
    }
  ]
}
```

支持的分类类型：
- **movie**：热门、最新、经典、豆瓣高分、冷门佳片、华语、欧美、韩国、日本、动作、喜剧、爱情、科幻、悬疑、恐怖、治愈
- **tv**：热门、美剧、英剧、韩剧、日剧、国产剧、港剧、日本动画、综艺、纪录片

## 环境变量

所有环境变量都可以在 `.env` 文件中配置，或在 `docker-compose.yml` 中直接设置。

### 必需配置

| 变量                      | 说明                 | 示例值                    |
| ------------------------- | -------------------- | ------------------------- |
| DB_URL                    | 数据库连接地址       | jdbc:mysql://localhost:3306/orangetv?... |
| DB_USERNAME               | 数据库用户名         | root                      |
| DB_PASSWORD               | 数据库密码           | your_password             |
| REDIS_HOST                | Redis 主机地址       | localhost                 |
| REDIS_PORT                | Redis 端口           | 6379                      |
| REDIS_PASSWORD            | Redis 密码           | your_password             |

### 可选配置

| 变量                      | 说明                 | 默认值                    |
| ------------------------- | -------------------- | ------------------------- |
| JWT_SECRET                | JWT 密钥             | your-256-bit-secret-key... |
| ADMIN_USERNAME            | 管理员用户名         | admin                     |
| ADMIN_PASSWORD            | 管理员密码           | admin                     |
| SITE_NAME                 | 站点名称             | OrangeTV                  |
| REQUIRE_DEVICE_CODE       | 是否启用设备码       | true                      |
| ALLOW_REGISTRATION        | 是否允许注册         | true                      |
| MAX_MACHINE_CODES         | 最大设备绑定数       | 3                         |
| REDIS_DATABASE            | Redis 数据库编号     | 0                         |
| LINUXDO_CLIENT_ID         | LinuxDo OAuth ID     | -                         |
| LINUXDO_CLIENT_SECRET     | LinuxDo OAuth Secret | -                         |
| LINUXDO_REDIRECT_URI      | LinuxDo 回调地址     | -                         |
| FRONTEND_URL              | 前端 URL             | http://localhost:3000     |
| INTERNAL_API_KEY          | 内部 API 密钥        | orangetv-internal-2026    |

### 使用 Docker 镜像部署

如果使用已发布的 Docker 镜像（如 ghcr.io），只需准备 `docker-compose.yml` 和 `.env` 文件：

```yaml
services:
  orangetv:
    image: ghcr.io/YOUR_USERNAME/orangetv:latest
    container_name: orangetv
    restart: unless-stopped
    ports:
      - "3000:80"
    volumes:
      - ./uploads:/app/uploads
    env_file:
      - .env
```

然后创建 `.env` 文件配置环境变量，直接启动：

```bash
docker-compose up -d
```

## 功能说明

### 用户系统

- **注册/登录**：支持用户名密码登录和 LinuxDo OAuth 登录
- **设备绑定**：支持机器码绑定，一个账号可绑定多个设备
- **权限管理**：支持普通用户、管理员、超级管理员三种角色

### 视频播放

- **多源搜索**：同时搜索多个视频源，快速返回结果
- **在线播放**：支持 HLS、MP4 等多种格式
- **播放记录**：自动记录播放进度，支持继续观看
- **收藏功能**：收藏喜欢的影视作品

### 直播功能

- **直播源管理**：支持添加、编辑、删除直播源
- **EPG 节目单**：支持显示节目单信息
- **频道分类**：支持按分类浏览直播频道

### 社交功能

- **好友系统**：添加好友、查看好友列表
- **聊天功能**：支持文字、图片、表情聊天
- **一起看**：邀请好友一起观看视频，同步播放进度

### 管理后台

- **用户管理**：查看、编辑、删除用户
- **视频源管理**：添加、编辑、删除视频源
- **直播源管理**：管理直播源配置
- **站点配置**：配置站点名称、公告、自定义分类等
- **统计数据**：查看用户数、搜索次数、视频源数量等统计信息

## 常见问题

### 1. Docker 容器启动失败

**问题**：`exec /docker-entrypoint.sh: no such file or directory`

**原因**：脚本文件使用了 Windows 风格的换行符（CRLF）

**解决方案**：
```bash
cd /home/orangetv-java
sed -i 's/\r$//' docker-entrypoint.sh
chmod +x docker-entrypoint.sh
docker-compose up -d --build
```

### 2. 端口被占用

**问题**：`bind: address already in use`

**解决方案**：
```bash
# 查看占用端口的进程
sudo netstat -tulpn | grep 3000

# 修改 docker-compose.yml 中的端口
ports:
  - "8080:80"  # 改为其他端口
```

### 3. 前端页面无法访问

检查步骤：
```bash
# 1. 确认容器正在运行
docker-compose ps

# 2. 查看日志
docker-compose logs

# 3. 检查 dist 目录
ls -la /home/orangetv-java/dist/

# 4. 进入容器检查
docker-compose exec orangetv ls -la /usr/share/nginx/html
```

### 4. 数据库连接失败

1. 确认 MySQL 服务正在运行
2. 检查 application.yml 中的数据库配置
3. 创建数据库：
```sql
CREATE DATABASE IF NOT EXISTS orangetv CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 5. 更新部署

```bash
cd /home/orangetv-java
# 停止服务
docker-compose down
# 更新文件（app.jar 或 dist/）
# 重新构建并启动
docker-compose up -d --build
```

## 安全与隐私提醒

### 请设置密码保护并谨慎开放注册

为了您的安全和避免潜在的法律风险，我们强烈建议：

### 部署要求

1. **修改默认密码**：首次登录后立即修改管理员密码
2. **谨慎开放注册**：建议关闭公开注册，仅供个人或小范围使用
3. **启用设备绑定**：开启设备码功能，提升账户安全性
4. **遵守当地法律**：请确保您的使用行为符合当地法律法规

### 重要声明

- 本项目仅供学习和个人使用
- 请勿将部署的实例用于商业用途或公开服务
- 如因公开分享导致的任何法律问题，用户需自行承担责任
- 项目开发者不对用户的使用行为承担任何法律责任
- 本项目不在中国大陆地区提供服务。如有该项目在向中国大陆地区提供服务，属个人行为。在该地区使用所产生的法律风险及责任，属于用户个人行为，与本项目无关，须自行承担全部责任。特此声明

## License

[MIT](LICENSE) © 2025 OrangeTV & Contributors

## 致谢

- [LibreTV](https://github.com/LibreSpark/LibreTV) — 由此启发，站在巨人的肩膀上
- [MoonTV](https://github.com/MoonTechLab/LunaTV) — 由此启发，第二次站在巨人的肩膀上
- [ArtPlayer](https://github.com/zhw2590582/ArtPlayer) — 提供强大的网页视频播放器
- [HLS.js](https://github.com/video-dev/hls.js) — 实现 HLS 流媒体在浏览器中的播放支持
- [Spring Boot](https://spring.io/projects/spring-boot) — 强大的 Java 后端框架
- [Vue.js](https://vuejs.org/) — 渐进式 JavaScript 框架
- 感谢所有提供免费影视接口的站点

## 贡献

欢迎提交 Issue 和 Pull Request！

## Star History

如果这个项目对你有帮助，请给我们一个 Star ⭐️

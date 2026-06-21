# ============================================================
# 多阶段构建 - Stage 1: 构建阶段
# 使用 Node.js 镜像编译 Vue 3 前端项目
# ============================================================
FROM node:20-alpine AS build

WORKDIR /app

# 先复制 package.json 和 package-lock.json，利用 Docker 缓存层
# 只要依赖不变，node_modules 层就不会重新构建
COPY package*.json ./

# 安装依赖（npm ci 比 npm install 更适合 CI/CD 环境，严格按照 lock 文件安装）
RUN npm ci

# 复制源代码
COPY . .

# 执行生产环境构建，输出到 dist 目录
RUN npm run build

# ============================================================
# 多阶段构建 - Stage 2: 运行阶段
# 使用 Nginx 作为静态文件服务器和反向代理
# ============================================================
FROM nginx:alpine

# 从构建阶段复制打包好的静态文件到 Nginx 默认的 HTML 目录
COPY --from=build /app/dist /usr/share/nginx/html

# 复制自定义 Nginx 配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露 Nginx 默认端口
EXPOSE 80

# 使用 Nginx 默认的前台运行命令
CMD ["nginx", "-g", "daemon off;"]

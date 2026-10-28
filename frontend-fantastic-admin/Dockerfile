ARG NODE_VERSION=24.15.0
FROM node:${NODE_VERSION}-alpine AS docs-build
WORKDIR /app/docs
RUN apk add --no-cache git
COPY VERSION /app/VERSION
COPY backend-repo/examples /app/backend-repo/examples
COPY vitepress-doc/package.json vitepress-doc/package-lock.json ./
RUN npm ci

COPY vitepress-doc/ ./
RUN npm run docs:build

FROM nginx:1.27-alpine
COPY --from=docs-build /app/docs/.vitepress/dist-user /usr/share/nginx/html/docs
COPY --from=docs-build /app/docs/.vitepress/dist-internal /usr/share/nginx/html/docs/internal
COPY frontend-fantastic-admin/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

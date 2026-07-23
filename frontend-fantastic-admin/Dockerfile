FROM node:22-alpine AS docs-build
WORKDIR /app/docs

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

# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /build
# 의존성 레이어를 먼저 캐싱한다
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80

# syntax=docker/dockerfile:1.7
FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm test && npm run build

FROM nginx:1.27-alpine AS runtime
RUN addgroup -S hcop && adduser -S hcop -G hcop \
    && chown -R hcop:hcop /var/cache/nginx /var/run /var/log/nginx \
    && touch /var/run/nginx.pid && chown hcop:hcop /var/run/nginx.pid

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/hcop-jp-angular/browser /usr/share/nginx/html/app
COPY --from=build /workspace/public /usr/share/nginx/html

USER hcop
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=8 \
  CMD wget -q -O- http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]

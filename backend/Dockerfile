FROM node:22-alpine AS builder
WORKDIR /app
COPY backend/package.json backend/package-lock.json ./
RUN npm ci
COPY backend/tsconfig.json ./
COPY backend/src ./src
RUN npm run build

FROM node:22-alpine
WORKDIR /app
COPY backend/package.json backend/package-lock.json ./
RUN npm ci --omit=dev
COPY --from=builder /app/dist ./dist

# SQLite data directory (mount as volume for persistence)
RUN mkdir -p /app/data
VOLUME /app/data

ENV NODE_ENV=production
ENV DB_PATH=/app/data/store.db
ENV PORT=3000

EXPOSE 3000
CMD ["node", "dist/index.js"]

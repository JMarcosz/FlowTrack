FROM node:20-slim AS build
WORKDIR /app

COPY package.json tsconfig.json tsconfig.build.json ./
COPY src ./src

RUN npm install
RUN npm run build

FROM node:20-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY package.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist

EXPOSE 8080
CMD ["node", "dist/main.js"]

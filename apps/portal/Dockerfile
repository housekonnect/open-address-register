# syntax=docker/dockerfile:1
# Builds the portal from the monorepo root (context: repository root). Runtime configuration comes from the
# environment (API_URL, TILES_URL, PUBLIC_URL, ...); no secrets are baked into the image.
FROM node:24.21.0-trixie-slim AS build
ENV CI=true NEXT_TELEMETRY_DISABLED=1 COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable
WORKDIR /repo
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY contracts ./contracts
COPY packages ./packages
COPY apps/portal ./apps/portal
RUN pnpm install --frozen-lockfile --filter "@ugaddress/portal..."
RUN pnpm --filter "@ugaddress/portal" build

FROM node:24.21.0-trixie-slim AS runtime
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
# The runtime only runs `node server.js`; npm and corepack (with their own vulnerable dependencies) are not needed.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
      /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
WORKDIR /app
COPY --from=build --chown=node:node /repo/apps/portal/.next/standalone ./
COPY --from=build --chown=node:node /repo/apps/portal/.next/static ./apps/portal/.next/static
COPY --from=build --chown=node:node /repo/apps/portal/public ./apps/portal/public
USER node
EXPOSE 3000
CMD ["node", "apps/portal/server.js"]

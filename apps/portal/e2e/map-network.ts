import type { Page } from "@playwright/test";

/** Host names that belong to the stack; any other host means a map request left it. */
const STACK_HOSTS = new Set((process.env.E2E_STACK_HOSTS ?? "localhost").split(",").map((h) => h.trim()));

/** Tile requests of the self-hosted basemap and of the register's Martin layers. */
const BASEMAP_TILE = /\/basemap\/\d+\/\d+\/\d+$/;
const REGISTER_TILE = /\/(admin_units|thoroughfares|buildings|public_entrances)\/\d+\/\d+\/\d+$/;

export interface MapNetwork {
  /** Requests to hosts outside the stack. */
  external: string[];
  /** Status codes of basemap tile responses. */
  basemapTiles: number[];
  /** Status codes of register tile responses. */
  registerTiles: number[];
  /** Status codes of glyph and sprite responses. */
  assets: number[];
}

/** Records every request of the page's context, including those of MapLibre's web worker. */
export function trackMapNetwork(page: Page): MapNetwork {
  const network: MapNetwork = { external: [], basemapTiles: [], registerTiles: [], assets: [] };
  const context = page.context();
  context.on("request", (request) => {
    const url = new URL(request.url());
    if ((url.protocol === "http:" || url.protocol === "https:") && !STACK_HOSTS.has(url.hostname)) {
      network.external.push(request.url());
    }
  });
  context.on("response", (response) => {
    const path = new URL(response.url()).pathname;
    if (BASEMAP_TILE.test(path)) network.basemapTiles.push(response.status());
    else if (REGISTER_TILE.test(path)) network.registerTiles.push(response.status());
    else if (path.startsWith("/map/fonts/") || path.startsWith("/map/sprites/")) network.assets.push(response.status());
  });
  return network;
}

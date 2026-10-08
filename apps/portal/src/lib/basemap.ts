import "server-only";
import { readFile } from "node:fs/promises";
import path from "node:path";

/** Font stacks of the style (see infra/basemap/basemap.sh); nothing else is ever read from disk. */
const FONT_STACKS = new Set(["Noto Sans Regular", "Noto Sans Medium", "Noto Sans Italic"]);
const GLYPH_RANGE = /^\d{1,5}-\d{1,5}\.pbf$/;
const SPRITE_FILE = /^(light|dark)(@2x)?\.(json|png)$/;

/** Public base URL of the portal, without a trailing slash. */
export function portalUrl(): string {
  return (process.env.PUBLIC_URL ?? "http://localhost:3000").replace(/\/+$/, "");
}

/** URL of the shared map style that every map (portal, console, field app) loads. */
export function mapStyleUrl(): string {
  return process.env.MAP_STYLE_URL ?? `${portalUrl()}/map/style.json`;
}

/** Base URL of Martin as browsers and devices reach it. */
export function tilesPublicUrl(): string {
  return process.env.TILES_PUBLIC_URL ?? "http://localhost:3002";
}

function assetsDir(): string {
  return path.join(process.env.BASEMAP_DIR ?? "/basemap", "assets");
}

async function readOrUndefined(file: string): Promise<Buffer | undefined> {
  try {
    return await readFile(file);
  } catch {
    return undefined;
  }
}

/**
 * Reads one glyph range. MapLibre may ask for a comma-separated list of font stacks; the first known one is used.
 * Returns `undefined` for unknown stacks, malformed ranges and missing files.
 */
export async function readGlyphs(fontstack: string, range: string): Promise<Buffer | undefined> {
  const stack = decodeURIComponent(fontstack)
    .split(",")
    .map((s) => s.trim())
    .find((s) => FONT_STACKS.has(s));
  if (!stack || !GLYPH_RANGE.test(range)) return undefined;
  return readOrUndefined(path.join(assetsDir(), "fonts", stack, range));
}

/** Reads one sprite file (`light.json`, `dark@2x.png`, ...); `undefined` for any other name. */
export async function readSprite(file: string): Promise<{ body: Buffer; contentType: string } | undefined> {
  if (!SPRITE_FILE.test(file)) return undefined;
  const body = await readOrUndefined(path.join(assetsDir(), "sprites", "v4", file));
  return body && { body, contentType: file.endsWith(".png") ? "image/png" : "application/json" };
}

/** Headers of map resources: other origins (console, field app) load them too. */
export function mapHeaders(contentType: string, maxAgeSeconds: number): HeadersInit {
  return {
    "Content-Type": contentType,
    "Cache-Control": `public, max-age=${maxAgeSeconds}`,
    "Access-Control-Allow-Origin": "*",
  };
}

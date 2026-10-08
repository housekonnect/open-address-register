import { layers as basemapLayers, namedFlavor } from "@protomaps/basemaps";
import type { ExpressionSpecification, FilterSpecification, LayerSpecification, StyleSpecification } from "maplibre-gl";

/** Colour scheme of the map; matches the `.dark` class of the web apps and the field app's colour scheme. */
export type MapTheme = "light" | "dark";

/** Colours of the register's own layers, drawn on top of the basemap. Their layer ids start with `register-`. */
export interface RegisterColors {
  boundary: string;
  street: string;
  streetOwn: string;
  streetLabel: string;
  streetLabelHalo: string;
  building: string;
  buildingOutline: string;
  highlight: string;
  entrance: string;
}

/** The map's colours, per theme. Comma syntax, because MapLibre Native parses only CSS Color 3. */
export const REGISTER_COLORS: Record<MapTheme, RegisterColors> = {
  light: {
    boundary: "hsl(150, 10%, 45%)",
    street: "hsl(150, 12%, 42%)",
    streetOwn: "hsl(152, 55%, 30%)",
    streetLabel: "hsl(150, 25%, 15%)",
    streetLabelHalo: "hsl(0, 0%, 100%)",
    building: "hsl(152, 30%, 62%)",
    buildingOutline: "hsl(152, 40%, 30%)",
    highlight: "hsl(43, 90%, 50%)",
    entrance: "hsl(210, 60%, 45%)",
  },
  dark: {
    boundary: "hsl(150, 8%, 55%)",
    street: "hsl(150, 10%, 60%)",
    streetOwn: "hsl(152, 45%, 52%)",
    streetLabel: "hsl(40, 25%, 92%)",
    streetLabelHalo: "hsl(150, 15%, 12%)",
    building: "hsl(152, 25%, 35%)",
    buildingOutline: "hsl(152, 35%, 55%)",
    highlight: "hsl(43, 85%, 58%)",
    entrance: "hsl(210, 60%, 62%)",
  },
};

/** ODbL attribution required on every map that shows the OpenStreetMap basemap. */
export const OSM_ATTRIBUTION = '<a href="https://www.openstreetmap.org/copyright">© OpenStreetMap contributors</a>';

/** Maximum zoom of the basemap extract (`BASEMAP_MAXZOOM` in infra/basemap/pins.env); MapLibre overzooms beyond. */
const BASEMAP_MAXZOOM = 15;

export interface MapStyleOptions {
  /** Base URL of Martin as browsers and devices reach it, e.g. `http://localhost:3002`. */
  tilesUrl: string;
  /** Base URL under which the portal serves `fonts/` and `sprites/`, e.g. `http://localhost:3000/map`. */
  assetsUrl: string;
  theme: MapTheme;
}

/**
 * Builds the one MapLibre style of the register: the self-hosted Protomaps basemap (OpenStreetMap) with the
 * register's public layers on top. Every URL points into the stack; nothing is fetched from outside.
 * Residential entrances are not part of any layer (ADR 0009).
 */
export function buildMapStyle({ tilesUrl, assetsUrl, theme }: MapStyleOptions): StyleSpecification {
  const tiles = tilesUrl.replace(/\/+$/, "");
  const assets = assetsUrl.replace(/\/+$/, "");
  return {
    version: 8,
    glyphs: `${assets}/fonts/{fontstack}/{range}.pbf`,
    sprite: `${assets}/sprites/v4/${theme}`,
    sources: {
      basemap: {
        type: "vector",
        tiles: [`${tiles}/basemap/{z}/{x}/{y}`],
        maxzoom: BASEMAP_MAXZOOM,
        attribution: OSM_ATTRIBUTION,
      },
      admin_units: { type: "vector", url: `${tiles}/admin_units` },
      thoroughfares: { type: "vector", url: `${tiles}/thoroughfares` },
      buildings: { type: "vector", url: `${tiles}/buildings` },
      public_entrances: { type: "vector", url: `${tiles}/public_entrances` },
    },
    layers: [...basemapLayers("basemap", namedFlavor(theme), { lang: "en" }), ...registerLayers(REGISTER_COLORS[theme])],
  };
}

function registerLayers(colors: RegisterColors): LayerSpecification[] {
  return [
    {
      id: "register-admin-units",
      type: "line",
      source: "admin_units",
      "source-layer": "admin_units",
      paint: { "line-color": colors.boundary, "line-width": 1.5, "line-dasharray": [3, 2] },
    },
    {
      id: "register-buildings",
      type: "fill",
      source: "buildings",
      "source-layer": "buildings",
      paint: { "fill-color": colors.building, "fill-opacity": 0.85, "fill-outline-color": colors.buildingOutline },
    },
    {
      id: "register-buildings-highlight",
      type: "fill",
      source: "buildings",
      "source-layer": "buildings",
      filter: highlightFilter(undefined),
      paint: { "fill-color": colors.highlight, "fill-outline-color": colors.buildingOutline },
    },
    {
      id: "register-streets",
      type: "line",
      source: "thoroughfares",
      "source-layer": "thoroughfares",
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": colors.street, "line-width": 4 },
    },
    {
      id: "register-street-labels",
      type: "symbol",
      source: "thoroughfares",
      "source-layer": "thoroughfares",
      layout: {
        "symbol-placement": "line",
        "text-field": ["get", "name"],
        "text-font": ["Noto Sans Medium"],
        "text-size": 12,
      },
      paint: { "text-color": colors.streetLabel, "text-halo-color": colors.streetLabelHalo, "text-halo-width": 1.5 },
    },
    {
      id: "register-public-entrances",
      type: "circle",
      source: "public_entrances",
      "source-layer": "public_entrances",
      paint: { "circle-color": colors.entrance, "circle-radius": 4 },
    },
  ];
}

/** Filter of the `register-buildings-highlight` layer: the building with this national ID, or none. */
export function highlightFilter(nationalId: string | undefined): FilterSpecification {
  return ["==", ["get", "national_id"], nationalId ?? ""];
}

/** Paint of the `register-streets` layer that draws the streets of `custodian` thicker and in the "own" colour. */
export function ownStreetPaint(
  custodian: string,
  colors: RegisterColors,
): { "line-color": ExpressionSpecification; "line-width": ExpressionSpecification } {
  const own: ExpressionSpecification = ["in", custodian, ["coalesce", ["get", "custodians"], ""]];
  return {
    "line-color": ["case", own, colors.streetOwn, colors.street],
    "line-width": ["case", own, 6, 4],
  };
}

/** URL of the shared style for a theme. */
export function styleUrlFor(styleUrl: string, theme: MapTheme): string {
  return theme === "dark" ? `${styleUrl}${styleUrl.includes("?") ? "&" : "?"}theme=dark` : styleUrl;
}

import type { ExpressionSpecification, StyleSpecification } from "maplibre-gl";

/** Colours of the map layers, resolved from the semantic CSS tokens (`--map-*`). */
export interface MapColors {
  background: string;
  boundary: string;
  street: string;
  streetOwn: string;
  building: string;
  buildingOutline: string;
  highlight: string;
  entrance: string;
}

export interface MapStyleOptions {
  /** Base URL of the Martin tile server, e.g. `http://localhost:3002`. */
  tilesUrl: string;
  colors: MapColors;
  /** National ID of a building to highlight. */
  highlightNationalId?: string | undefined;
  /** Custodian code whose streets are drawn in the "own" colour (console). */
  ownCustodian?: string | undefined;
}

/** Names of the CSS variables that hold the map colours. */
export const MAP_COLOR_TOKENS: Record<keyof MapColors, string> = {
  background: "--map-background",
  boundary: "--map-boundary",
  street: "--map-street",
  streetOwn: "--map-street-own",
  building: "--map-building",
  buildingOutline: "--map-building-outline",
  highlight: "--map-highlight",
  entrance: "--map-entrance",
};

/** Reads the map colours from the semantic tokens of the current document. */
export function readMapColors(element: Element = document.documentElement): MapColors {
  const style = getComputedStyle(element);
  const read = (token: string) => style.getPropertyValue(token).trim();
  return {
    background: read(MAP_COLOR_TOKENS.background),
    boundary: read(MAP_COLOR_TOKENS.boundary),
    street: read(MAP_COLOR_TOKENS.street),
    streetOwn: read(MAP_COLOR_TOKENS.streetOwn),
    building: read(MAP_COLOR_TOKENS.building),
    buildingOutline: read(MAP_COLOR_TOKENS.buildingOutline),
    highlight: read(MAP_COLOR_TOKENS.highlight),
    entrance: read(MAP_COLOR_TOKENS.entrance),
  };
}

/**
 * Builds the MapLibre style for the register's own vector tiles (served by Martin from the `tiles` schema).
 * There is no external base map: everything shown comes from the register.
 */
export function buildMapStyle({ tilesUrl, colors, highlightNationalId, ownCustodian }: MapStyleOptions): StyleSpecification {
  const base = tilesUrl.replace(/\/+$/, "");
  const ownStreet: ExpressionSpecification = ownCustodian
    ? ["in", ownCustodian, ["coalesce", ["get", "custodians"], ""]]
    : ["boolean", false];
  return {
    version: 8,
    sources: {
      admin_units: { type: "vector", url: `${base}/admin_units` },
      thoroughfares: { type: "vector", url: `${base}/thoroughfares` },
      buildings: { type: "vector", url: `${base}/buildings` },
      public_entrances: { type: "vector", url: `${base}/public_entrances` },
    },
    layers: [
      { id: "background", type: "background", paint: { "background-color": colors.background } },
      {
        id: "admin-units",
        type: "line",
        source: "admin_units",
        "source-layer": "admin_units",
        paint: { "line-color": colors.boundary, "line-width": 1, "line-dasharray": [3, 2] },
      },
      {
        id: "buildings",
        type: "fill",
        source: "buildings",
        "source-layer": "buildings",
        paint: { "fill-color": colors.building, "fill-outline-color": colors.buildingOutline },
      },
      {
        id: "buildings-highlight",
        type: "fill",
        source: "buildings",
        "source-layer": "buildings",
        filter: ["==", ["get", "national_id"], highlightNationalId ?? ""],
        paint: { "fill-color": colors.highlight, "fill-outline-color": colors.buildingOutline },
      },
      {
        id: "streets",
        type: "line",
        source: "thoroughfares",
        "source-layer": "thoroughfares",
        layout: { "line-cap": "round" },
        paint: {
          "line-color": ["case", ownStreet, colors.streetOwn, colors.street],
          "line-width": ["case", ownStreet, 6, 4],
        },
      },
      {
        id: "public-entrances",
        type: "circle",
        source: "public_entrances",
        "source-layer": "public_entrances",
        paint: { "circle-color": colors.entrance, "circle-radius": 4 },
      },
    ],
  };
}

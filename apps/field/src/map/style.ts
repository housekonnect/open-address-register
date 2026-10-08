import type { Tokens } from "../theme/tokens";

/** MapLibre style with the register's public vector tiles (Martin). No external base map. */
export function fieldMapStyle(tilesUrl: string, tokens: Tokens) {
  const base = tilesUrl.replace(/\/+$/, "");
  return {
    version: 8 as const,
    sources: {
      thoroughfares: { type: "vector" as const, url: `${base}/thoroughfares` },
      buildings: { type: "vector" as const, url: `${base}/buildings` },
    },
    layers: [
      { id: "background", type: "background" as const, paint: { "background-color": tokens.mapBackground } },
      {
        id: "buildings",
        type: "fill" as const,
        source: "buildings",
        "source-layer": "buildings",
        paint: { "fill-color": tokens.mapBuilding, "fill-outline-color": tokens.mapBuildingOutline },
      },
      {
        id: "streets",
        type: "line" as const,
        source: "thoroughfares",
        "source-layer": "thoroughfares",
        paint: { "line-color": tokens.mapStreet, "line-width": 4 },
      },
    ],
  };
}

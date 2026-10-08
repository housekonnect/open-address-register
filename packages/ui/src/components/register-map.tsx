"use client";

import "maplibre-gl/dist/maplibre-gl.css";
import {
  AttributionControl,
  Map as MapLibreMap,
  NavigationControl,
  setWorkerUrl,
  type LngLatBoundsLike,
  type MapMouseEvent,
} from "maplibre-gl";
import * as React from "react";
import { highlightFilter, ownStreetPaint, REGISTER_COLORS, styleUrlFor, type MapTheme } from "../lib/map-style";
import { cn } from "../lib/utils";

// maplibre-gl derives its worker URL from its own `import.meta.url` at runtime. After bundling that is the chunk's
// URL, so the worker is requested from a path that does not exist and no tile is ever loaded. Letting the bundler
// emit and resolve the worker file gives MapLibre a URL that exists.
setWorkerUrl(new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url).href);

/** A feature the user clicked on the map. */
export interface MapSelection {
  layer: "street" | "building";
  id: string;
  name?: string | undefined;
  nationalId?: string | undefined;
}

export interface RegisterMapProps {
  /** URL of the shared map style (`MAP_STYLE_URL`, served by the portal at `/map/style.json`). */
  styleUrl: string;
  /** Initial centre as `[longitude, latitude]`; ignored when `bounds` is given. */
  center?: [number, number] | undefined;
  /** Initial view as `[west, south, east, north]`. */
  bounds?: [number, number, number, number] | undefined;
  zoom?: number;
  highlightNationalId?: string | undefined;
  ownCustodian?: string | undefined;
  /** Called when a street or building is clicked. Enables selection. */
  onSelect?: ((selection: MapSelection) => void) | undefined;
  /** Accessible label of the map region (translated by the caller). */
  label: string;
  className?: string;
}

function currentTheme(): MapTheme {
  return document.documentElement.classList.contains("dark") ? "dark" : "light";
}

/**
 * Map of the register on the self-hosted basemap (MapLibre GL JS, Martin and the portal's style).
 * The container's `data-map-state` becomes `idle` once the style and all visible tiles have been rendered.
 */
export function RegisterMap({
  styleUrl,
  center,
  bounds,
  zoom = 16,
  highlightNationalId,
  ownCustodian,
  onSelect,
  label,
  className,
}: RegisterMapProps) {
  const container = React.useRef<HTMLDivElement>(null);
  const mapRef = React.useRef<MapLibreMap | null>(null);
  const highlightRef = React.useRef(highlightNationalId);
  const onSelectRef = React.useRef(onSelect);
  React.useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  const [lon, lat] = center ?? [0, 0];
  const [west, south, east, north] = bounds ?? [];
  React.useEffect(() => {
    const element = container.current;
    if (!element) return;
    const theme = currentTheme();
    const initialBounds: LngLatBoundsLike | undefined =
      west !== undefined && south !== undefined && east !== undefined && north !== undefined
        ? [west, south, east, north]
        : undefined;
    element.dataset.mapState = "loading";
    const map = new MapLibreMap({
      container: element,
      style: styleUrlFor(styleUrl, theme),
      ...(initialBounds ? { bounds: initialBounds, fitBoundsOptions: { padding: 24 } } : { center: [lon, lat], zoom }),
      attributionControl: false,
    });
    map.addControl(new AttributionControl({ compact: false }), "bottom-right");
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    mapRef.current = map;
    map.on("load", () => {
      map.setFilter("register-buildings-highlight", highlightFilter(highlightRef.current));
      if (ownCustodian) {
        const paint = ownStreetPaint(ownCustodian, REGISTER_COLORS[theme]);
        map.setPaintProperty("register-streets", "line-color", paint["line-color"]);
        map.setPaintProperty("register-streets", "line-width", paint["line-width"]);
      }
    });
    map.on("idle", () => {
      element.dataset.mapState = "idle";
    });
    map.on("error", (event) => {
      element.dataset.mapState = "error";
      console.error("Map error", event.error);
    });
    map.on("click", (event: MapMouseEvent) => {
      const handler = onSelectRef.current;
      if (!handler) return;
      const [feature] = map.queryRenderedFeatures(event.point, { layers: ["register-buildings", "register-streets"] });
      if (!feature) return;
      const properties = feature.properties as Record<string, string | undefined>;
      handler({
        layer: feature.layer.id === "register-streets" ? "street" : "building",
        id: properties.id ?? "",
        name: properties.name,
        nationalId: properties.national_id,
      });
    });
    for (const layer of ["register-buildings", "register-streets"]) {
      map.on("mouseenter", layer, () => {
        if (onSelectRef.current) map.getCanvas().style.cursor = "pointer";
      });
      map.on("mouseleave", layer, () => {
        map.getCanvas().style.cursor = "";
      });
    }
    return () => {
      mapRef.current = null;
      map.remove();
    };
  }, [styleUrl, lon, lat, west, south, east, north, zoom, ownCustodian]);

  // Changing the highlighted building only updates a filter; the map and its view stay as they are.
  React.useEffect(() => {
    highlightRef.current = highlightNationalId;
    const map = mapRef.current;
    if (map?.isStyleLoaded()) map.setFilter("register-buildings-highlight", highlightFilter(highlightNationalId));
  }, [highlightNationalId]);

  return (
    <div
      ref={container}
      role="region"
      aria-label={label}
      className={cn("h-80 w-full overflow-hidden rounded-lg border bg-muted", className)}
    />
  );
}

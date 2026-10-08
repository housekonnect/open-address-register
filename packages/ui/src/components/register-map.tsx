"use client";

import "maplibre-gl/dist/maplibre-gl.css";
import { Map as MapLibreMap, NavigationControl, type MapMouseEvent } from "maplibre-gl";
import * as React from "react";
import { buildMapStyle, readMapColors } from "../lib/map-style";
import { cn } from "../lib/utils";

/** A feature the user clicked on the map. */
export interface MapSelection {
  layer: "street" | "building";
  id: string;
  name?: string | undefined;
  nationalId?: string | undefined;
}

export interface RegisterMapProps {
  /** Base URL of the Martin tile server, reachable from the browser. */
  tilesUrl: string;
  /** Initial centre as `[longitude, latitude]`. */
  center: [number, number];
  zoom?: number;
  highlightNationalId?: string | undefined;
  ownCustodian?: string | undefined;
  /** Called when a street or building is clicked. Enables selection. */
  onSelect?: ((selection: MapSelection) => void) | undefined;
  /** Accessible label of the map region (translated by the caller). */
  label: string;
  className?: string;
}

/** Map of the register's own vector tiles (MapLibre GL JS + Martin). */
export function RegisterMap({
  tilesUrl,
  center,
  zoom = 16,
  highlightNationalId,
  ownCustodian,
  onSelect,
  label,
  className,
}: RegisterMapProps) {
  const container = React.useRef<HTMLDivElement>(null);
  const onSelectRef = React.useRef(onSelect);
  React.useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  const [lon, lat] = center;
  React.useEffect(() => {
    if (!container.current) return;
    const map = new MapLibreMap({
      container: container.current,
      style: buildMapStyle({ tilesUrl, colors: readMapColors(), highlightNationalId, ownCustodian }),
      center: [lon, lat],
      zoom,
      attributionControl: false,
    });
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    map.on("click", (event: MapMouseEvent) => {
      const handler = onSelectRef.current;
      if (!handler) return;
      const [feature] = map.queryRenderedFeatures(event.point, { layers: ["buildings", "streets"] });
      if (!feature) return;
      const properties = feature.properties as Record<string, string | undefined>;
      handler({
        layer: feature.layer.id === "streets" ? "street" : "building",
        id: properties.id ?? "",
        name: properties.name,
        nationalId: properties.national_id,
      });
    });
    for (const layer of ["buildings", "streets"]) {
      map.on("mouseenter", layer, () => {
        if (onSelectRef.current) map.getCanvas().style.cursor = "pointer";
      });
      map.on("mouseleave", layer, () => {
        map.getCanvas().style.cursor = "";
      });
    }
    return () => {
      map.remove();
    };
  }, [tilesUrl, lon, lat, zoom, highlightNationalId, ownCustodian]);

  return (
    <div
      ref={container}
      role="region"
      aria-label={label}
      className={cn("h-80 w-full overflow-hidden rounded-lg border bg-muted", className)}
    />
  );
}

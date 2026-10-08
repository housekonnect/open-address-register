import { describe, expect, it } from "vitest";
import { buildMapStyle, highlightFilter, OSM_ATTRIBUTION, ownStreetPaint, REGISTER_COLORS, styleUrlFor } from "./map-style";

const options = { tilesUrl: "http://tiles.test/", assetsUrl: "http://portal.test/map/", theme: "light" } as const;

describe("buildMapStyle", () => {
  it("points every URL into the stack", () => {
    // GIVEN tile and asset base URLs with trailing slashes
    // WHEN the style is built
    const style = buildMapStyle(options);
    // THEN glyphs, sprites and all sources use those hosts and nothing else
    expect(style.glyphs).toBe("http://portal.test/map/fonts/{fontstack}/{range}.pbf");
    expect(style.sprite).toBe("http://portal.test/map/sprites/v4/light");
    const urls = Object.values(style.sources).flatMap((s) => ("url" in s && s.url ? [s.url] : "tiles" in s ? (s.tiles ?? []) : []));
    expect(urls).toEqual([
      "http://tiles.test/basemap/{z}/{x}/{y}",
      "http://tiles.test/admin_units",
      "http://tiles.test/thoroughfares",
      "http://tiles.test/buildings",
      "http://tiles.test/public_entrances",
    ]);
    expect(JSON.stringify(style)).not.toMatch(/https?:\/\/(?!tiles\.test|portal\.test|www\.openstreetmap\.org\/copyright)/);
  });

  it("credits OpenStreetMap on the basemap source", () => {
    // GIVEN / WHEN the style is built
    const style = buildMapStyle(options);
    // THEN the basemap carries the ODbL attribution
    expect(style.sources.basemap).toMatchObject({ attribution: OSM_ATTRIBUTION });
    expect(OSM_ATTRIBUTION).toContain("© OpenStreetMap contributors");
  });

  it("draws the register's layers on top of the basemap and never residential entrances", () => {
    // GIVEN / WHEN the dark style is built
    const style = buildMapStyle({ ...options, theme: "dark" });
    // THEN the register layers come last and use the dark colours
    const ids = style.layers.map((l) => l.id);
    expect(ids.slice(-6)).toEqual([
      "register-admin-units",
      "register-buildings",
      "register-buildings-highlight",
      "register-streets",
      "register-street-labels",
      "register-public-entrances",
    ]);
    expect(ids.length).toBeGreaterThan(50);
    expect(new Set(ids).size).toBe(ids.length);
    expect(JSON.stringify(style.layers.find((l) => l.id === "register-streets"))).toContain(REGISTER_COLORS.dark.street);
    expect(Object.keys(style.sources)).not.toContain("residential_entrances");
    expect(style.sprite).toBe("http://portal.test/map/sprites/v4/dark");
  });
});

describe("map helpers", () => {
  it("highlights the requested building and the custodian's own streets", () => {
    // GIVEN a building and a custodian
    // WHEN the filter and the street paint are built
    const filter = highlightFilter("95261845754");
    const paint = ownStreetPaint("demo-city", REGISTER_COLORS.light);
    // THEN they reference them
    expect(JSON.stringify(filter)).toContain("95261845754");
    expect(JSON.stringify(paint["line-color"])).toContain("demo-city");
    expect(highlightFilter(undefined)).toEqual(["==", ["get", "national_id"], ""]);
  });

  it("adds the theme to the style URL only for dark", () => {
    // GIVEN the shared style URL
    // WHEN a theme is applied
    // THEN light keeps the URL and dark adds the parameter
    expect(styleUrlFor("http://portal.test/map/style.json", "light")).toBe("http://portal.test/map/style.json");
    expect(styleUrlFor("http://portal.test/map/style.json", "dark")).toBe("http://portal.test/map/style.json?theme=dark");
  });
});

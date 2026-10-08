import { describe, expect, it } from "vitest";
import { buildMapStyle, type MapColors } from "./map-style";

const colors: MapColors = {
  background: "hsl(0 0% 100%)",
  boundary: "hsl(0 0% 50%)",
  street: "hsl(0 0% 40%)",
  streetOwn: "hsl(150 50% 30%)",
  building: "hsl(0 0% 80%)",
  buildingOutline: "hsl(0 0% 60%)",
  highlight: "hsl(45 90% 50%)",
  entrance: "hsl(210 60% 45%)",
};

describe("buildMapStyle", () => {
  it("uses only the register's own Martin sources", () => {
    // GIVEN a tile server URL with a trailing slash
    // WHEN the style is built
    const style = buildMapStyle({ tilesUrl: "http://tiles.test/", colors });
    // THEN every source points at Martin and no residential entrance source exists
    expect(Object.values(style.sources).map((s) => ("url" in s ? s.url : ""))).toEqual([
      "http://tiles.test/admin_units",
      "http://tiles.test/thoroughfares",
      "http://tiles.test/buildings",
      "http://tiles.test/public_entrances",
    ]);
  });

  it("highlights the requested building and the custodian's own streets", () => {
    // GIVEN a building and a custodian
    // WHEN the style is built
    const style = buildMapStyle({ tilesUrl: "http://tiles.test", colors, highlightNationalId: "95261845754", ownCustodian: "demo-city" });
    // THEN the highlight filter and the street expression reference them
    const highlight = style.layers.find((l) => l.id === "buildings-highlight");
    const streets = style.layers.find((l) => l.id === "streets");
    expect(JSON.stringify(highlight)).toContain("95261845754");
    expect(JSON.stringify(streets)).toContain("demo-city");
  });
});

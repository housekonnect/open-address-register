import { expect, test } from "@playwright/test";
import { trackMapNetwork } from "./map-network";

test("the address page shows the basemap and the register's layers, all served by the stack", async ({ page }, testInfo) => {
  // GIVEN a record of every request the page makes
  const network = trackMapNetwork(page);

  // WHEN a demonstration address is looked up by its alias and its map has finished rendering
  await page.goto("/lookup?ref=demo-plot:MIR-0001");
  await expect(page).toHaveURL(/\/a\/\d{11}$/);
  const map = page.locator("div[data-map-state]");
  await expect(map).toHaveAttribute("data-map-state", "idle", { timeout: 60_000 });

  // THEN basemap and register tiles were loaded, nothing failed and nothing left the stack
  expect(network.basemapTiles).toContain(200);
  expect(network.registerTiles).toContain(200);
  expect([...network.basemapTiles, ...network.registerTiles, ...network.assets].filter((status) => status >= 400)).toEqual([]);
  expect(network.assets).toContain(200);
  expect(network.external).toEqual([]);
  await expect(map.getByText("© OpenStreetMap contributors")).toBeVisible();

  const screenshot = testInfo.outputPath("portal-map.png");
  await map.screenshot({ path: screenshot });
  await testInfo.attach("portal-map", { path: screenshot, contentType: "image/png" });
});

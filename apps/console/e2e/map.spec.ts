import { expect, test } from "@playwright/test";
import { trackMapNetwork } from "./map-network";
import { signIn } from "./sign-in";

test("the workspace map shows the basemap and the register's layers, all served by the stack", async ({ page, baseURL }, testInfo) => {
  // GIVEN a signed-in custodian editor and a record of every request the page makes
  const network = trackMapNetwork(page);
  await signIn(page, baseURL ?? "http://localhost:3001", "editor");

  // WHEN the workspace map has finished rendering
  const map = page.locator("div[data-map-state]");
  await expect(map).toHaveAttribute("data-map-state", "idle", { timeout: 60_000 });

  // THEN basemap and register tiles were loaded, nothing failed and nothing left the stack
  expect(network.basemapTiles).toContain(200);
  expect(network.registerTiles).toContain(200);
  expect([...network.basemapTiles, ...network.registerTiles, ...network.assets].filter((status) => status >= 400)).toEqual([]);
  expect(network.assets).toContain(200);
  expect(network.external).toEqual([]);
  await expect(map.getByText("© OpenStreetMap contributors")).toBeVisible();

  const screenshot = testInfo.outputPath("console-map.png");
  await map.screenshot({ path: screenshot });
  await testInfo.attach("console-map", { path: screenshot, contentType: "image/png" });
});

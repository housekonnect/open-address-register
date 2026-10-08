import { defineConfig, devices } from "@playwright/test";

/** End-to-end tests against the running stack (`make up`); see README. */
export default defineConfig({
  testDir: "e2e",
  timeout: 90_000,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.CONSOLE_URL ?? "http://localhost:3001",
    trace: "retain-on-failure",
  },
  // PLAYWRIGHT_CHANNEL=chrome uses an installed Chrome instead of Playwright's Chromium.
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 900 }, channel: process.env.PLAYWRIGHT_CHANNEL } }],
});

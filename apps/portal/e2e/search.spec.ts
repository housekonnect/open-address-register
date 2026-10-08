import { expect, test } from "@playwright/test";

test("the search box finds a street despite a typo and opens an address", async ({ page }) => {
  // GIVEN the home page
  await page.goto("/");

  // WHEN a street name with a typo is searched
  await page.getByLabel("Address, street, place, national address ID or reference").fill("amani avnue");
  await page.getByRole("button", { name: "Search" }).click();

  // THEN the street's addresses are listed, best match first, and the first one opens its address page
  await expect(page).toHaveURL(/\/search\?q=amani\+avnue$/);
  const results = page.getByRole("listitem");
  await expect(results.first()).toContainText("1 Amani Avenue");
  await results.first().getByRole("link").click();
  await expect(page).toHaveURL(/\/a\/\d{11}$/);
  await expect(page.locator("[data-slot=card-title]", { hasText: "1 Amani Avenue" })).toBeVisible();
});

test("a reference typed into the search box goes straight to its address", async ({ page }) => {
  // GIVEN an alias that resolves
  await page.goto("/");

  // WHEN it is searched
  await page.getByLabel("Address, street, place, national address ID or reference").fill("demo-plot:AMA-0001");
  await page.getByRole("button", { name: "Search" }).click();

  // THEN the address page opens directly
  await expect(page).toHaveURL(/\/a\/\d{11}$/);
  await expect(page.locator("[data-slot=card-title]", { hasText: "1 Amani Avenue" })).toBeVisible();
});

import type { Page } from "@playwright/test";

/** Password of the synthetic test users, from the root `.env` (`make e2e` exports it). */
function testUserPassword(): string {
  const password = process.env.TEST_USER_PASSWORD;
  if (!password) throw new Error("TEST_USER_PASSWORD is not set; run the tests with `make e2e`.");
  return password;
}

/** Signs a synthetic test user in to the console through Authentik and waits for the console to load. */
export async function signIn(page: Page, consoleUrl: string, username: string): Promise<void> {
  await page.goto("/api/auth/login");
  await page.locator('input[name="uidField"]').fill(username);
  await page.getByRole("button", { name: "Log in" }).click();
  // The password stage replaces the identification stage; wait for it before typing.
  const continueButton = page.getByRole("button", { name: "Continue" });
  await continueButton.waitFor();
  await page.getByRole("textbox", { name: "Password" }).fill(testUserPassword());
  await continueButton.click();
  await page.waitForURL(new URL("/", consoleUrl).href);
}

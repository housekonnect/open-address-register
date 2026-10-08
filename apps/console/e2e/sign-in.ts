import type { Page } from "@playwright/test";
import { secretOf, totp } from "./totp";

function env(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is not set; run the tests with \`make e2e\`.`);
  return value;
}

/** authentik's public URL; the bootstrap token from `.env` authorises its admin API. */
const AUTHENTIK_URL = (process.env.OIDC_PUBLIC_URL ?? "http://localhost:9000").replace(/\/+$/, "");

async function authentikApi(path: string, method = "GET"): Promise<Response> {
  const response = await fetch(`${AUTHENTIK_URL}/api/v3${path}`, {
    method,
    headers: { Authorization: `Bearer ${env("AUTHENTIK_BOOTSTRAP_TOKEN")}`, Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`authentik API ${method} ${path} answered ${response.status}`);
  return response;
}

/**
 * Removes the user's TOTP devices, so that the next login enrols a fresh one whose secret the test knows. (Their
 * second factor is required: custodian-editor, custodian-approver and steward-admin must use MFA.)
 */
async function resetTotpDevices(username: string): Promise<void> {
  const users = (await (await authentikApi(`/core/users/?username=${encodeURIComponent(username)}`)).json()) as {
    results: { pk: number }[];
  };
  const user = users.results[0];
  if (!user) throw new Error(`No authentik user ${username}`);
  const devices = (await (await authentikApi(`/authenticators/admin/totp/?user=${user.pk}`)).json()) as {
    results: { pk: number }[];
  };
  for (const device of devices.results) await authentikApi(`/authenticators/admin/totp/${device.pk}/`, "DELETE");
}

/**
 * Signs a synthetic test user in to the console through authentik and waits for the console to load. Users in a
 * group that requires MFA enrol a TOTP device on the way, exactly as a person does on first login.
 */
export async function signIn(page: Page, consoleUrl: string, username: string): Promise<void> {
  await resetTotpDevices(username);
  // The TOTP setup challenge carries the otpauth:// URL (shown to people as a QR code).
  let configUrl: string | undefined;
  page.on("response", async (response) => {
    if (!response.url().includes("/api/v3/flows/executor/")) return;
    const challenge = (await response.json().catch(() => ({}))) as { component?: string; config_url?: string };
    if (challenge.component === "ak-stage-authenticator-totp" && challenge.config_url) configUrl = challenge.config_url;
  });

  await page.goto("/api/auth/login");
  await page.locator('input[name="uidField"]').fill(username);
  await page.getByRole("button", { name: "Log in" }).click();
  // The password stage replaces the identification stage; wait for it before typing.
  const continueButton = page.getByRole("button", { name: "Continue" });
  await continueButton.waitFor();
  await page.getByRole("textbox", { name: "Password" }).fill(env("TEST_USER_PASSWORD"));
  await continueButton.click();

  const consoleHome = new URL("/", consoleUrl).href;
  const totpChoice = page.getByRole("button", { name: /TOTP Device/ });
  await Promise.race([page.waitForURL(consoleHome), totpChoice.waitFor()]);
  if (page.url() !== consoleHome) {
    await totpChoice.click();
    await page.locator('input[name="code"]').waitFor();
    if (!configUrl) throw new Error("authentik did not send a TOTP setup challenge");
    await page.locator('input[name="code"]').fill(totp(secretOf(configUrl)));
    await page.getByRole("button", { name: "Continue" }).click();
  }
  await page.waitForURL(consoleHome);
}

import { describe, expect, it } from "vitest";
import { seal, unseal, type Session } from "./session-crypto";

const secret = "0123456789abcdef0123456789abcdef-test";
const session: Session = {
  sub: "abc",
  name: "Demo Editor",
  custodian: "demo-city",
  groups: ["custodian-editor"],
  accessToken: "token",
  expiresAt: Math.floor(Date.now() / 1000) + 600,
};

describe("session cookies", () => {
  it("round-trips a sealed session", async () => {
    // GIVEN a sealed session WHEN it is unsealed with the same secret THEN it is unchanged
    const sealed = await seal(session, secret, session.expiresAt);
    expect(sealed).not.toContain("Demo Editor");
    expect(await unseal<Session>(sealed, secret)).toEqual(session);
  });

  it("rejects tampered, foreign or expired cookies", async () => {
    const sealed = await seal(session, secret, session.expiresAt);
    // WHEN the ciphertext is modified THEN nothing is returned
    expect(await unseal(`${sealed.slice(0, -2)}xx`, secret)).toBeUndefined();
    // WHEN another secret is used THEN nothing is returned
    expect(await unseal(sealed, `${secret}-other`)).toBeUndefined();
    // WHEN the cookie has expired THEN nothing is returned
    const expired = await seal(session, secret, Math.floor(Date.now() / 1000) - 10);
    expect(await unseal(expired, secret)).toBeUndefined();
  });

  it("refuses short secrets", async () => {
    await expect(seal(session, "short", session.expiresAt)).rejects.toThrow("SESSION_SECRET");
  });
});

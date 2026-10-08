import { exchangeCode, isFresh, refreshTokens, TokenRequestError } from "./token-client";

const TOKEN_ENDPOINT = "http://localhost:9000/application/o/token/";
const now = () => Date.parse("2026-10-08T10:00:00Z");

function recordingFetch(status: number, body: unknown) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fetchImpl = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
  }) as unknown as typeof fetch;
  return { calls, fetchImpl };
}

describe("token client", () => {
  it("exchanges the code at the endpoint exactly as discovered, keeping the trailing slash", async () => {
    // GIVEN Authentik's token endpoint, which ends with a slash
    const { calls, fetchImpl } = recordingFetch(200, {
      access_token: "at",
      refresh_token: "rt",
      id_token: "it",
      token_type: "Bearer",
      expires_in: 900,
    });

    // WHEN the authorization code is exchanged
    const tokens = await exchangeCode(
      TOKEN_ENDPOINT,
      { clientId: "ugaddress-field", code: "c0de", redirectUri: "ugaddress://callback", codeVerifier: "v3rifier" },
      fetchImpl,
      now,
    );

    // THEN the request goes to .../token/ with the PKCE verifier and no client secret
    expect(calls[0]?.url).toBe(TOKEN_ENDPOINT);
    const form = new URLSearchParams(String(calls[0]?.init.body));
    expect(Object.fromEntries(form)).toEqual({
      grant_type: "authorization_code",
      client_id: "ugaddress-field",
      code: "c0de",
      redirect_uri: "ugaddress://callback",
      code_verifier: "v3rifier",
    });
    expect(tokens).toMatchObject({ accessToken: "at", refreshToken: "rt", expiresIn: 900, issuedAt: now() / 1000 });
  });

  it("refreshes and keeps the old refresh token when it is not rotated", async () => {
    const { calls, fetchImpl } = recordingFetch(200, { access_token: "at2", expires_in: 900 });
    const tokens = await refreshTokens(TOKEN_ENDPOINT, { clientId: "ugaddress-field", refreshToken: "rt" }, fetchImpl, now);
    expect(calls[0]?.url).toBe(TOKEN_ENDPOINT);
    expect(new URLSearchParams(String(calls[0]?.init.body)).get("grant_type")).toBe("refresh_token");
    expect(tokens.refreshToken).toBe("rt");
  });

  it("reports OAuth errors", async () => {
    const { fetchImpl } = recordingFetch(400, { error: "invalid_grant" });
    await expect(
      exchangeCode(TOKEN_ENDPOINT, { clientId: "c", code: "x", redirectUri: "ugaddress://callback", codeVerifier: "v" }, fetchImpl, now),
    ).rejects.toEqual(new TokenRequestError(400, "invalid_grant"));
  });

  it("treats tokens as stale shortly before they expire", () => {
    const issuedAt = now() / 1000;
    expect(isFresh({ accessToken: "a", tokenType: "Bearer", issuedAt, expiresIn: 900 }, now)).toBe(true);
    expect(isFresh({ accessToken: "a", tokenType: "Bearer", issuedAt: issuedAt - 870, expiresIn: 900 }, now)).toBe(false);
  });
});

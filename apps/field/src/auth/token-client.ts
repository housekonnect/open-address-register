/**
 * Token endpoint calls (code exchange and refresh).
 *
 * expo-auth-session's own request helper strips a trailing slash from every URL, but Authentik's endpoints end in
 * one (`/application/o/token/`), so its exchange and refresh calls fail with 404. These requests use the token
 * endpoint exactly as published in the discovery document.
 */

export interface Tokens {
  accessToken: string;
  refreshToken?: string | undefined;
  idToken?: string | undefined;
  tokenType: string;
  scope?: string | undefined;
  /** Lifetime of the access token in seconds. */
  expiresIn?: number | undefined;
  /** Seconds since the epoch when the tokens were issued. */
  issuedAt: number;
}

interface TokenEndpointResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  token_type?: string;
  scope?: string;
  expires_in?: number;
  error?: string;
  error_description?: string;
}

export class TokenRequestError extends Error {
  override readonly name = "TokenRequestError";

  constructor(
    readonly status: number,
    readonly code: string | undefined,
  ) {
    super(`Token request failed with status ${String(status)}${code ? ` (${code})` : ""}`);
  }
}

async function post(
  tokenEndpoint: string,
  params: Record<string, string>,
  fetchImpl: typeof fetch,
  now: () => number,
): Promise<Tokens> {
  const response = await fetchImpl(tokenEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body: new URLSearchParams(params).toString(),
  });
  const body = (await response.json().catch(() => ({}))) as Partial<TokenEndpointResponse>;
  if (!response.ok || !body.access_token) {
    throw new TokenRequestError(response.status, body.error);
  }
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    idToken: body.id_token,
    tokenType: body.token_type ?? "Bearer",
    scope: body.scope,
    expiresIn: body.expires_in,
    issuedAt: Math.floor(now() / 1000),
  };
}

/** Exchanges an authorization code (PKCE, public client: no secret). */
export function exchangeCode(
  tokenEndpoint: string,
  input: { clientId: string; code: string; redirectUri: string; codeVerifier: string },
  fetchImpl: typeof fetch = fetch,
  now: () => number = Date.now,
): Promise<Tokens> {
  return post(
    tokenEndpoint,
    {
      grant_type: "authorization_code",
      client_id: input.clientId,
      code: input.code,
      redirect_uri: input.redirectUri,
      code_verifier: input.codeVerifier,
    },
    fetchImpl,
    now,
  );
}

/** Uses a refresh token; keeps the old refresh token if the server does not rotate it. */
export async function refreshTokens(
  tokenEndpoint: string,
  input: { clientId: string; refreshToken: string },
  fetchImpl: typeof fetch = fetch,
  now: () => number = Date.now,
): Promise<Tokens> {
  const tokens = await post(
    tokenEndpoint,
    { grant_type: "refresh_token", client_id: input.clientId, refresh_token: input.refreshToken },
    fetchImpl,
    now,
  );
  return { ...tokens, refreshToken: tokens.refreshToken ?? input.refreshToken };
}

/** Whether the access token is still valid for at least `marginSeconds`. */
export function isFresh(tokens: Tokens, now: () => number = Date.now, marginSeconds = 60): boolean {
  if (tokens.expiresIn === undefined) return true;
  return tokens.issuedAt + tokens.expiresIn - marginSeconds > Math.floor(now() / 1000);
}

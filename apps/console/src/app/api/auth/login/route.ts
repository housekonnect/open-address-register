import { NextResponse } from "next/server";
import * as client from "openid-client";
import { oidcConfiguration, oidcSettings } from "@/lib/oidc";
import { cookieOptions, LOGIN_COOKIE, sealLogin } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Starts the OIDC authorization code flow with PKCE. */
export async function GET() {
  const settings = oidcSettings();
  const config = await oidcConfiguration();
  const codeVerifier = client.randomPKCECodeVerifier();
  const state = client.randomState();
  const authorizationUrl = client.buildAuthorizationUrl(config, {
    redirect_uri: settings.redirectUri,
    scope: settings.scope,
    code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
    code_challenge_method: "S256",
    state,
  });
  const response = NextResponse.redirect(authorizationUrl);
  response.cookies.set(LOGIN_COOKIE, await sealLogin({ state, codeVerifier, returnTo: "/" }), cookieOptions(600));
  return response;
}

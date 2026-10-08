import { type NextRequest, NextResponse } from "next/server";
import * as client from "openid-client";
import { oidcConfiguration, oidcSettings } from "@/lib/oidc";
import { cookieOptions, LOGIN_COOKIE, SESSION_COOKIE, sealSession, unsealLogin } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Completes the code flow: exchanges the code, validates the ID token and stores an encrypted session cookie. */
export async function GET(request: NextRequest) {
  const settings = oidcSettings();
  const transaction = await unsealLogin(request.cookies.get(LOGIN_COOKIE)?.value);
  if (!transaction) {
    return NextResponse.redirect(new URL("/?error=login", settings.publicUrl));
  }
  // Behind Docker the request URL has an internal host; the code was issued for the public redirect URI.
  const currentUrl = new URL(`/api/auth/callback${request.nextUrl.search}`, settings.publicUrl);
  try {
    const tokens = await client.authorizationCodeGrant(await oidcConfiguration(), currentUrl, {
      pkceCodeVerifier: transaction.codeVerifier,
      expectedState: transaction.state,
    });
    const claims = tokens.claims();
    if (!claims) throw new Error("No ID token returned");
    const session = {
      sub: claims.sub,
      name: typeof claims.name === "string" ? claims.name : claims.sub,
      custodian: typeof claims.custodian === "string" ? claims.custodian : null,
      groups: Array.isArray(claims.groups) ? claims.groups.filter((g): g is string => typeof g === "string") : [],
      accessToken: tokens.access_token,
      expiresAt: Math.floor(Date.now() / 1000) + (tokens.expiresIn() ?? 300),
    };
    const response = NextResponse.redirect(new URL(transaction.returnTo, settings.publicUrl));
    response.cookies.set(SESSION_COOKIE, await sealSession(session), cookieOptions(session.expiresAt - Math.floor(Date.now() / 1000)));
    response.cookies.delete(LOGIN_COOKIE);
    return response;
  } catch (error) {
    console.error("OIDC callback failed", error instanceof Error ? error.message : error);
    return NextResponse.redirect(new URL("/?error=login", settings.publicUrl));
  }
}

import "server-only";
import { lookup } from "node:dns";
import * as client from "openid-client";
import { Agent, fetch as undiciFetch } from "undici";

/** Public settings of the console's OIDC client (Authentik, authorization code flow with PKCE). */
export function oidcSettings() {
  const issuer = process.env.OIDC_ISSUER ?? "http://localhost:9000/application/o/ugaddress-console/";
  const publicUrl = (process.env.PUBLIC_URL ?? "http://localhost:3001").replace(/\/+$/, "");
  return {
    issuer,
    clientId: process.env.OIDC_CLIENT_ID ?? "ugaddress-console",
    clientSecret: process.env.OIDC_CLIENT_SECRET ?? "",
    publicUrl,
    redirectUri: `${publicUrl}/api/auth/callback`,
    scope: "openid profile ugaddress",
  };
}

/**
 * Inside Docker Compose the browser reaches Authentik at the public URL (e.g. localhost:9000) while the console's
 * server must use the Compose network. Resolving the public host name to OIDC_INTERNAL_HOST keeps the Host header,
 * and therefore the token issuer, identical for browser and server.
 */
function internalFetch(issuer: string): client.CustomFetch | undefined {
  const internalHost = process.env.OIDC_INTERNAL_HOST;
  if (!internalHost) return undefined;
  const publicHost = new URL(issuer).hostname;
  const dispatcher = new Agent({
    connect: {
      lookup: (hostname, options, callback) => lookup(hostname === publicHost ? internalHost : hostname, options, callback),
    },
  });
  return (url, options) => undiciFetch(url, { ...options, dispatcher } as Parameters<typeof undiciFetch>[1]) as unknown as Promise<Response>;
}

let configuration: Promise<client.Configuration> | undefined;

/** Discovers Authentik's endpoints once per process. */
export function oidcConfiguration(): Promise<client.Configuration> {
  if (!configuration) {
    const { issuer, clientId, clientSecret } = oidcSettings();
    const fetchImpl = internalFetch(issuer);
    configuration = client
      .discovery(new URL(issuer), clientId, clientSecret, undefined, {
        ...(fetchImpl ? { [client.customFetch]: fetchImpl } : {}),
        // Local development runs Authentik over plain HTTP; production issuers use HTTPS.
        execute: issuer.startsWith("http://") ? [client.allowInsecureRequests] : [],
      })
      .catch((error: unknown) => {
        configuration = undefined;
        throw error;
      });
  }
  return configuration;
}

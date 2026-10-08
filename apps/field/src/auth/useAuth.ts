import * as AuthSession from "expo-auth-session";
import * as SecureStore from "expo-secure-store";
import * as WebBrowser from "expo-web-browser";
import { useCallback, useEffect, useRef, useState } from "react";
import { config } from "../config";
import { exchangeCode, isFresh, refreshTokens, type Tokens } from "./token-client";

WebBrowser.maybeCompleteAuthSession();

const TOKEN_KEY = "ugaddress.tokens.v2";
const redirectUri = AuthSession.makeRedirectUri({ scheme: config.scheme, path: "callback" });
// expo-auth-session appends "/.well-known/openid-configuration"; Authentik issuers end with a slash.
const discoveryUrl = config.oidcIssuer.replace(/\/+$/, "");

async function loadTokens(): Promise<Tokens | undefined> {
  const raw = await SecureStore.getItemAsync(TOKEN_KEY);
  return raw ? (JSON.parse(raw) as Tokens) : undefined;
}

async function saveTokens(tokens: Tokens | undefined): Promise<void> {
  if (tokens) await SecureStore.setItemAsync(TOKEN_KEY, JSON.stringify(tokens));
  else await SecureStore.deleteItemAsync(TOKEN_KEY);
}

/**
 * OIDC authorization code flow with PKCE against Authentik (public client, no secret on the device).
 * expo-auth-session runs the browser step; the token calls use our own client (see token-client.ts) because
 * expo-auth-session strips the trailing slash Authentik's token endpoint needs.
 * Tokens live in the platform keystore (expo-secure-store) and are refreshed with the refresh token.
 */
export function useAuth() {
  const discovery = AuthSession.useAutoDiscovery(discoveryUrl);
  const [request, response, promptAsync] = AuthSession.useAuthRequest(
    { clientId: config.clientId, redirectUri, scopes: ["openid", "profile", "offline_access", "ugaddress"], usePKCE: true },
    discovery,
  );
  const [signedIn, setSignedIn] = useState<boolean>();
  const [exchangeFailed, setExchangeFailed] = useState(false);
  const tokens = useRef<Tokens | undefined>(undefined);

  useEffect(() => {
    void loadTokens().then((loaded) => {
      tokens.current = loaded;
      setSignedIn(loaded !== undefined);
    });
  }, []);

  useEffect(() => {
    const tokenEndpoint = discovery?.tokenEndpoint;
    if (response?.type !== "success" || !tokenEndpoint || !request?.codeVerifier) return;
    exchangeCode(tokenEndpoint, {
      clientId: config.clientId,
      code: response.params.code ?? "",
      redirectUri,
      codeVerifier: request.codeVerifier,
    })
      .then(async (exchanged) => {
        tokens.current = exchanged;
        await saveTokens(exchanged);
        setExchangeFailed(false);
        setSignedIn(true);
      })
      .catch((error: unknown) => {
        console.warn("Token exchange failed:", error instanceof Error ? error.message : error);
        setExchangeFailed(true);
      });
  }, [response, discovery, request]);

  /** Returns a fresh access token, refreshing it when needed; undefined if the user must sign in again. */
  const getAccessToken = useCallback(async (): Promise<string | undefined> => {
    const current = tokens.current;
    if (!current) return undefined;
    if (isFresh(current)) return current.accessToken;
    if (!current.refreshToken || !discovery?.tokenEndpoint) return undefined;
    try {
      const refreshed = await refreshTokens(discovery.tokenEndpoint, {
        clientId: config.clientId,
        refreshToken: current.refreshToken,
      });
      tokens.current = refreshed;
      await saveTokens(refreshed);
      return refreshed.accessToken;
    } catch {
      return undefined;
    }
  }, [discovery]);

  const signIn = useCallback(() => {
    setExchangeFailed(false);
    void promptAsync();
  }, [promptAsync]);
  const signOut = useCallback(async () => {
    tokens.current = undefined;
    await saveTokens(undefined);
    setSignedIn(false);
  }, []);

  const error = response?.type === "error" || exchangeFailed;
  return { ready: request !== null && signedIn !== undefined, signedIn: signedIn === true, error, signIn, signOut, getAccessToken };
}

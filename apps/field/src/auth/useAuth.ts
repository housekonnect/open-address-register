import * as AuthSession from "expo-auth-session";
import * as SecureStore from "expo-secure-store";
import * as WebBrowser from "expo-web-browser";
import { useCallback, useEffect, useRef, useState } from "react";
import { config } from "../config";

WebBrowser.maybeCompleteAuthSession();

const TOKEN_KEY = "ugaddress.tokens";
const redirectUri = AuthSession.makeRedirectUri({ scheme: config.scheme, path: "callback" });

async function loadTokens(): Promise<AuthSession.TokenResponse | undefined> {
  const raw = await SecureStore.getItemAsync(TOKEN_KEY);
  return raw ? new AuthSession.TokenResponse(JSON.parse(raw) as AuthSession.TokenResponseConfig) : undefined;
}

async function saveTokens(tokens: AuthSession.TokenResponse | undefined): Promise<void> {
  if (tokens) await SecureStore.setItemAsync(TOKEN_KEY, JSON.stringify(tokens.getRequestConfig()));
  else await SecureStore.deleteItemAsync(TOKEN_KEY);
}

/**
 * OIDC authorization code flow with PKCE against Authentik (public client, no secret on the device).
 * Tokens live in the platform keystore (expo-secure-store) and are refreshed with the refresh token.
 */
export function useAuth() {
  const discovery = AuthSession.useAutoDiscovery(config.oidcIssuer);
  const [request, response, promptAsync] = AuthSession.useAuthRequest(
    { clientId: config.clientId, redirectUri, scopes: ["openid", "profile", "offline_access", "ugaddress"], usePKCE: true },
    discovery,
  );
  const [signedIn, setSignedIn] = useState<boolean>();
  const [exchangeFailed, setExchangeFailed] = useState(false);
  const tokens = useRef<AuthSession.TokenResponse | undefined>(undefined);

  useEffect(() => {
    void loadTokens().then((loaded) => {
      tokens.current = loaded;
      setSignedIn(loaded !== undefined);
    });
  }, []);

  useEffect(() => {
    if (response?.type !== "success" || !discovery || !request?.codeVerifier) return;
    AuthSession.exchangeCodeAsync(
      { clientId: config.clientId, code: response.params.code ?? "", redirectUri, extraParams: { code_verifier: request.codeVerifier } },
      discovery,
    )
      .then(async (exchanged) => {
        tokens.current = exchanged;
        await saveTokens(exchanged);
        setExchangeFailed(false);
        setSignedIn(true);
      })
      .catch(() => setExchangeFailed(true));
  }, [response, discovery, request]);

  /** Returns a fresh access token, refreshing it when needed; undefined if the user must sign in again. */
  const getAccessToken = useCallback(async (): Promise<string | undefined> => {
    const current = tokens.current;
    if (!current) return undefined;
    if (current.shouldRefresh() && current.refreshToken && discovery) {
      try {
        const refreshed = await current.refreshAsync({ clientId: config.clientId }, discovery);
        tokens.current = refreshed;
        await saveTokens(refreshed);
        return refreshed.accessToken;
      } catch {
        return undefined;
      }
    }
    return AuthSession.TokenResponse.isTokenFresh(current) ? current.accessToken : undefined;
  }, [discovery]);

  const signIn = useCallback(() => void promptAsync(), [promptAsync]);
  const signOut = useCallback(async () => {
    tokens.current = undefined;
    await saveTokens(undefined);
    setSignedIn(false);
  }, []);

  const error = response?.type === "error" || exchangeFailed;
  return { ready: request !== null && signedIn !== undefined, signedIn: signedIn === true, error, signIn, signOut, getAccessToken };
}

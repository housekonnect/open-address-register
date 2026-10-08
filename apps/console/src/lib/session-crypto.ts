import { EncryptJWT, jwtDecrypt } from "jose";

/** What the console keeps about a signed-in custodian user. No personal data beyond the display name. */
export interface Session {
  /** Opaque subject from Authentik. */
  sub: string;
  /** Display name for the header. */
  name: string;
  /** Custodian code from the `custodian` claim. */
  custodian: string | null;
  groups: string[];
  accessToken: string;
  /** Expiry of the access token, seconds since the epoch. */
  expiresAt: number;
}

/** Short-lived state of a login in progress (PKCE verifier and state). */
export interface LoginTransaction {
  state: string;
  codeVerifier: string;
  returnTo: string;
}

async function keyFrom(secret: string): Promise<Uint8Array> {
  if (secret.length < 32) throw new Error("SESSION_SECRET must be at least 32 characters");
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret)));
}

/** Encrypts a value into a compact JWE (dir + A256GCM), so cookies can be neither read nor forged. */
export async function seal<T extends object>(value: T, secret: string, expiresAt: number): Promise<string> {
  return new EncryptJWT({ v: value })
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .setExpirationTime(expiresAt)
    .encrypt(await keyFrom(secret));
}

/** Decrypts a value sealed by {@link seal}; returns undefined if it is invalid, tampered with or expired. */
export async function unseal<T extends object>(token: string | undefined, secret: string): Promise<T | undefined> {
  if (!token) return undefined;
  try {
    const { payload } = await jwtDecrypt(token, await keyFrom(secret));
    return payload.v as T;
  } catch {
    return undefined;
  }
}

import "server-only";
import { cookies } from "next/headers";
import { seal, unseal, type LoginTransaction, type Session } from "./session-crypto";

export const SESSION_COOKIE = "ugaddress_session";
export const LOGIN_COOKIE = "ugaddress_login";

function secret(): string {
  const value = process.env.SESSION_SECRET;
  if (!value) throw new Error("SESSION_SECRET is not set");
  return value;
}

export function cookieOptions(maxAge: number) {
  const secure = (process.env.PUBLIC_URL ?? "").startsWith("https://");
  return { httpOnly: true, sameSite: "lax" as const, secure, path: "/", maxAge };
}

/** Returns the signed-in user, or undefined if there is no valid, unexpired session. */
export async function currentSession(): Promise<Session | undefined> {
  const session = await unseal<Session>((await cookies()).get(SESSION_COOKIE)?.value, secret());
  if (!session || session.expiresAt * 1000 <= Date.now()) return undefined;
  return session;
}

export async function sealSession(session: Session): Promise<string> {
  return seal(session, secret(), session.expiresAt);
}

export async function sealLogin(transaction: LoginTransaction): Promise<string> {
  return seal(transaction, secret(), Math.floor(Date.now() / 1000) + 600);
}

export async function unsealLogin(value: string | undefined): Promise<LoginTransaction | undefined> {
  return unseal<LoginTransaction>(value, secret());
}

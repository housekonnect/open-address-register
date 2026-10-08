import { NextResponse } from "next/server";
import { oidcSettings } from "@/lib/oidc";
import { SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Ends the console session. The Authentik session itself ends when the browser session does. */
export async function POST() {
  const response = NextResponse.redirect(new URL("/", oidcSettings().publicUrl), 303);
  response.cookies.delete(SESSION_COOKIE);
  return response;
}

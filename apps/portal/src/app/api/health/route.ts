export const dynamic = "force-dynamic";

/** Liveness for the container healthcheck: the Next.js server is up and answering. No dependencies are called. */
export function GET(): Response {
  return Response.json({ status: "UP" }, { headers: { "Cache-Control": "no-store" } });
}

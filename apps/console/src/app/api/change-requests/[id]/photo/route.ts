import { apiUrl } from "@/lib/api";
import { currentSession } from "@/lib/session";

export const dynamic = "force-dynamic";

/**
 * Evidence photo of a change request, fetched with the signed-in approver's token. The browser never talks to the
 * register API or the object store directly.
 */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }): Promise<Response> {
  const session = await currentSession();
  if (!session) return new Response(null, { status: 401 });
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) return new Response(null, { status: 404 });
  const upstream = await fetch(`${apiUrl()}/v1/change-requests/${id}/photo`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
    cache: "no-store",
  });
  if (!upstream.ok) return new Response(null, { status: upstream.status });
  return new Response(upstream.body, {
    headers: {
      "Content-Type": upstream.headers.get("Content-Type") ?? "application/octet-stream",
      "Cache-Control": "private, no-store",
    },
  });
}

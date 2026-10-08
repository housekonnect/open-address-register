import { buildMapStyle } from "@ugaddress/ui/lib/map-style";
import { mapHeaders, portalUrl, tilesPublicUrl } from "@/lib/basemap";

export const dynamic = "force-dynamic";

/** The one map style of the register, shared by the portal, the console and the field app (`?theme=dark`). */
export function GET(request: Request): Response {
  const theme = new URL(request.url).searchParams.get("theme") === "dark" ? "dark" : "light";
  const style = buildMapStyle({ tilesUrl: tilesPublicUrl(), assetsUrl: `${portalUrl()}/map`, theme });
  return new Response(JSON.stringify(style), { headers: mapHeaders("application/json", 300) });
}

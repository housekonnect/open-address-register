import { mapHeaders, readGlyphs } from "@/lib/basemap";

/** Glyph ranges of the basemap's fonts (protomaps/basemaps-assets, stored by `make basemap`). */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ fontstack: string; range: string }> },
): Promise<Response> {
  const { fontstack, range } = await params;
  const glyphs = await readGlyphs(fontstack, range);
  if (!glyphs) return new Response(null, { status: 404 });
  return new Response(new Uint8Array(glyphs), { headers: mapHeaders("application/x-protobuf", 86400) });
}

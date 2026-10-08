import { mapHeaders, readSprite } from "@/lib/basemap";

/** Sprite sheets of the basemap (protomaps/basemaps-assets, stored by `make basemap`). */
export async function GET(_request: Request, { params }: { params: Promise<{ file: string }> }): Promise<Response> {
  const { file } = await params;
  const sprite = await readSprite(file);
  if (!sprite) return new Response(null, { status: 404 });
  return new Response(new Uint8Array(sprite.body), { headers: mapHeaders(sprite.contentType, 86400) });
}

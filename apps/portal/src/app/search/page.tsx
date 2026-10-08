import { Alert, AlertDescription } from "@ugaddress/ui/components/alert";
import { Badge } from "@ugaddress/ui/components/badge";
import { Button } from "@ugaddress/ui/components/button";
import { Card, CardContent, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { SearchForm } from "@/components/search-form";
import { resolveReference, searchRegister } from "@/lib/api";
import { classifyReference, toApiRef } from "@/lib/reference";

export const dynamic = "force-dynamic";

function first(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value) ?? "";
}

/**
 * Search results. A national ID or reference that resolves goes straight to its address page; anything else is
 * searched with /v1/search (streets, addresses, places, partial IDs, typos).
 */
export default async function SearchPage({ searchParams }: { searchParams: Promise<{ q?: string | string[]; cursor?: string | string[] }> }) {
  const t = await getTranslations("search");
  const tAddress = await getTranslations("address");
  const params = await searchParams;
  const q = first(params.q).trim();
  const cursor = first(params.cursor) || undefined;

  const apiRef = cursor ? undefined : toApiRef(classifyReference(q));
  if (apiRef) {
    const resolved = await resolveReference(apiRef);
    if (resolved.status === "found") redirect(`/a/${resolved.resolution.object.nationalId}`);
  }

  const result = await searchRegister(q, cursor);
  return (
    <div className="flex flex-col gap-6">
      <SearchForm initialValue={q} />
      {result.status !== "ok" ? (
        <Alert variant={result.status === "unavailable" ? "destructive" : "default"}>
          <AlertDescription>{t(result.status)}</AlertDescription>
        </Alert>
      ) : result.page.items.length === 0 ? (
        <Alert>
          <AlertDescription>{t("empty", { q })}</AlertDescription>
        </Alert>
      ) : (
        <section className="flex flex-col gap-3" aria-labelledby="search-title">
          <h1 id="search-title" className="text-xl font-semibold">
            {t("title", { q })}
          </h1>
          <ol className="flex flex-col gap-3">
            {result.page.items.map((object) => (
              <li key={object.id}>
                <Card className="py-4">
                  <CardHeader>
                    <CardTitle className="text-lg">
                      <Link href={`/a/${object.nationalId}`} className="hover:underline">
                        {object.name ?? object.address?.lines[0] ?? object.displayId}
                      </Link>
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                    <Badge variant="secondary">{tAddress(`kind.${object.kind}`)}</Badge>
                    {object.nationalIdStatus === "demonstration" && <Badge variant="outline">{t("demonstration")}</Badge>}
                    {object.name && object.address && <span>{object.address.lines[0]}</span>}
                    <span className="font-mono text-foreground">{object.displayId}</span>
                    {object.address && <span>{object.address.lines.slice(1).join(" · ")}</span>}
                  </CardContent>
                </Card>
              </li>
            ))}
          </ol>
          {result.page.nextCursor && (
            <Button asChild variant="outline" className="self-start">
              <Link href={`/search?${new URLSearchParams({ q, cursor: result.page.nextCursor }).toString()}`}>{t("more")}</Link>
            </Button>
          )}
        </section>
      )}
    </div>
  );
}

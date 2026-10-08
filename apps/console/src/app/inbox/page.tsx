import type { ChangeRequestPage } from "@ugaddress/api-client";
import { Alert, AlertDescription } from "@ugaddress/ui/components/alert";
import { Badge } from "@ugaddress/ui/components/badge";
import { Button } from "@ugaddress/ui/components/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getFormatter, getTranslations } from "next-intl/server";
import { changesApi, isApprover } from "@/lib/api";
import { currentSession } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Approver inbox: submitted change requests in the approver's jurisdiction, oldest first. */
export default async function InboxPage({ searchParams }: { searchParams: Promise<{ cursor?: string }> }) {
  const t = await getTranslations("inbox");
  const format = await getFormatter();
  const session = await currentSession();
  if (!session) redirect("/");
  if (!isApprover(session)) {
    return (
      <Alert>
        <AlertDescription>{t("approversOnly")}</AlertDescription>
      </Alert>
    );
  }

  const { cursor } = await searchParams;
  let page: ChangeRequestPage | undefined;
  try {
    page = await changesApi(session).listChangeRequests({ state: "submitted", limit: 50, ...(cursor ? { cursor } : {}) });
  } catch (error) {
    console.error("Loading the inbox failed", error instanceof Error ? error.message : error);
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="text-muted-foreground">{t("intro")}</p>
      </div>
      {!page ? (
        <Alert variant="destructive">
          <AlertDescription>{t("unavailable")}</AlertDescription>
        </Alert>
      ) : page.items.length === 0 ? (
        <Alert>
          <AlertDescription>{t("empty")}</AlertDescription>
        </Alert>
      ) : (
        <ol className="flex flex-col gap-3">
          {page.items.map((request) => (
            <li key={request.id}>
              <Card className="py-4">
                <CardHeader>
                  <CardTitle className="text-lg">
                    <Link href={`/inbox/${request.id}`} className="hover:underline">
                      {request.target?.label ?? request.summary}
                    </Link>
                  </CardTitle>
                  <CardDescription>{request.summary}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                  <Badge variant="secondary">{t(`kind.${request.kind}`)}</Badge>
                  <Badge variant="outline">{t(`source.${request.source ?? "console"}`)}</Badge>
                  {request.evidence?.photo && <Badge variant="outline">{t("hasPhoto")}</Badge>}
                  {request.permissions?.reason === "own_request" && <Badge>{t("ownRequest")}</Badge>}
                  <span>{format.dateTime(request.createdAt, { dateStyle: "medium", timeStyle: "short" })}</span>
                </CardContent>
              </Card>
            </li>
          ))}
        </ol>
      )}
      {page?.nextCursor && (
        <Button asChild variant="outline" className="self-start">
          <Link href={`/inbox?${new URLSearchParams({ cursor: page.nextCursor }).toString()}`}>{t("more")}</Link>
        </Button>
      )}
    </div>
  );
}

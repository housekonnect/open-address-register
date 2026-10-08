import { ResponseError, type ChangeRequest } from "@ugaddress/api-client";
import { Alert, AlertDescription } from "@ugaddress/ui/components/alert";
import { Button } from "@ugaddress/ui/components/button";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { ChangeReview } from "@/components/change-review";
import { changesApi } from "@/lib/api";
import { currentSession } from "@/lib/session";

export const dynamic = "force-dynamic";

/** One change request with map preview, diff, evidence and the approve / return decision. */
export default async function ChangeRequestPage({ params }: { params: Promise<{ id: string }> }) {
  const t = await getTranslations("inbox");
  const session = await currentSession();
  if (!session) redirect("/");
  const { id } = await params;

  let request: ChangeRequest;
  try {
    request = await changesApi(session).getChangeRequest({ id });
  } catch (error) {
    if (error instanceof ResponseError && error.response.status === 404) notFound();
    const forbidden = error instanceof ResponseError && error.response.status === 403;
    return (
      <Alert variant={forbidden ? "default" : "destructive"}>
        <AlertDescription>{forbidden ? t("forbidden") : t("unavailable")}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <Button asChild variant="ghost" size="sm" className="self-start">
        <Link href="/inbox">
          <ArrowLeft aria-hidden />
          {t("back")}
        </Link>
      </Button>
      <ChangeReview request={request} styleUrl={process.env.MAP_STYLE_URL ?? "http://localhost:3000/map/style.json"} />
    </div>
  );
}

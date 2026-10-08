import { Alert, AlertDescription, AlertTitle } from "@ugaddress/ui/components/alert";
import { Button } from "@ugaddress/ui/components/button";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { resolveReference } from "@/lib/api";
import { classifyReference, toApiRef } from "@/lib/reference";

export default async function LookupPage({ searchParams }: { searchParams: Promise<{ ref?: string | string[] }> }) {
  const t = await getTranslations("lookup");
  const raw = (await searchParams).ref;
  const input = (Array.isArray(raw) ? raw[0] : raw) ?? "";
  const apiRef = toApiRef(classifyReference(input));

  const result = apiRef ? await resolveReference(apiRef) : ({ status: "invalid" } as const);
  if (result.status === "found") {
    redirect(`/a/${result.resolution.object.nationalId}`);
  }

  const [title, message] =
    result.status === "not-found"
      ? [t("notFoundTitle"), t("notFound", { ref: input })]
      : result.status === "invalid"
        ? [t("invalidTitle"), t("invalid", { ref: input })]
        : [t("notFoundTitle"), t("unavailable")];
  return (
    <div className="flex flex-col gap-4">
      <Alert variant={result.status === "unavailable" ? "destructive" : "default"}>
        <AlertTitle>{title}</AlertTitle>
        <AlertDescription>{message}</AlertDescription>
      </Alert>
      <Button asChild variant="outline" className="self-start">
        <Link href="/">{t("back")}</Link>
      </Button>
    </div>
  );
}

import { Badge } from "@ugaddress/ui/components/badge";
import { Button } from "@ugaddress/ui/components/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { Download } from "lucide-react";
import { getTranslations } from "next-intl/server";

const ENDPOINTS: { method: string; path: string; implemented: boolean }[] = [
  { method: "GET", path: "/v1/resolve?ref=", implemented: true },
  { method: "GET", path: "/v1/search?q=", implemented: true },
  { method: "GET", path: "/v1/reverse?lat=&lon=", implemented: true },
  { method: "GET", path: "/v1/objects/{id}", implemented: true },
  { method: "GET", path: "/v1/objects/{id}/history", implemented: true },
  { method: "POST", path: "/v1/change-requests", implemented: true },
  { method: "GET", path: "/v1/field/assignments", implemented: false },
  { method: "POST", path: "/v1/field/captures", implemented: true },
  { method: "GET", path: "/v1/changes?since=", implemented: false },
];

const CONVENTIONS = ["errors", "pagination", "etags", "idempotency", "privacy"] as const;

export default async function DevelopersPage() {
  const t = await getTranslations("developers");
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">{t("title")}</CardTitle>
        <CardDescription>{t("intro")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <Button asChild className="self-start">
          <a href="/openapi.yaml" download>
            <Download aria-hidden />
            {t("contract")}
          </a>
        </Button>
        <section className="flex flex-col gap-2">
          <h2 className="font-semibold">{t("conventions")}</h2>
          <ul className="list-disc pl-5 text-sm text-muted-foreground">
            {CONVENTIONS.map((key) => (
              <li key={key}>{t(`conventionList.${key}`)}</li>
            ))}
          </ul>
        </section>
        <section className="flex flex-col gap-2">
          <h2 className="font-semibold">{t("endpoints")}</h2>
          <ul className="flex flex-col gap-1 font-mono text-sm">
            {ENDPOINTS.map((e) => (
              <li key={`${e.method} ${e.path}`} className="flex flex-wrap items-center gap-2">
                <Badge variant="outline">{e.method}</Badge>
                <span>{e.path}</span>
                <Badge variant={e.implemented ? "default" : "secondary"}>{e.implemented ? t("implemented") : t("planned")}</Badge>
              </li>
            ))}
          </ul>
        </section>
      </CardContent>
    </Card>
  );
}

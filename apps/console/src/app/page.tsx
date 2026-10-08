import { Alert, AlertDescription } from "@ugaddress/ui/components/alert";
import { Button } from "@ugaddress/ui/components/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { LogIn } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { Workspace } from "@/components/workspace";
import { currentSession } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Initial view `[west, south, east, north]`; defaults to the streets of the synthetic demo district. */
function mapBounds(): [number, number, number, number] {
  const [west, south, east, north] = (process.env.MAP_BOUNDS ?? "32.572,0.342,32.606,0.351").split(",").map(Number);
  return [west ?? 32.572, south ?? 0.342, east ?? 32.606, north ?? 0.351];
}

export default async function ConsolePage({ searchParams }: { searchParams: Promise<{ error?: string }> }) {
  const t = await getTranslations("home");
  const session = await currentSession();
  const { error } = await searchParams;

  if (!session) {
    return (
      <Card className="mx-auto max-w-lg">
        <CardHeader>
          <CardTitle className="text-2xl">{t("title")}</CardTitle>
          <CardDescription>{t("intro")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{t("loginFailed")}</AlertDescription>
            </Alert>
          )}
          <Button asChild size="lg" className="self-start">
            <a href="/api/auth/login">
              <LogIn aria-hidden />
              {t("signIn")}
            </a>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Workspace
      styleUrl={process.env.MAP_STYLE_URL ?? "http://localhost:3000/map/style.json"}
      bounds={mapBounds()}
      custodian={session.custodian}
      canEdit={session.groups.includes("custodian-editor")}
    />
  );
}

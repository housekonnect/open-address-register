import { Alert, AlertDescription } from "@ugaddress/ui/components/alert";
import { Button } from "@ugaddress/ui/components/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { LogIn } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { Workspace } from "@/components/workspace";
import { currentSession } from "@/lib/session";

export const dynamic = "force-dynamic";

function mapCenter(): [number, number] {
  const [lon, lat] = (process.env.MAP_CENTER ?? "32.5900,0.3515").split(",").map(Number);
  return [lon ?? 32.59, lat ?? 0.3515];
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
      tilesUrl={process.env.TILES_URL ?? "http://localhost:3002"}
      center={mapCenter()}
      custodian={session.custodian}
      canEdit={session.groups.includes("custodian-editor")}
    />
  );
}

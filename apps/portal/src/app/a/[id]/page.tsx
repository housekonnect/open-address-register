import { Alert, AlertDescription } from "@ugaddress/ui/components/alert";
import { Badge } from "@ugaddress/ui/components/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { Separator } from "@ugaddress/ui/components/separator";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { QRCodeSVG } from "qrcode.react";
import { AddressMap } from "@/components/address-map";
import { resolveReference } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function AddressPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const t = await getTranslations("address");
  const result = await resolveReference(id);
  if (result.status !== "found") notFound();

  const object = result.resolution.object;
  const address = object.address;
  const demonstration = object.nationalIdStatus === "demonstration";
  const publicUrl = (process.env.PUBLIC_URL ?? "http://localhost:3000").replace(/\/+$/, "");
  const tilesUrl = process.env.TILES_URL ?? "http://localhost:3002";
  const coordinates = object.location?.coordinates;
  const center: [number, number] | undefined =
    coordinates && coordinates.length === 2 ? [coordinates[0] ?? 0, coordinates[1] ?? 0] : undefined;
  const hasHiddenEntrances = object.entrances.some((e) => e.residential && !e.location);

  return (
    <div className="grid gap-6 md:grid-cols-[1fr_auto]">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{t(`kind.${object.kind}`)}</Badge>
            {demonstration && <Badge variant="outline">{t("demonstration")}</Badge>}
          </div>
          <CardTitle className="text-2xl">{address?.lines[0] ?? object.name ?? object.displayId}</CardTitle>
          <CardDescription>
            {t("nationalId")}: <span className="font-mono text-foreground">{object.displayId}</span>
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {address && (
            <address className="not-italic leading-relaxed">
              {address.lines.map((line) => (
                <div key={line}>{line}</div>
              ))}
            </address>
          )}
          {center && <AddressMap tilesUrl={tilesUrl} center={center} nationalId={object.nationalId} />}
          <Separator />
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            {address?.postcode && (
              <>
                <dt className="text-muted-foreground">{t("postcode")}</dt>
                <dd>{address.postcode}</dd>
              </>
            )}
            {address && (
              <>
                <dt className="text-muted-foreground">{t("adminUnits")}</dt>
                <dd>{address.adminUnits.map((u) => u.name).join(" · ")}</dd>
              </>
            )}
            <dt className="text-muted-foreground">{t("entrances")}</dt>
            <dd>{t("entrancesCount", { count: object.entrances.length })}</dd>
            {object.aliases.length > 0 && (
              <>
                <dt className="text-muted-foreground">{t("aliases")}</dt>
                <dd className="font-mono">{object.aliases.map((a) => `${a.system}:${a.value}`).join(", ")}</dd>
              </>
            )}
          </dl>
          {hasHiddenEntrances && (
            <Alert>
              <AlertDescription>{t("residentialHidden")}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
      <Card className="h-fit items-center">
        <CardContent className="flex flex-col items-center gap-2">
          <QRCodeSVG
            value={`${publicUrl}/a/${object.nationalId}`}
            size={168}
            marginSize={2}
            title={t("qr")}
            className="rounded-md bg-card"
          />
          <p className="text-sm text-muted-foreground">{t("qrCaption")}</p>
        </CardContent>
      </Card>
    </div>
  );
}

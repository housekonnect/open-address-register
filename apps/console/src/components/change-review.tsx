"use client";

import type { ChangeRequest } from "@ugaddress/api-client";
import { Badge } from "@ugaddress/ui/components/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { RegisterMap } from "@ugaddress/ui/components/register-map";
import { Separator } from "@ugaddress/ui/components/separator";
import Image from "next/image";
import { useFormatter, useTranslations } from "next-intl";
import { approveChangeRequest, returnChangeRequest } from "@/app/inbox/actions";
import { DecisionPanel } from "./decision-panel";

function lonLat(point: { coordinates: number[] } | undefined): [number, number] | undefined {
  const [lon, lat] = point?.coordinates ?? [];
  return lon !== undefined && lat !== undefined ? [lon, lat] : undefined;
}

/** One change request for an approver: map preview, diff, evidence and the decision. */
export function ChangeReview({ request, styleUrl }: { request: ChangeRequest; styleUrl: string }) {
  const t = useTranslations("inbox");
  const format = useFormatter();
  const captured = lonLat(request.evidence?.location ?? request.proposal?.location);
  const center = lonLat(request.target?.location) ?? captured;

  return (
    <div className="grid gap-6 lg:grid-cols-[3fr_2fr]">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{t(`kind.${request.kind}`)}</Badge>
            <Badge variant="outline">{t(`source.${request.source ?? "console"}`)}</Badge>
            <Badge variant="outline">{t(`state.${request.state}`)}</Badge>
          </div>
          <CardTitle className="text-xl">{request.target?.label ?? request.summary}</CardTitle>
          <CardDescription>
            {request.target?.displayId && <span className="font-mono text-foreground">{request.target.displayId} · </span>}
            {t("proposedAt", { at: format.dateTime(request.createdAt, { dateStyle: "medium", timeStyle: "short" }) })}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {center ? (
            <RegisterMap
              styleUrl={styleUrl}
              center={center}
              zoom={18}
              highlightNationalId={request.target?.nationalId ?? undefined}
              marker={captured}
              label={t("mapLabel")}
            />
          ) : (
            <p className="text-sm text-muted-foreground">{t("noLocation")}</p>
          )}
          {captured && <p className="text-sm text-muted-foreground">{t("markerLegend")}</p>}
          <Separator />
          <section className="flex flex-col gap-2" aria-labelledby="summary-title">
            <h2 id="summary-title" className="font-semibold">
              {t("summary")}
            </h2>
            <p>{request.summary}</p>
          </section>
          <section className="flex flex-col gap-2" aria-labelledby="diff-title">
            <h2 id="diff-title" className="font-semibold">
              {t("diff.title")}
            </h2>
            {request.diff && request.diff.length > 0 ? (
              <dl className="grid grid-cols-[auto_1fr_1fr] gap-x-6 gap-y-2 text-sm">
                <dt className="sr-only">{t("diff.field")}</dt>
                <dd className="col-start-2 text-muted-foreground">{t("diff.current")}</dd>
                <dd className="text-muted-foreground">{t("diff.proposed")}</dd>
                {request.diff.map((d) => (
                  <div key={d.field} className="contents">
                    <dt className="font-medium">{t(`diff.fields.${d.field}`)}</dt>
                    <dd className="font-mono text-muted-foreground line-through">{d.current ?? t("diff.none")}</dd>
                    <dd className="font-mono font-semibold">{d.proposed ?? t("diff.none")}</dd>
                  </div>
                ))}
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">{t("diff.empty")}</p>
            )}
          </section>
        </CardContent>
      </Card>
      <div className="flex flex-col gap-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("evidence.title")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            {request.evidence?.photo ? (
              <Image
                src={`/api/change-requests/${request.id}/photo`}
                alt={t("evidence.photoAlt")}
                width={640}
                height={480}
                unoptimized
                className="h-auto w-full rounded-md border"
              />
            ) : (
              <p className="text-muted-foreground">{t("evidence.noPhoto")}</p>
            )}
            {request.evidence?.photoSha256 && (
              <p className="break-all">
                {t("evidence.sha256")}: <span className="font-mono text-xs">{request.evidence.photoSha256}</span>
              </p>
            )}
            {captured && (
              <p>
                {t("evidence.location")}: <span className="font-mono">{`${captured[1].toFixed(6)}, ${captured[0].toFixed(6)}`}</span>
              </p>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("decision.title")}</CardTitle>
            {request.decisionReason && <CardDescription>{t("decision.returnedWith", { reason: request.decisionReason })}</CardDescription>}
          </CardHeader>
          <CardContent>
            <DecisionPanel
              permissions={request.permissions ?? { decide: false, reason: "not_approver" }}
              onApprove={approveChangeRequest.bind(null, request.id)}
              onReturn={returnChangeRequest.bind(null, request.id)}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

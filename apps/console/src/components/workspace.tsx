"use client";

import { Alert, AlertDescription, AlertTitle } from "@ugaddress/ui/components/alert";
import { Badge } from "@ugaddress/ui/components/badge";
import { Button } from "@ugaddress/ui/components/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { Input } from "@ugaddress/ui/components/input";
import { Label } from "@ugaddress/ui/components/label";
import { RegisterMap, type MapSelection } from "@ugaddress/ui/components/register-map";
import { Textarea } from "@ugaddress/ui/components/textarea";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { submitCorrection, type SubmitResult } from "@/app/actions";
import { newIdempotencyKey } from "@/lib/correction";

interface WorkspaceProps {
  styleUrl: string;
  bounds: [number, number, number, number];
  custodian: string | null;
  canEdit: boolean;
}

/** Map of the custodian's streets with a correction form for the selected street or building. */
export function Workspace({ styleUrl, bounds, custodian, canEdit }: WorkspaceProps) {
  const t = useTranslations("workspace");
  const [selection, setSelection] = useState<MapSelection>();
  const [summary, setSummary] = useState("");
  const [houseNumber, setHouseNumber] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
  const [result, setResult] = useState<SubmitResult>();
  const [pending, startTransition] = useTransition();

  function select(next: MapSelection) {
    setSelection(next);
    setResult(undefined);
    setIdempotencyKey(newIdempotencyKey());
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selection) return;
    startTransition(async () => {
      const outcome = await submitCorrection({
        idempotencyKey,
        targetType: selection.layer,
        targetId: selection.id,
        summary,
        proposedHouseNumber: selection.layer === "building" && houseNumber ? houseNumber : undefined,
      });
      setResult(outcome);
      if (outcome.status === "submitted") {
        setSummary("");
        setHouseNumber("");
        setIdempotencyKey(newIdempotencyKey());
      }
    });
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
      <Card>
        <CardHeader>
          <CardTitle>{t("mapTitle")}</CardTitle>
          <CardDescription>{custodian ? t("mapIntro", { custodian }) : t("noCustodian")}</CardDescription>
        </CardHeader>
        <CardContent>
          <RegisterMap
            styleUrl={styleUrl}
            bounds={bounds}
            ownCustodian={custodian ?? undefined}
            highlightNationalId={selection?.nationalId}
            onSelect={select}
            label={t("mapLabel")}
            className="h-[28rem]"
          />
          <p className="mt-2 text-sm text-muted-foreground">{t("legend")}</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t("formTitle")}</CardTitle>
          <CardDescription>{selection ? t("selected") : t("selectHint")}</CardDescription>
        </CardHeader>
        <CardContent>
          {selection && (
            <form onSubmit={submit} className="flex flex-col gap-4">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary">{t(`layer.${selection.layer}`)}</Badge>
                <span className="font-medium">{selection.name ?? selection.nationalId ?? selection.id}</span>
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="summary">{t("summary")}</Label>
                <Textarea
                  id="summary"
                  value={summary}
                  onChange={(e) => setSummary(e.target.value)}
                  placeholder={t("summaryPlaceholder")}
                  minLength={3}
                  maxLength={500}
                  required
                />
                <p className="text-sm text-muted-foreground">{t("noPersonalData")}</p>
              </div>
              {selection.layer === "building" && (
                <div className="flex flex-col gap-2">
                  <Label htmlFor="house-number">{t("houseNumber")}</Label>
                  <Input id="house-number" value={houseNumber} onChange={(e) => setHouseNumber(e.target.value.toUpperCase())} maxLength={6} />
                </div>
              )}
              <Button type="submit" disabled={!canEdit || pending}>
                {pending ? t("submitting") : t("submit")}
              </Button>
              {!canEdit && <p className="text-sm text-muted-foreground">{t("editorsOnly")}</p>}
            </form>
          )}
          {result?.status === "submitted" && (
            <Alert className="mt-4">
              <AlertTitle>{t("submittedTitle")}</AlertTitle>
              <AlertDescription>
                {t("submitted", { state: result.state })} <span className="font-mono">{result.id}</span>
              </AlertDescription>
            </Alert>
          )}
          {result?.status === "error" && (
            <Alert variant="destructive" className="mt-4">
              <AlertTitle>{t("errorTitle")}</AlertTitle>
              <AlertDescription>{t(`error.${result.reason}`)}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

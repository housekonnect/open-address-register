"use client";

import type { ChangePermissions } from "@ugaddress/api-client";
import { Alert, AlertDescription, AlertTitle } from "@ugaddress/ui/components/alert";
import { Button } from "@ugaddress/ui/components/button";
import { Label } from "@ugaddress/ui/components/label";
import { Textarea } from "@ugaddress/ui/components/textarea";
import { Check, Undo2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { DecisionResult } from "@/app/inbox/actions";
import { newIdempotencyKey } from "@/lib/correction";

interface DecisionPanelProps {
  /** What the signed-in user may do, as computed by the register API. */
  permissions: ChangePermissions;
  onApprove: (idempotencyKey: string) => Promise<DecisionResult>;
  onReturn: (reason: string, idempotencyKey: string) => Promise<DecisionResult>;
}

/**
 * Approve, or return with a written reason. Both are disabled, with the reason shown, when the API says the user
 * may not decide: in particular when they proposed the change themselves (four-eyes rule).
 */
export function DecisionPanel({ permissions, onApprove, onReturn }: DecisionPanelProps) {
  const t = useTranslations("inbox.decision");
  const reasonId = useId();
  const hintId = useId();
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<DecisionResult>();
  const [pending, startTransition] = useTransition();
  // One key per decision attempt: a retried request after a network error is not applied twice.
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);

  const blocked = !permissions.decide || result?.status === "decided";
  const reasonValid = reason.trim().length >= 3 && reason.trim().length <= 500;

  function run(action: () => Promise<DecisionResult>) {
    startTransition(async () => {
      const outcome = await action();
      setResult(outcome);
      if (outcome.status === "error") setIdempotencyKey(newIdempotencyKey());
    });
  }

  return (
    <div className="flex flex-col gap-4">
      {!permissions.decide && (
        <Alert>
          <AlertTitle>{t("blockedTitle")}</AlertTitle>
          <AlertDescription id={hintId}>{t(`blocked.${permissions.reason ?? "not_approver"}`)}</AlertDescription>
        </Alert>
      )}
      <div className="flex flex-col gap-2">
        <Label htmlFor={reasonId}>{t("reason")}</Label>
        <Textarea
          id={reasonId}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder={t("reasonPlaceholder")}
          maxLength={500}
          disabled={blocked || pending}
        />
        <p className="text-sm text-muted-foreground">{t("noPersonalData")}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          disabled={blocked || pending}
          aria-describedby={permissions.decide ? undefined : hintId}
          onClick={() => run(() => onApprove(idempotencyKey))}
        >
          <Check aria-hidden />
          {t("approve")}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={blocked || pending || !reasonValid}
          aria-describedby={permissions.decide ? undefined : hintId}
          onClick={() => run(() => onReturn(reason, idempotencyKey))}
        >
          <Undo2 aria-hidden />
          {t("return")}
        </Button>
      </div>
      {result?.status === "decided" && (
        <Alert>
          <AlertTitle>{t("decidedTitle")}</AlertTitle>
          <AlertDescription>{t("decided", { state: result.state })}</AlertDescription>
        </Alert>
      )}
      {result?.status === "error" && (
        <Alert variant="destructive">
          <AlertTitle>{t("errorTitle")}</AlertTitle>
          <AlertDescription>{t(`error.${result.reason}`)}</AlertDescription>
        </Alert>
      )}
    </div>
  );
}

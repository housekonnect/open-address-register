"use client";

import { Button } from "@ugaddress/ui/components/button";
import { Input } from "@ugaddress/ui/components/input";
import { Label } from "@ugaddress/ui/components/label";
import { Search } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { classifyReference } from "@/lib/reference";

/**
 * Search box. Validates national IDs as the user types (the Damm check digit catches typos before any request),
 * and works without JavaScript as a plain GET form.
 */
export function SearchForm({ initialValue = "" }: { initialValue?: string }) {
  const t = useTranslations("home");
  const [value, setValue] = useState(initialValue);
  const inputId = useId();
  const hintId = useId();
  const reference = classifyReference(value);
  const looksLikeId = /^[\sD-EMOdemo0-9-]+$/.test(value) && value.replace(/\D/g, "").length >= 11;

  let hint: string | undefined;
  if (reference.kind === "invalid" && reference.reason === "checkDigit") hint = t("hint.checkDigit");
  else if (reference.kind === "invalid" && looksLikeId) hint = t("hint.format");
  else if (reference.kind === "nationalId") hint = t("hint.valid");

  const invalid = reference.kind === "invalid" && hint !== undefined;
  return (
    <form action="/lookup" method="get" className="flex flex-col gap-2">
      <Label htmlFor={inputId}>{t("label")}</Label>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Input
          id={inputId}
          name="ref"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={t("placeholder")}
          autoComplete="off"
          inputMode="text"
          aria-invalid={invalid}
          aria-describedby={hint ? hintId : undefined}
          className="font-mono text-lg"
          required
        />
        <Button type="submit" size="lg" disabled={reference.kind === "empty" || invalid}>
          <Search aria-hidden />
          {t("submit")}
        </Button>
      </div>
      <p id={hintId} className={invalid ? "text-sm text-destructive" : "text-sm text-muted-foreground"} aria-live="polite">
        {hint ?? " "}
      </p>
    </form>
  );
}

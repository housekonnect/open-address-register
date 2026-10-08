"use client";

import { RegisterMap } from "@ugaddress/ui/components/register-map";
import { useTranslations } from "next-intl";

/** Map of an address, highlighting its building. */
export function AddressMap(props: { tilesUrl: string; center: [number, number]; nationalId: string }) {
  const t = useTranslations("address");
  return <RegisterMap tilesUrl={props.tilesUrl} center={props.center} highlightNationalId={props.nationalId} zoom={17} label={t("map")} />;
}

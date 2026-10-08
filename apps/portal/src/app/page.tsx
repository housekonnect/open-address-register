import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@ugaddress/ui/components/card";
import { getTranslations } from "next-intl/server";
import { SearchForm } from "@/components/search-form";

export default async function HomePage() {
  const t = await getTranslations("home");
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">{t("title")}</CardTitle>
        <CardDescription>{t("intro")}</CardDescription>
      </CardHeader>
      <CardContent>
        <SearchForm />
      </CardContent>
    </Card>
  );
}

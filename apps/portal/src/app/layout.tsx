import "./globals.css";
import { Separator } from "@ugaddress/ui/components/separator";
import { MapPinned } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import type { ReactNode } from "react";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("app");
  return { title: t("name"), description: t("tagline") };
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const locale = await getLocale();
  const t = await getTranslations("app");
  return (
    <html lang={locale}>
      <body className="min-h-dvh">
        <NextIntlClientProvider>
          <header className="border-b bg-card">
            <div className="mx-auto flex max-w-4xl flex-wrap items-center justify-between gap-3 px-4 py-3">
              <Link href="/" className="flex items-center gap-2 font-semibold text-foreground">
                <MapPinned aria-hidden className="size-5 text-primary" />
                {t("name")}
              </Link>
              <nav className="flex gap-4 text-sm text-muted-foreground">
                <Link href="/" className="hover:text-foreground">
                  {t("nav.lookup")}
                </Link>
                <Link href="/developers" className="hover:text-foreground">
                  {t("nav.developers")}
                </Link>
              </nav>
            </div>
          </header>
          <main className="mx-auto max-w-4xl px-4 py-8">{children}</main>
          <Separator />
          <footer className="mx-auto max-w-4xl px-4 py-6 text-sm text-muted-foreground">{t("footer")}</footer>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

import "./globals.css";
import { Badge } from "@ugaddress/ui/components/badge";
import { Button } from "@ugaddress/ui/components/button";
import { LogOut, MapPinned } from "lucide-react";
import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import type { ReactNode } from "react";
import { currentSession } from "@/lib/session";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("app");
  return { title: t("name") };
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const locale = await getLocale();
  const t = await getTranslations("app");
  const session = await currentSession();
  return (
    <html lang={locale}>
      <body className="min-h-dvh">
        <NextIntlClientProvider>
          <header className="border-b bg-card">
            <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
              <span className="flex items-center gap-2 font-semibold">
                <MapPinned aria-hidden className="size-5 text-primary" />
                {t("name")}
              </span>
              {session && (
                <div className="flex flex-wrap items-center gap-3 text-sm">
                  <span>{session.name}</span>
                  {session.custodian && <Badge variant="secondary">{session.custodian}</Badge>}
                  {session.groups.map((group) => (
                    <Badge key={group} variant="outline">
                      {group}
                    </Badge>
                  ))}
                  <form action="/api/auth/logout" method="post">
                    <Button type="submit" variant="ghost" size="sm">
                      <LogOut aria-hidden />
                      {t("signOut")}
                    </Button>
                  </form>
                </div>
              )}
            </div>
          </header>
          <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

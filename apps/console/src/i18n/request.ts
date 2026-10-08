import { getRequestConfig } from "next-intl/server";

/** English first; Luganda will be added as a second locale. */
export const DEFAULT_LOCALE = "en";

export default getRequestConfig(async () => ({
  locale: DEFAULT_LOCALE,
  messages: (await import(`../../messages/${DEFAULT_LOCALE}.json`)).default,
}));

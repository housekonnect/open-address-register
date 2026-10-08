/** Runtime configuration from EXPO_PUBLIC_* variables (see .env.example). */
export const config = {
  apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080",
  oidcIssuer: process.env.EXPO_PUBLIC_OIDC_ISSUER ?? "http://localhost:9000/application/o/ugaddress-field/",
  tilesUrl: process.env.EXPO_PUBLIC_TILES_URL ?? "http://localhost:3002",
  clientId: "ugaddress-field",
  scheme: "ugaddress",
  /** Initial map centre when no location is known yet: the synthetic demo area. */
  defaultCenter: [32.59, 0.3515] as [number, number],
};

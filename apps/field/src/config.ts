/** Runtime configuration from EXPO_PUBLIC_* variables (see .env.example). */
export const config = {
  apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080",
  oidcIssuer: process.env.EXPO_PUBLIC_OIDC_ISSUER ?? "http://localhost:9000/application/o/ugaddress-field/",
  /** The one map style of the register, served by the portal (same URL as the web apps). */
  mapStyleUrl: process.env.EXPO_PUBLIC_MAP_STYLE_URL ?? "http://localhost:3000/map/style.json",
  clientId: "ugaddress-field",
  scheme: "ugaddress",
  /** Initial map centre: the streets of the synthetic demo area. "My location" moves the map to the device. */
  defaultCenter: [32.589, 0.3466] as [number, number],
};

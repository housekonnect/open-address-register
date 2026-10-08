/** Runtime configuration from EXPO_PUBLIC_* variables (see .env.example). */
export const config = {
  apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080",
  oidcIssuer: process.env.EXPO_PUBLIC_OIDC_ISSUER ?? "http://localhost:9000/application/o/ugaddress-field/",
  /** The one map style of the register, served by the portal (same URL as the web apps). */
  mapStyleUrl: process.env.EXPO_PUBLIC_MAP_STYLE_URL ?? "http://localhost:3000/map/style.json",
  clientId: "ugaddress-field",
  scheme: "ugaddress",
  /**
   * Initial map view `[west, south, east, north]`: the streets of the synthetic demo area, as in the console.
   * "My location" moves the map to the device.
   */
  defaultBounds: [32.572, 0.342, 32.606, 0.351] as [number, number, number, number],
};

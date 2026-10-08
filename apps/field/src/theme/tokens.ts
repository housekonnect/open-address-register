import { useColorScheme } from "react-native";

/** Semantic colour tokens, mirroring packages/ui (React Native cannot use the web stylesheet). */
export interface Tokens {
  background: string;
  foreground: string;
  card: string;
  muted: string;
  mutedForeground: string;
  primary: string;
  primaryForeground: string;
  border: string;
  destructive: string;
  success: string;
  mapBackground: string;
  mapStreet: string;
  mapBuilding: string;
  mapBuildingOutline: string;
}

const light: Tokens = {
  background: "hsl(40, 33%, 98%)",
  foreground: "hsl(150, 25%, 12%)",
  card: "hsl(0, 0%, 100%)",
  muted: "hsl(40, 20%, 93%)",
  mutedForeground: "hsl(150, 10%, 38%)",
  primary: "hsl(152, 55%, 26%)",
  primaryForeground: "hsl(40, 33%, 98%)",
  border: "hsl(40, 15%, 85%)",
  destructive: "hsl(4, 70%, 46%)",
  success: "hsl(152, 55%, 30%)",
  mapBackground: "hsl(40, 30%, 95%)",
  mapStreet: "hsl(150, 10%, 55%)",
  mapBuilding: "hsl(40, 15%, 78%)",
  mapBuildingOutline: "hsl(40, 10%, 60%)",
};

const dark: Tokens = {
  background: "hsl(150, 20%, 8%)",
  foreground: "hsl(40, 25%, 94%)",
  card: "hsl(150, 18%, 11%)",
  muted: "hsl(150, 12%, 16%)",
  mutedForeground: "hsl(40, 10%, 68%)",
  primary: "hsl(152, 45%, 52%)",
  primaryForeground: "hsl(150, 25%, 8%)",
  border: "hsl(150, 10%, 22%)",
  destructive: "hsl(4, 70%, 58%)",
  success: "hsl(152, 45%, 52%)",
  mapBackground: "hsl(150, 15%, 12%)",
  mapStreet: "hsl(150, 8%, 45%)",
  mapBuilding: "hsl(150, 8%, 26%)",
  mapBuildingOutline: "hsl(150, 8%, 40%)",
};

export function useTokens(): Tokens {
  return useColorScheme() === "dark" ? dark : light;
}

import js from "@eslint/js";
import tseslint from "typescript-eslint";

// Generated code is not linted: it is regenerated, never edited.
export default tseslint.config(
  { ignores: ["node_modules", "dist", "src/generated"] },
  js.configs.recommended,
  ...tseslint.configs.strict,
);

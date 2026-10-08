const expoConfig = require("eslint-config-expo/flat");

module.exports = [
  { ignores: ["node_modules/**", ".expo/**", "android/**", "ios/**"] },
  ...expoConfig,
  // eslint-plugin-react's version auto-detection uses an API removed in ESLint 10; state the version instead.
  { settings: { react: { version: "19.2" } } },
];

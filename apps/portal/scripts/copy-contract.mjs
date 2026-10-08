// Publishes the API contract on the developer page (/openapi.yaml). The copy is generated, not committed.
import { copyFileSync } from "node:fs";

copyFileSync(new URL("../../../contracts/register.v1.yaml", import.meta.url), new URL("../public/openapi.yaml", import.meta.url));

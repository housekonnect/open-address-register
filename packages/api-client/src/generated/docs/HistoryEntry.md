# HistoryEntry

## Properties

| Name              | Type                      |
| ----------------- | ------------------------- |
| `recordedAt`      | Date                      |
| `operation`       | string                    |
| `version`         | number                    |
| `lifecycle`       | [Lifecycle](Lifecycle.md) |
| `validFrom`       | Date                      |
| `validTo`         | Date                      |
| `changeRequestId` | string                    |

## Example

```typescript
import type { HistoryEntry } from "";

// TODO: Update the object below with actual values
const example = {
  recordedAt: null,
  operation: null,
  version: null,
  lifecycle: null,
  validFrom: null,
  validTo: null,
  changeRequestId: null,
} satisfies HistoryEntry;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as HistoryEntry;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

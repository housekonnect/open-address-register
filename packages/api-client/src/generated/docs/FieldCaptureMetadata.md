# FieldCaptureMetadata

## Properties

| Name             | Type                        |
| ---------------- | --------------------------- |
| `capturedAt`     | Date                        |
| `location`       | [Point](Point.md)           |
| `accuracyMeters` | number                      |
| `kind`           | [ObjectKind](ObjectKind.md) |
| `targetObjectId` | string                      |
| `note`           | string                      |

## Example

```typescript
import type { FieldCaptureMetadata } from "";

// TODO: Update the object below with actual values
const example = {
  capturedAt: null,
  location: null,
  accuracyMeters: null,
  kind: null,
  targetObjectId: null,
  note: null,
} satisfies FieldCaptureMetadata;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FieldCaptureMetadata;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

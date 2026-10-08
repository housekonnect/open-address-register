# FieldCaptureUpload

## Properties

| Name       | Type                                            |
| ---------- | ----------------------------------------------- |
| `metadata` | [FieldCaptureMetadata](FieldCaptureMetadata.md) |
| `photo`    | Blob                                            |

## Example

```typescript
import type { FieldCaptureUpload } from "";

// TODO: Update the object below with actual values
const example = {
  metadata: null,
  photo: null,
} satisfies FieldCaptureUpload;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FieldCaptureUpload;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

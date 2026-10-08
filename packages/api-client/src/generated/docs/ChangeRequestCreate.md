# ChangeRequestCreate

## Properties

| Name                  | Type                        |
| --------------------- | --------------------------- |
| `kind`                | [ChangeKind](ChangeKind.md) |
| `targetObjectId`      | string                      |
| `thoroughfareId`      | string                      |
| `summary`             | string                      |
| `proposedLocation`    | [Point](Point.md)           |
| `proposedHouseNumber` | string                      |

## Example

```typescript
import type { ChangeRequestCreate } from "";

// TODO: Update the object below with actual values
const example = {
  kind: null,
  targetObjectId: null,
  thoroughfareId: null,
  summary: null,
  proposedLocation: null,
  proposedHouseNumber: null,
} satisfies ChangeRequestCreate;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ChangeRequestCreate;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

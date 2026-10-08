# Change

## Properties

| Name         | Type   |
| ------------ | ------ |
| `sequence`   | number |
| `objectId`   | string |
| `operation`  | string |
| `recordedAt` | Date   |

## Example

```typescript
import type { Change } from "";

// TODO: Update the object below with actual values
const example = {
  sequence: null,
  objectId: null,
  operation: null,
  recordedAt: null,
} satisfies Change;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Change;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# Resolution

## Properties

| Name        | Type                                      |
| ----------- | ----------------------------------------- |
| `matchedBy` | string                                    |
| `object`    | [AddressableObject](AddressableObject.md) |

## Example

```typescript
import type { Resolution } from "";

// TODO: Update the object below with actual values
const example = {
  matchedBy: null,
  object: null,
} satisfies Resolution;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Resolution;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

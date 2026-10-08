# ChangePermissions

What the caller may do with this change request, computed by the server.

## Properties

| Name     | Type    |
| -------- | ------- |
| `decide` | boolean |
| `reason` | string  |

## Example

```typescript
import type { ChangePermissions } from "";

// TODO: Update the object below with actual values
const example = {
  decide: null,
  reason: null,
} satisfies ChangePermissions;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ChangePermissions;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# ChangeTarget

What the change refers to, for display and the map preview.

## Properties

| Name         | Type              |
| ------------ | ----------------- |
| `type`       | string            |
| `label`      | string            |
| `nationalId` | string            |
| `displayId`  | string            |
| `location`   | [Point](Point.md) |

## Example

```typescript
import type { ChangeTarget } from "";

// TODO: Update the object below with actual values
const example = {
  type: null,
  label: null,
  nationalId: null,
  displayId: null,
  location: null,
} satisfies ChangeTarget;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ChangeTarget;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

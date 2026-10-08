# Entrance

## Properties

| Name          | Type              |
| ------------- | ----------------- |
| `id`          | string            |
| `nationalId`  | string            |
| `displayId`   | string            |
| `residential` | boolean           |
| `main`        | boolean           |
| `location`    | [Point](Point.md) |

## Example

```typescript
import type { Entrance } from "";

// TODO: Update the object below with actual values
const example = {
  id: null,
  nationalId: null,
  displayId: null,
  residential: null,
  main: null,
  location: null,
} satisfies Entrance;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Entrance;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

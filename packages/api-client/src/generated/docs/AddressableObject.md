# AddressableObject

## Properties

| Name               | Type                                    |
| ------------------ | --------------------------------------- |
| `id`               | string                                  |
| `nationalId`       | string                                  |
| `displayId`        | string                                  |
| `nationalIdStatus` | [NationalIdStatus](NationalIdStatus.md) |
| `kind`             | [ObjectKind](ObjectKind.md)             |
| `lifecycle`        | [Lifecycle](Lifecycle.md)               |
| `name`             | string                                  |
| `location`         | [Point](Point.md)                       |
| `address`          | [Address](Address.md)                   |
| `entrances`        | [Array&lt;Entrance&gt;](Entrance.md)    |
| `aliases`          | [Array&lt;Alias&gt;](Alias.md)          |
| `version`          | number                                  |
| `validFrom`        | Date                                    |

## Example

```typescript
import type { AddressableObject } from "";

// TODO: Update the object below with actual values
const example = {
  id: null,
  nationalId: null,
  displayId: null,
  nationalIdStatus: null,
  kind: null,
  lifecycle: null,
  name: null,
  location: null,
  address: null,
  entrances: null,
  aliases: null,
  version: null,
  validFrom: null,
} satisfies AddressableObject;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AddressableObject;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

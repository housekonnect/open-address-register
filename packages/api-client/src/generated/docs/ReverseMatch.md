# ReverseMatch

One match of a reverse lookup. `precision` is `street` for public callers (no `object`) and `object` for partners.

## Properties

| Name             | Type                                         |
| ---------------- | -------------------------------------------- |
| `precision`      | string                                       |
| `distanceMeters` | number                                       |
| `thoroughfare`   | [ThoroughfareRef](ThoroughfareRef.md)        |
| `postcode`       | string                                       |
| `adminUnits`     | [Array&lt;AdminUnitRef&gt;](AdminUnitRef.md) |
| `object`         | [AddressableObject](AddressableObject.md)    |

## Example

```typescript
import type { ReverseMatch } from "";

// TODO: Update the object below with actual values
const example = {
  precision: null,
  distanceMeters: null,
  thoroughfare: null,
  postcode: null,
  adminUnits: null,
  object: null,
} satisfies ReverseMatch;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReverseMatch;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

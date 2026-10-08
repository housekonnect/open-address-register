# ChangeRequest

## Properties

| Name             | Type                                        |
| ---------------- | ------------------------------------------- |
| `id`             | string                                      |
| `kind`           | [ChangeKind](ChangeKind.md)                 |
| `state`          | [ChangeRequestState](ChangeRequestState.md) |
| `summary`        | string                                      |
| `targetObjectId` | string                                      |
| `thoroughfareId` | string                                      |
| `adminUnitId`    | string                                      |
| `source`         | string                                      |
| `createdAt`      | Date                                        |
| `decidedAt`      | Date                                        |
| `decisionReason` | string                                      |
| `proposal`       | [ChangeProposal](ChangeProposal.md)         |
| `target`         | [ChangeTarget](ChangeTarget.md)             |
| `diff`           | [Array&lt;ChangeDiff&gt;](ChangeDiff.md)    |
| `evidence`       | [ChangeEvidence](ChangeEvidence.md)         |
| `permissions`    | [ChangePermissions](ChangePermissions.md)   |

## Example

```typescript
import type { ChangeRequest } from "";

// TODO: Update the object below with actual values
const example = {
  id: null,
  kind: null,
  state: null,
  summary: null,
  targetObjectId: null,
  thoroughfareId: null,
  adminUnitId: null,
  source: null,
  createdAt: null,
  decidedAt: null,
  decisionReason: null,
  proposal: null,
  target: null,
  diff: null,
  evidence: null,
  permissions: null,
} satisfies ChangeRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ChangeRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

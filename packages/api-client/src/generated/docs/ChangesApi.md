# ChangesApi

All URIs are relative to *http://localhost:8080*

| Method                                                       | HTTP request                 | Description                                                          |
| ------------------------------------------------------------ | ---------------------------- | -------------------------------------------------------------------- |
| [**createChangeRequest**](ChangesApi.md#createchangerequest) | **POST** /v1/change-requests | Propose a change to the register                                     |
| [**listChanges**](ChangesApi.md#listchanges)                 | **GET** /v1/changes          | Feed of register changes since a point in time (not implemented yet) |

## createChangeRequest

> ChangeRequest createChangeRequest(idempotencyKey, changeRequestCreate)

Propose a change to the register

Creates a change request in state &#x60;submitted&#x60; and writes an audit event. Requires the &#x60;custodian-editor&#x60; role; the target must lie in the caller\&#39;s jurisdiction. The proposer can never approve their own request (four-eyes rule).

### Example

```ts
import {
  Configuration,
  ChangesApi,
} from '';
import type { CreateChangeRequestRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ChangesApi(config);

  const body = {
    // string | Client-generated unique key (a UUID is recommended), reused unchanged on retries.
    idempotencyKey: idempotencyKey_example,
    // ChangeRequestCreate
    changeRequestCreate: ...,
  } satisfies CreateChangeRequestRequest;

  try {
    const data = await api.createChangeRequest(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                    | Type                                          | Description                                                                       | Notes                     |
| ----------------------- | --------------------------------------------- | --------------------------------------------------------------------------------- | ------------------------- |
| **idempotencyKey**      | `string`                                      | Client-generated unique key (a UUID is recommended), reused unchanged on retries. | [Defaults to `undefined`] |
| **changeRequestCreate** | [ChangeRequestCreate](ChangeRequestCreate.md) |                                                                                   |                           |

### Return type

[**ChangeRequest**](ChangeRequest.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                                         | Response headers                |
| ----------- | ------------------------------------------------------------------- | ------------------------------- |
| **201**     | The change request was created.                                     | * Location - <br> * ETag - <br> |
| **400**     | The request is malformed or fails validation.                       | -                               |
| **401**     | A valid access token is required.                                   | -                               |
| **403**     | The caller lacks the role or jurisdiction for this operation.       | -                               |
| **404**     | Nothing matches.                                                    | -                               |
| **422**     | The Idempotency-Key was already used with a different request body. | -                               |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## listChanges

> ChangePage listChanges(since, cursor, limit)

Feed of register changes since a point in time (not implemented yet)

### Example

```ts
import {
  Configuration,
  ChangesApi,
} from '';
import type { ListChangesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ChangesApi(config);

  const body = {
    // Date
    since: 2013-10-20T19:20:30+01:00,
    // string | Opaque cursor from a previous page\'s `nextCursor`. (optional)
    cursor: cursor_example,
    // number (optional)
    limit: 56,
  } satisfies ListChangesRequest;

  try {
    const data = await api.listChanges(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name       | Type     | Description                                                       | Notes                                |
| ---------- | -------- | ----------------------------------------------------------------- | ------------------------------------ |
| **since**  | `Date`   |                                                                   | [Defaults to `undefined`]            |
| **cursor** | `string` | Opaque cursor from a previous page\&#39;s &#x60;nextCursor&#x60;. | [Optional] [Defaults to `undefined`] |
| **limit**  | `number` |                                                                   | [Optional] [Defaults to `20`]        |

### Return type

[**ChangePage**](ChangePage.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                          | Response headers |
| ----------- | ---------------------------------------------------- | ---------------- |
| **200**     | Changes in commit order.                             | -                |
| **400**     | The request is malformed or fails validation.        | -                |
| **501**     | The operation is contracted but not implemented yet. | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# ChangesApi

All URIs are relative to *http://localhost:8080*

| Method                                                         | HTTP request                              | Description                                                                    |
| -------------------------------------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------ |
| [**approveChangeRequest**](ChangesApi.md#approvechangerequest) | **POST** /v1/change-requests/{id}/approve | Approve a change request (four-eyes rule)                                      |
| [**createChangeRequest**](ChangesApi.md#createchangerequest)   | **POST** /v1/change-requests              | Propose a change to the register                                               |
| [**getChangeRequest**](ChangesApi.md#getchangerequest)         | **GET** /v1/change-requests/{id}          | One change request with its diff and evidence                                  |
| [**listChangeRequests**](ChangesApi.md#listchangerequests)     | **GET** /v1/change-requests               | Approver inbox                                                                 |
| [**listChanges**](ChangesApi.md#listchanges)                   | **GET** /v1/changes                       | Feed of register changes since a point in time (not implemented yet)           |
| [**returnChangeRequest**](ChangesApi.md#returnchangerequest)   | **POST** /v1/change-requests/{id}/return  | Return a change request to its proposer with a written reason (four-eyes rule) |

## approveChangeRequest

> ChangeRequest approveChangeRequest(id, idempotencyKey)

Approve a change request (four-eyes rule)

Requires the &#x60;custodian-approver&#x60; role and the change request\&#39;s jurisdiction. The proposer can never approve their own request: that answers &#x60;403&#x60;. Writes an audit event.

### Example

```ts
import {
  Configuration,
  ChangesApi,
} from '';
import type { ApproveChangeRequestRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ChangesApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string | Client-generated unique key (a UUID is recommended), reused unchanged on retries.
    idempotencyKey: idempotencyKey_example,
  } satisfies ApproveChangeRequestRequest;

  try {
    const data = await api.approveChangeRequest(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type     | Description                                                                       | Notes                     |
| ------------------ | -------- | --------------------------------------------------------------------------------- | ------------------------- |
| **id**             | `string` |                                                                                   | [Defaults to `undefined`] |
| **idempotencyKey** | `string` | Client-generated unique key (a UUID is recommended), reused unchanged on retries. | [Defaults to `undefined`] |

### Return type

[**ChangeRequest**](ChangeRequest.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                                                       | Response headers |
| ----------- | --------------------------------------------------------------------------------- | ---------------- |
| **200**     | The approved change request.                                                      | -                |
| **401**     | A valid access token is required.                                                 | -                |
| **403**     | The caller lacks the role or jurisdiction for this operation.                     | -                |
| **404**     | Nothing matches.                                                                  | -                |
| **409**     | The resource is not in a state that allows this operation (e.g. already decided). | -                |
| **422**     | The Idempotency-Key was already used with a different request body.               | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

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

## getChangeRequest

> ChangeRequest getChangeRequest(id, ifNoneMatch)

One change request with its diff and evidence

Requires the &#x60;custodian-approver&#x60; role; the change request must lie in the caller\&#39;s jurisdiction.

### Example

```ts
import {
  Configuration,
  ChangesApi,
} from '';
import type { GetChangeRequestRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ChangesApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    ifNoneMatch: ifNoneMatch_example,
  } satisfies GetChangeRequestRequest;

  try {
    const data = await api.getChangeRequest(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type     | Description | Notes                                |
| --------------- | -------- | ----------- | ------------------------------------ |
| **id**          | `string` |             | [Defaults to `undefined`]            |
| **ifNoneMatch** | `string` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**ChangeRequest**](ChangeRequest.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                                   | Response headers |
| ----------- | ------------------------------------------------------------- | ---------------- |
| **200**     | The change request.                                           | * ETag - <br>    |
| **304**     | The representation has not changed since the given ETag.      | -                |
| **401**     | A valid access token is required.                             | -                |
| **403**     | The caller lacks the role or jurisdiction for this operation. | -                |
| **404**     | Nothing matches.                                              | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## listChangeRequests

> ChangeRequestPage listChangeRequests(state, cursor, limit)

Approver inbox

Change requests inside the caller\&#39;s jurisdiction, oldest first. Requires the &#x60;custodian-approver&#x60; role. Each item carries &#x60;permissions&#x60;, so a client can tell whether the caller may decide it (four-eyes rule).

### Example

```ts
import {
  Configuration,
  ChangesApi,
} from '';
import type { ListChangeRequestsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ChangesApi(config);

  const body = {
    // ChangeRequestState (optional)
    state: ...,
    // string | Opaque cursor from a previous page\'s `nextCursor`. (optional)
    cursor: cursor_example,
    // number (optional)
    limit: 56,
  } satisfies ListChangeRequestsRequest;

  try {
    const data = await api.listChangeRequests(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name       | Type                 | Description                                                       | Notes                                                                                                               |
| ---------- | -------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| **state**  | `ChangeRequestState` |                                                                   | [Optional] [Defaults to `undefined`] [Enum: submitted, in_review, approved, rejected, returned, applied, withdrawn] |
| **cursor** | `string`             | Opaque cursor from a previous page\&#39;s &#x60;nextCursor&#x60;. | [Optional] [Defaults to `undefined`]                                                                                |
| **limit**  | `number`             |                                                                   | [Optional] [Defaults to `20`]                                                                                       |

### Return type

[**ChangeRequestPage**](ChangeRequestPage.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                                   | Response headers |
| ----------- | ------------------------------------------------------------- | ---------------- |
| **200**     | One page of change requests.                                  | -                |
| **400**     | The request is malformed or fails validation.                 | -                |
| **401**     | A valid access token is required.                             | -                |
| **403**     | The caller lacks the role or jurisdiction for this operation. | -                |

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

## returnChangeRequest

> ChangeRequest returnChangeRequest(id, idempotencyKey, changeRequestReturn)

Return a change request to its proposer with a written reason (four-eyes rule)

Same rules as approving. The reason is shown to the proposer; it must not contain personal data. Writes an audit event.

### Example

```ts
import {
  Configuration,
  ChangesApi,
} from '';
import type { ReturnChangeRequestRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ChangesApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string | Client-generated unique key (a UUID is recommended), reused unchanged on retries.
    idempotencyKey: idempotencyKey_example,
    // ChangeRequestReturn
    changeRequestReturn: ...,
  } satisfies ReturnChangeRequestRequest;

  try {
    const data = await api.returnChangeRequest(body);
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
| **id**                  | `string`                                      |                                                                                   | [Defaults to `undefined`] |
| **idempotencyKey**      | `string`                                      | Client-generated unique key (a UUID is recommended), reused unchanged on retries. | [Defaults to `undefined`] |
| **changeRequestReturn** | [ChangeRequestReturn](ChangeRequestReturn.md) |                                                                                   |                           |

### Return type

[**ChangeRequest**](ChangeRequest.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                                                       | Response headers |
| ----------- | --------------------------------------------------------------------------------- | ---------------- |
| **200**     | The returned change request.                                                      | -                |
| **400**     | The request is malformed or fails validation.                                     | -                |
| **401**     | A valid access token is required.                                                 | -                |
| **403**     | The caller lacks the role or jurisdiction for this operation.                     | -                |
| **404**     | Nothing matches.                                                                  | -                |
| **409**     | The resource is not in a state that allows this operation (e.g. already decided). | -                |
| **422**     | The Idempotency-Key was already used with a different request body.               | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

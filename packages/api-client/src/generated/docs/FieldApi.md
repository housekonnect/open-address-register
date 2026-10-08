# FieldApi

All URIs are relative to *http://localhost:8080*

| Method                                                         | HTTP request                           | Description                                                     |
| -------------------------------------------------------------- | -------------------------------------- | --------------------------------------------------------------- |
| [**createFieldCapture**](FieldApi.md#createfieldcapture)       | **POST** /v1/field/captures            | Upload a field capture (point and photo)                        |
| [**getChangeRequestPhoto**](FieldApi.md#getchangerequestphoto) | **GET** /v1/change-requests/{id}/photo | Evidence photo of a change request                              |
| [**listFieldAssignments**](FieldApi.md#listfieldassignments)   | **GET** /v1/field/assignments          | Assignments of the calling field verifier (not implemented yet) |

## createFieldCapture

> FieldCapture createFieldCapture(idempotencyKey, metadata, photo)

Upload a field capture (point and photo)

Stores the photo in object storage and creates a change request for the captured point. Requires the &#x60;field-verifier&#x60; role. The client generates the &#x60;Idempotency-Key&#x60; when the capture is created offline and reuses it on every retry, so a capture is applied at most once. Resumable (chunked) uploads are planned.

### Example

```ts
import {
  Configuration,
  FieldApi,
} from '';
import type { CreateFieldCaptureRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FieldApi(config);

  const body = {
    // string | Client-generated unique key (a UUID is recommended), reused unchanged on retries.
    idempotencyKey: idempotencyKey_example,
    // FieldCaptureMetadata
    metadata: ...,
    // Blob | JPEG or PNG photo, at most 10 MB.
    photo: BINARY_DATA_HERE,
  } satisfies CreateFieldCaptureRequest;

  try {
    const data = await api.createFieldCapture(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type                                            | Description                                                                       | Notes                     |
| ------------------ | ----------------------------------------------- | --------------------------------------------------------------------------------- | ------------------------- |
| **idempotencyKey** | `string`                                        | Client-generated unique key (a UUID is recommended), reused unchanged on retries. | [Defaults to `undefined`] |
| **metadata**       | [FieldCaptureMetadata](FieldCaptureMetadata.md) |                                                                                   | [Defaults to `undefined`] |
| **photo**          | `Blob`                                          | JPEG or PNG photo, at most 10 MB.                                                 | [Defaults to `undefined`] |

### Return type

[**FieldCapture**](FieldCapture.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                                         | Response headers  |
| ----------- | ------------------------------------------------------------------- | ----------------- |
| **201**     | The capture was stored and a change request created.                | * Location - <br> |
| **400**     | The request is malformed or fails validation.                       | -                 |
| **401**     | A valid access token is required.                                   | -                 |
| **403**     | The caller lacks the role or jurisdiction for this operation.       | -                 |
| **413**     | The upload is too large.                                            | -                 |
| **422**     | The Idempotency-Key was already used with a different request body. | -                 |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getChangeRequestPhoto

> Blob getChangeRequestPhoto(id)

Evidence photo of a change request

Streams the photo attached to a field capture. Same access rule as the change request itself; clients never read the object store directly.

### Example

```ts
import {
  Configuration,
  FieldApi,
} from '';
import type { GetChangeRequestPhotoRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FieldApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetChangeRequestPhotoRequest;

  try {
    const data = await api.getChangeRequestPhoto(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `string` |             | [Defaults to `undefined`] |

### Return type

**Blob**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `image/jpeg`, `image/png`, `application/problem+json`

### HTTP response details

| Status code | Description                                                   | Response headers |
| ----------- | ------------------------------------------------------------- | ---------------- |
| **200**     | The photo.                                                    | -                |
| **401**     | A valid access token is required.                             | -                |
| **403**     | The caller lacks the role or jurisdiction for this operation. | -                |
| **404**     | Nothing matches.                                              | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## listFieldAssignments

> FieldAssignmentPage listFieldAssignments(cursor, limit)

Assignments of the calling field verifier (not implemented yet)

### Example

```ts
import { Configuration, FieldApi } from "";
import type { ListFieldAssignmentsRequest } from "";

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FieldApi(config);

  const body = {
    // string | Opaque cursor from a previous page\'s `nextCursor`. (optional)
    cursor: cursor_example,
    // number (optional)
    limit: 56,
  } satisfies ListFieldAssignmentsRequest;

  try {
    const data = await api.listFieldAssignments(body);
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
| **cursor** | `string` | Opaque cursor from a previous page\&#39;s &#x60;nextCursor&#x60;. | [Optional] [Defaults to `undefined`] |
| **limit**  | `number` |                                                                   | [Optional] [Defaults to `20`]        |

### Return type

[**FieldAssignmentPage**](FieldAssignmentPage.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                          | Response headers |
| ----------- | ---------------------------------------------------- | ---------------- |
| **200**     | One page of assignments.                             | -                |
| **401**     | A valid access token is required.                    | -                |
| **501**     | The operation is contracted but not implemented yet. | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

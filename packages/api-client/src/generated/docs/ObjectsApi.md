# ObjectsApi

All URIs are relative to *http://localhost:8080*

| Method                                                 | HTTP request                     | Description                                                     |
| ------------------------------------------------------ | -------------------------------- | --------------------------------------------------------------- |
| [**getObject**](ObjectsApi.md#getobject)               | **GET** /v1/objects/{id}         | Get an addressable object                                       |
| [**getObjectHistory**](ObjectsApi.md#getobjecthistory) | **GET** /v1/objects/{id}/history | Get the recorded history of an addressable object, newest first |

## getObject

> AddressableObject getObject(id, ifNoneMatch)

Get an addressable object

### Example

```ts
import {
  Configuration,
  ObjectsApi,
} from '';
import type { GetObjectRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ObjectsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    ifNoneMatch: ifNoneMatch_example,
  } satisfies GetObjectRequest;

  try {
    const data = await api.getObject(body);
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

[**AddressableObject**](AddressableObject.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                              | Response headers |
| ----------- | -------------------------------------------------------- | ---------------- |
| **200**     | The object.                                              | * ETag - <br>    |
| **304**     | The representation has not changed since the given ETag. | -                |
| **404**     | Nothing matches.                                         | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getObjectHistory

> HistoryPage getObjectHistory(id, cursor, limit)

Get the recorded history of an addressable object, newest first

### Example

```ts
import {
  Configuration,
  ObjectsApi,
} from '';
import type { GetObjectHistoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ObjectsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string | Opaque cursor from a previous page\'s `nextCursor`. (optional)
    cursor: cursor_example,
    // number (optional)
    limit: 56,
  } satisfies GetObjectHistoryRequest;

  try {
    const data = await api.getObjectHistory(body);
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
| **id**     | `string` |                                                                   | [Defaults to `undefined`]            |
| **cursor** | `string` | Opaque cursor from a previous page\&#39;s &#x60;nextCursor&#x60;. | [Optional] [Defaults to `undefined`] |
| **limit**  | `number` |                                                                   | [Optional] [Defaults to `20`]        |

### Return type

[**HistoryPage**](HistoryPage.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                   | Response headers |
| ----------- | --------------------------------------------- | ---------------- |
| **200**     | One page of history entries.                  | -                |
| **400**     | The request is malformed or fails validation. | -                |
| **404**     | Nothing matches.                              | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# ResolveApi

All URIs are relative to *http://localhost:8080*

| Method                               | HTTP request        | Description                                        |
| ------------------------------------ | ------------------- | -------------------------------------------------- |
| [**resolve**](ResolveApi.md#resolve) | **GET** /v1/resolve | Resolve a national ID or an alias to an address    |
| [**reverse**](ResolveApi.md#reverse) | **GET** /v1/reverse | Nearest addresses to a point (not implemented yet) |
| [**search**](ResolveApi.md#search)   | **GET** /v1/search  | Free-text address search (not implemented yet)     |

## resolve

> Resolution resolve(ref, ifNoneMatch)

Resolve a national ID or an alias to an address

&#x60;ref&#x60; is either a national ID in any display form (&#x60;4821 093 7618&#x60;, &#x60;4821-093-7618&#x60;, &#x60;DEMO 4821 093 7618&#x60;) or an alias written as &#x60;&lt;system&gt;:&lt;value&gt;&#x60; (for example &#x60;kcca-plot:KLA-C-0042&#x60;).

### Example

```ts
import { Configuration, ResolveApi } from "";
import type { ResolveRequest } from "";

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ResolveApi(config);

  const body = {
    // string
    ref: ref_example,
    // string (optional)
    ifNoneMatch: ifNoneMatch_example,
  } satisfies ResolveRequest;

  try {
    const data = await api.resolve(body);
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
| **ref**         | `string` |             | [Defaults to `undefined`]            |
| **ifNoneMatch** | `string` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**Resolution**](Resolution.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                              | Response headers |
| ----------- | -------------------------------------------------------- | ---------------- |
| **200**     | The resolved address.                                    | * ETag - <br>    |
| **304**     | The representation has not changed since the given ETag. | -                |
| **400**     | The request is malformed or fails validation.            | -                |
| **404**     | Nothing matches.                                         | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## reverse

> AddressPage reverse(lat, lon, cursor, limit)

Nearest addresses to a point (not implemented yet)

### Example

```ts
import { Configuration, ResolveApi } from "";
import type { ReverseRequest } from "";

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ResolveApi(config);

  const body = {
    // number
    lat: 1.2,
    // number
    lon: 1.2,
    // string | Opaque cursor from a previous page\'s `nextCursor`. (optional)
    cursor: cursor_example,
    // number (optional)
    limit: 56,
  } satisfies ReverseRequest;

  try {
    const data = await api.reverse(body);
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
| **lat**    | `number` |                                                                   | [Defaults to `undefined`]            |
| **lon**    | `number` |                                                                   | [Defaults to `undefined`]            |
| **cursor** | `string` | Opaque cursor from a previous page\&#39;s &#x60;nextCursor&#x60;. | [Optional] [Defaults to `undefined`] |
| **limit**  | `number` |                                                                   | [Optional] [Defaults to `20`]        |

### Return type

[**AddressPage**](AddressPage.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                          | Response headers |
| ----------- | ---------------------------------------------------- | ---------------- |
| **200**     | Addresses ordered by distance.                       | -                |
| **400**     | The request is malformed or fails validation.        | -                |
| **501**     | The operation is contracted but not implemented yet. | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## search

> AddressPage search(q, cursor, limit)

Free-text address search (not implemented yet)

### Example

```ts
import { Configuration, ResolveApi } from "";
import type { SearchRequest } from "";

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new ResolveApi(config);

  const body = {
    // string
    q: q_example,
    // string | Opaque cursor from a previous page\'s `nextCursor`. (optional)
    cursor: cursor_example,
    // number (optional)
    limit: 56,
  } satisfies SearchRequest;

  try {
    const data = await api.search(body);
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
| **q**      | `string` |                                                                   | [Defaults to `undefined`]            |
| **cursor** | `string` | Opaque cursor from a previous page\&#39;s &#x60;nextCursor&#x60;. | [Optional] [Defaults to `undefined`] |
| **limit**  | `number` |                                                                   | [Optional] [Defaults to `20`]        |

### Return type

[**AddressPage**](AddressPage.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`, `application/problem+json`

### HTTP response details

| Status code | Description                                          | Response headers |
| ----------- | ---------------------------------------------------- | ---------------- |
| **200**     | Matching addresses, best match first.                | -                |
| **400**     | The request is malformed or fails validation.        | -                |
| **501**     | The operation is contracted but not implemented yet. | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

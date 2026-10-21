# @soukly/listing-data

Front-end access to listing-service, shared by the remotes that read or write listings (`mfe-publish` and `mfe-catalog`).

| Export                                                                         | Role                                                                                                                                                                                                   |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ListingsApi`                                                                  | drafts (ANN-01): `create`, `get` and `update` with the version as ETag (`If-Match`, `application/merge-patch+json`), `publish`                                                                         |
| `PhotosApi`                                                                    | photos (ANN-02): upload request, direct `PUT` to the presigned URL with its progress (an `HttpClient` without interceptors, so the access token never reaches the storage), completion, order, removal |
| `ReferentialsApi`                                                              | categories, attributes of a subcategory, governorates and delegations (CAT-01), cached in memory for the session; a failed request is not cached                                                       |
| `SearchApi`                                                                    | public search (CATA-01): `search(params)` on `GET /listings`, empty criteria left out, `SEARCH_PAGE_SIZE` 24 and the `SEARCH_SORTS`                                                                    |
| `resizeForUpload`                                                              | reduces a photo in the browser before its upload: longest side 1600 px, JPEG 0.85, orientation of the camera applied; a photo that already fits and weighs less than 5 MB is sent as is                |
| `labelOf`, `familyOf`, `attributePrefix`, `usesGrouping`                       | display helpers: label in the current language, family of a subcategory, `S+` before the number of rooms, thousands separators only for large numbers (not for a year)                                 |
| `controlKind`, `parseInteger`, `formatInteger`, `attributeUnit`, `withoutUnit` | attribute form helpers shared by the publish form and the search filters: control of an attribute type, integer parsing and formatting, unit taken from the label                                      |
| `priceUnit`                                                                    | unit shown after a price: per month (rent, roommates), per night (holiday rentals), none otherwise                                                                                                     |
| `testing`                                                                      | fixtures for unit tests (`draftListing`, `TEST_FAMILIES`, `CAR_ATTRIBUTES`, `APARTMENT_ATTRIBUTES`, `TEST_GOVERNORATES`, `SFAX_DELEGATIONS`), copied from the referentials of listing-service          |

Every request goes through the `API_PREFIX` of [`@soukly/auth`](../auth/README.md), so `authInterceptor` adds the access token.

```bash
pnpm nx test listing-data
pnpm nx lint listing-data
```

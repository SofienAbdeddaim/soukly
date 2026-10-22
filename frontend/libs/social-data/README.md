# @soukly/social-data

Front-end access to social-publisher (SOC-01), shared by the shell (the return route of Facebook, `/oauth/facebook/callback`) and `mfe-account` (the page of the social networks).

| Export                                                                    | Role                                                                                                                                                                                                                            |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SocialApi`                                                               | `connections()`, `connectFacebook(lang)` (URL of the Facebook dialog), `facebookCallback(redirect)` (never rejects: `connected`, `choose` or `failed`), `pageSelection()`, `chooseFacebookPage(pageId)`, `disconnectFacebook()` |
| `FACEBOOK_CALLBACK_PATH`, `SOCIAL_PAGE_PATH`, `FACEBOOK_PAGE_CHOICE_PATH` | paths of the flow                                                                                                                                                                                                               |
| `ExternalNavigation`                                                      | `go(url)` leaves for the Facebook dialog; faked in the tests                                                                                                                                                                    |
| `FACEBOOK_RESULT_PARAM`, `toFacebookFailure`                              | how the return from Facebook ended, carried to the social page as `?facebook=connected` or a problem type                                                                                                                       |

Every request goes through the `API_PREFIX` of [`@soukly/auth`](../auth/README.md), so `authInterceptor` adds the access token. No Meta token ever reaches the browser.

```bash
pnpm nx test social-data
pnpm nx lint social-data
```

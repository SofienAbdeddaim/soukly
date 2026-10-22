# @soukly/social-data

Front-end access to social-publisher (SOC-01, SOC-02), shared by the shell (the return routes of Facebook, `/oauth/facebook/callback`, and Instagram, `/oauth/instagram/callback`) and `mfe-account` (the page of the social networks).

| Export                                                                                               | Role                                                                                                                                                                                                                                                                                                                                        |
| ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SocialApi`                                                                                          | `connections()`, `connectFacebook(lang)` (URL of the Facebook dialog), `facebookCallback(redirect)` (never rejects: `connected`, `choose` or `failed`), `pageSelection()`, `chooseFacebookPage(pageId)`, `disconnectFacebook()`; `connectInstagram(lang)`, `instagramCallback(redirect)` (`connected` or `failed`), `disconnectInstagram()` |
| `FACEBOOK_CALLBACK_PATH`, `INSTAGRAM_CALLBACK_PATH`, `SOCIAL_PAGE_PATH`, `FACEBOOK_PAGE_CHOICE_PATH` | paths of the flow                                                                                                                                                                                                                                                                                                                           |
| `ExternalNavigation`                                                                                 | `go(url)` leaves for the Facebook or Instagram dialog; faked in the tests                                                                                                                                                                                                                                                                   |
| `FACEBOOK_RESULT_PARAM`, `toFacebookFailure`, `INSTAGRAM_RESULT_PARAM`, `toInstagramFailure`         | how the return from Facebook or Instagram ended, carried to the social page as `?facebook=` / `?instagram=` `connected` or a problem type                                                                                                                                                                                                   |

Every request goes through the `API_PREFIX` of [`@soukly/auth`](../auth/README.md), so `authInterceptor` adds the access token. No Meta token ever reaches the browser.

```bash
pnpm nx test social-data
pnpm nx lint social-data
```

# social-publisher

Social networks of Soukly: package `com.soukly.social`, database `soukly_social` (Flyway, `src/main/resources/db/migration`), port 8083, routed by the gateway under `/api/social/**` (JWT required on every path). This service is the only one that holds Meta credentials and tokens.

## Facebook Page connection (SOC-01)

Meta only lets an app post on a **Page**, never on a personal profile. A member connects **one Page**; connecting another one replaces it.

```
browser                         social-publisher                         Facebook
POST /social/facebook/connect ──► state (32 random bytes, stored hashed, 10 min, single use)
◄── { authorizeUrl } ─────────────
──────────────────────── OAuth dialog (client_id, redirect_uri, state, scope) ──────────────────────►
◄─────────────── {FRONT_BASE_URL}/oauth/facebook/callback?code&state (or ?error=access_denied&state) ──
POST /social/facebook/callback ─► consumes the state ─► code → user token → long-lived user token
                                  /me/permissions, /me, /me/accounts (Pages + Page tokens) ◄─────────
◄── { lang, connection } or { lang, pendingSelection } (no token, ever)
POST /social/facebook/page ─────► the chosen Page of the pending selection (encrypted, 10 min)
```

- The redirect URI has **no language prefix** (one URI to declare at Meta). The shell route reads `code`, `state` or `error`, removes them from the address bar and posts them to the callback; the answer's `lang` brings the member back to `/{lang}/account/social`.
- The `code` is only exchanged here, never in the browser. A state that is unknown, expired, used or started by another member is refused (`oauth-state-invalid`) and nothing is saved.
- Permissions asked: `pages_show_list`, `pages_manage_posts`, `pages_read_engagement`. Without `pages_show_list` and `pages_manage_posts` the connection is refused (`oauth-permission-missing`). Pages whose `tasks` allow no posting (`CREATE_CONTENT` or `MANAGE`) are left out; none left: `facebook-no-page`.
- `social_connections` stores the Page id, its name and picture, the app-scoped id of the person (to revoke the permissions later) and the **Page token encrypted in AES-256-GCM** (`TokenCipher`: random 12-byte nonce per encryption, stored in front of the ciphertext; associated data = member and network, so a ciphertext moved to another row does not decrypt). `key_version` prepares a rotation. A Page token read with a long-lived user token does not expire on its own; `NEEDS_RECONNECT` (set by SOC-04 when Meta refuses it) is cleared by a new connection.
- `DELETE /social/facebook` deletes the row, then revokes the permissions (`DELETE /{user-id}/permissions` with the app access token) at best effort, unless another Soukly account still uses them.
- `user.deleted` (`soukly.user-events`, group `social-publisher`): the connections, states and pending choice of the account are deleted in the transaction that records `processed_events`, then the permissions are revoked. Retries then `soukly.user-events.dlq`, like listing-service.
- Expired states and choices are deleted hourly.
- Tokens, the app secret, codes and states are never returned, logged or put in an event: the Graph client drops the exception messages that quote a URL, and the tests check their own logs.

| Problem type                     | Status | When                                                                    |
| -------------------------------- | ------ | ----------------------------------------------------------------------- |
| `oauth-state-invalid`            | 400    | the state is unknown, expired, used, or another member's                |
| `oauth-cancelled`                | 400    | `error=access_denied`: the member cancelled on Facebook (with `lang`)   |
| `oauth-permission-missing`       | 400    | a required permission was declined (with `lang`)                        |
| `facebook-no-page`               | 400    | the person manages no Page they can post on (with `lang`)               |
| `facebook-unavailable`           | 502    | Facebook refused the code, sent another error, or did not answer        |
| `page-selection-not-found`       | 404    | no pending choice, it expired, or the Page is not in it                 |
| `facebook-not-configured`        | 503    | `META_APP_ID` or `META_APP_SECRET` is empty in this environment         |

## Instagram professional account connection (SOC-02)

A member connects **one Instagram professional account** (Business or Creator) with **Instagram API with Instagram Login**: no Facebook Page is needed, and Facebook and Instagram are independent (one, the other or both). The flow has the same structure and guarantees as Facebook, and shares its OAuth states (`OAuthStates`, package `oauth`), its problem types and `social_connections`.

```
browser                          social-publisher                         Instagram
POST /social/instagram/connect ──► state (shared with Facebook, bound to the network)
◄── { authorizeUrl } ──────────── www.instagram.com/oauth/authorize?client_id&redirect_uri&state&scope&response_type=code
◄─────────────── {FRONT_BASE_URL}/oauth/instagram/callback?code&state (or ?error=access_denied&state) ──
POST /social/instagram/callback ─► consumes the state ─► POST api.instagram.com/oauth/access_token (short-lived token,
                                   granted permissions) ─► graph.instagram.com/access_token (long-lived, ~60 days)
                                   ─► /{version}/me?fields=id,user_id,username,account_type,profile_picture_url
◄── { lang, connection: { network: INSTAGRAM, displayName: "@username", status, connectedAt } } (no token, ever)
```

- Permissions asked and required: `instagram_business_basic`, `instagram_business_content_publish`. A personal account (`account_type` other than `BUSINESS` or `MEDIA_CREATOR`) is refused with `instagram-not-professional`; nothing is saved.
- `social_connections` stores the professional account id (`user_id`, used to publish in SOC-05) as `external_id`, the app-scoped id as `external_user_id`, `@username`, the picture, the **long-lived token encrypted** like the Page tokens, and its expiry in `token_expires_at` (Facebook Page tokens do not expire: `NULL`).
- **Daily refresh** (`InstagramTokenRefresher`): every connected Instagram token that expires within `INSTAGRAM_TOKEN_REFRESH_BEFORE` (10 days) is refreshed (`graph.instagram.com/refresh_access_token`, a new ~60-day token). Each token is refreshed in its own transaction under a `FOR UPDATE SKIP LOCKED` row lock, so two instances never refresh the same one, and one run at a time per instance. A token Instagram refuses (4xx: access removed) or already expired puts the connection in `NEEDS_RECONNECT`; when Instagram is down (5xx, 429, no answer) it is tried again the next day.
- `DELETE /social/instagram` deletes the row only: Instagram Login offers no call to revoke the access, so the member removes Soukly in Instagram (*Settings › Website permissions › Apps and websites*); the token is no longer used and expires on its own. `user.deleted` deletes the Instagram connection with the others, and only Facebook connections are revoked at Meta.

| Problem type                     | Status | When                                                                    |
| -------------------------------- | ------ | ----------------------------------------------------------------------- |
| `oauth-state-invalid`            | 400    | the state is unknown, expired, used, another member's or a Facebook one |
| `oauth-cancelled`                | 400    | `error=access_denied` on Instagram (with `lang`)                        |
| `oauth-permission-missing`       | 400    | a required permission was not granted (with `lang`)                     |
| `instagram-not-professional`     | 400    | the account is a personal one (with `lang`)                             |
| `instagram-unavailable`          | 502    | Instagram refused the code, sent another error, or did not answer       |
| `instagram-not-configured`       | 503    | `INSTAGRAM_APP_ID` or `INSTAGRAM_APP_SECRET` is empty                   |

## Where to share a listing and the post text (SOC-03)

- **Preference per network**: `social_connections.auto_share` (V3), `true` when a network is connected; a new
  connection of a network already connected (after `NEEDS_RECONNECT`) keeps it, a disconnection forgets it.
  `GET /social/connections` returns `autoShare`; `PATCH /social/connections/{facebook|instagram}` with
  `{"autoShare": false}` changes it for the member of the token (`404` `connection-not-found`, `400`
  `validation-failed` without a boolean). The publish form ticks the networks with `autoShare` the first time it
  shows the preview of a draft; the choice is then stored in the draft (`shareTo`, listing-service).
- **Post text**: `CaptionBuilder` (package `caption`) is the single source of the texts, reused by SOC-04 and SOC-05;
  the front end never builds them. `POST /social/caption-preview` (JWT) takes `{ title, description, priceMode,
  priceTnd, negotiable, categoryCode, governorateCode, delegationCode, publicUrl, language }` and returns
  `{ text, hashtags }`:

  ```
  Peugeot 208 diesel, première main
  32 000 DT · Négociable
  Sfax, Sakiet Ezzit

  Première main, carnet d'entretien complet…

  Voir l'annonce : https://soukly.tn/fr/listings/8f3c…
  #Soukly #Sfax #Voitures
  ```

  Templates in `fr`, `ar` and `en` (`CaptionLanguage`); prices as the `price` pipe of the front end (`32 000 DT`,
  `32.000 د.ت.`, `TND 32,000`, with the non-breaking spaces of `Intl`); description cut at 300 characters on the last
  word, then `…`; whole text at most 2,000 characters (an Instagram caption allows 2,200). An empty field of a draft
  leaves its line out. Hashtags: the governorate and the subcategory in the language, without spaces or punctuation
  (`#BenArous`, `#MotosEtScooters`; Arabic words joined with `_`: `#دراجات_نارية_وسكوترات`).
- **No personal data**: no name, phone number or email of the seller is an input, and email addresses and Tunisian
  phone numbers typed in the title or the description are replaced with `[…]` (an amount followed by the currency,
  `25 000 000 DT`, is kept).
- **Labels** come from the public referential endpoints of listing-service (`GET /categories`,
  `/locations/governorates`, `/locations/governorates/{code}/delegations`, at `LISTING_SERVICE_URL`, never through the
  gateway), kept one hour in memory (Caffeine): listing-service stays the single source of the places and categories.
  listing-service unavailable: `503` `caption-unavailable`; an unknown code: `400` `validation-failed`
  (`category.unknown`, `governorate.unknown`, `delegation.unknown`).

## Publishing the listings on the Facebook Page (SOC-04)

When a listing is published with `FACEBOOK` in `shareTo`, it is posted on the connected Page of the seller: **all its
photos (at most 10, in the seller's order) and the text of the `CaptionBuilder`**, whose link carries
`utm_source=facebook&utm_medium=social&utm_campaign=soukly_share`.

```
listing-service ── listing.published ──► consumer (group social-publisher, processed_events)
                                          └─► social_posts: PENDING, or SKIPPED (not-connected / needs-reconnect)
FacebookPublisher (every 10 s) ── lock a due PENDING row (FOR UPDATE SKIP LOCKED) ── Page token decrypted
   POST /{page-id}/photos  url=<1600 px WebP>, published=false   (once per photo)
   POST /{page-id}/feed    message=<caption>, attached_media[i]={"media_fbid": …}
   GET  /{post-id}?fields=permalink_url
   └─► PUBLISHED (external_post_id, permalink, published_at)
listing.deleted / user.deleted ──► PENDING → CANCELLED; PUBLISHED → DELETE /{post-id} (3 attempts) → DELETED
```

- **`social_posts`** (V4): one row per listing and network (`unique (listing_id, network)`), with `status`
  (`PENDING`, `PUBLISHED`, `SKIPPED`, `FAILED`, `CANCELLED`, `DELETED`), `attempts`, `last_error_code` (the reason of a
  `SKIPPED` or `FAILED` row, or why a post could not be removed), `next_attempt_at`, the Page it was posted on and the
  `content` of the event (title, description, price, codes, photos, link, language): the publication is decoupled from
  Kafka and builds the text when it runs. The consumer never calls Meta for a publication.
- **Pace** (in memory, one instance in V1): at most one post per Page every 30 s (`page-interval`) and 30 publishing
  calls (photos and posts) per minute in all (`calls-per-minute`). A post whose Page is busy is postponed without
  counting an attempt.
- **Errors** (`GraphFailure`, codes of the Graph API error guide):

  | Meta answer | Effect |
  | --- | --- |
  | no answer, 5xx, 429, codes `1`, `2`, `4`, `17`, `32`, `341`, `613`, `80001`, `is_transient` | `PENDING`, tried again after 1 min, 5 min, 30 min, 2 h, 6 h (`retry-delays`), then `FAILED` with the last code |
  | `190` (any subcode), `102` | connection `NEEDS_RECONNECT`, post `FAILED` `needs-reconnect`, no new attempt |
  | `10`, `200`–`299` | `FAILED` `permission-missing` |
  | anything else (`100`, `324` invalid photo, `368`, `506` duplicate…) | `FAILED` `meta-{code}[/{subcode}]` |

  The message of Meta is logged cleaned of tokens, emails and phone numbers (`GraphError.safeMessage`).
- **Removal**: `listing.deleted` cancels the `PENDING` rows (a publication in progress finishes first, under its row
  lock) and deletes the `PUBLISHED` posts with the Page token, in 3 attempts 5 s apart; a post already gone on Facebook
  (`100/33`) counts as deleted. When it cannot be deleted (another Page connected since, token refused, Meta down), the
  row stays `PUBLISHED` with `last_error_code` and an error is logged. `user.deleted` does the same for every listing of
  the member **before** deleting the connections, then deletes the `social_posts` rows with them.
- `listing.updated`, `listing.renewed`, `listing.deactivated`, `listing.expired` are ignored in V1. Invalid listing
  events go to `soukly.listing-events.dlq` after the retries (shared error handler with the user events).
- **Metrics** (`/actuator/metrics`, never routed by the gateway): `social.posts{network,status}` counts the posts that
  enter each status, `social.posts.publish{network,outcome}` times the publications. Logs carry `listingId` and the
  `X-Request-Id` header of the Kafka message when it has one (kept in `social_posts.request_id`).

| Setting (`soukly.social.posts.facebook.*`) | Default |
| --- | --- |
| `publish-interval` / `publish-initial-delay` | `PT10S` / `PT30S` |
| `page-interval` | `30s` |
| `calls-per-minute` | `30` |
| `retry-delays` | `1m,5m,30m,2h,6h` |
| `max-photos` | `10` |
| `delete-attempts` / `delete-retry-interval` | `3` / `5s` |

## Configuration

| Variable                                  | Meaning                                                                                                                                   |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `SOCIAL_TOKEN_ENC_KEY`                    | 32 random bytes in base64 (`openssl rand -base64 32`). **Required**: the service does not start without a valid key. Never change it once tokens are stored. |
| `META_APP_ID`, `META_APP_SECRET`          | the Meta app of the environment (one app per environment). Empty: Facebook cannot be connected (`503`).                                  |
| `META_GRAPH_VERSION`                      | Graph API version of the dialog and the calls, `v26.0` by default (latest version in October 2026).                                       |
| `INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET` | the Instagram app id and secret of the Meta app (*Instagram › API setup with Instagram login*), distinct from the Facebook ones. Empty: Instagram cannot be connected (`503`). |
| `INSTAGRAM_GRAPH_VERSION`                 | version of the Instagram Graph API calls, `META_GRAPH_VERSION` by default                                                                 |
| `INSTAGRAM_TOKEN_REFRESH_BEFORE`          | the daily task refreshes the Instagram tokens that expire within this delay, `10d` by default                                            |
| `FRONT_BASE_URL` (else `PUBLIC_BASE_URL`) | origin of the redirect URIs `{FRONT_BASE_URL}/oauth/facebook/callback` and `/oauth/instagram/callback`                                  |
| `USER_SERVICE_JWKS_URI`, `JWT_ISSUER`     | validation of the access tokens, as in the gateway                                                                                        |
| `KAFKA_BOOTSTRAP_SERVERS`                 | Redpanda, for `user.deleted`                                                                                                              |
| `LISTING_SERVICE_URL`                     | listing-service as this service reaches it, for the labels of the post texts (SOC-03), `http://localhost:8082` by default               |

Running it locally (Docker infrastructure up, `.env` copied from `.env.example`):

```bash
cd backend && ./mvnw install -DskipTests                                     # once
(set -a; . ../.env; set +a; ./mvnw -pl social-publisher spring-boot:run)     # 8083
```

## Meta set-up (by the owner of the Meta app)

The exact labels move often in the Meta developer dashboard: check them there.

1. Create an app of type **Business** and add the **Facebook Login for Business** (or Facebook Login) product. Copy the app id and secret to `META_APP_ID` / `META_APP_SECRET` of the environment (`/opt/soukly/<env>/.env` on the VM, the root `.env` locally).
2. In the Facebook Login settings, add the **valid OAuth redirect URI** of each environment: `http://localhost:4200/oauth/facebook/callback` (local), `https://<dev host>/oauth/facebook/callback`, `https://soukly.tn/oauth/facebook/callback`.
3. Fill in the **privacy policy URL** (`/fr/legal/privacy`) and the **data deletion instructions URL** (`/fr/legal/data-deletion`) of LEG-01.
4. While the app is in **development mode**, only people with a role on the app (administrator, developer, tester) can connect a Page: add the testers under *App roles*.
5. Before opening the feature to everyone: **App Review** for `pages_show_list`, `pages_manage_posts` and `pages_read_engagement`, with a screencast of the flow, and the **Business Verification** if Meta asks for it.

### Instagram (SOC-02)

1. In the same Meta app, add the **Instagram** product and choose **API setup with Instagram login**. Copy the **Instagram app id** and **Instagram app secret** to `INSTAGRAM_APP_ID` / `INSTAGRAM_APP_SECRET`.
2. Under *Set up Instagram business login*, add the redirect URI of each environment: `http://localhost:4200/oauth/instagram/callback`, `https://<dev host>/oauth/instagram/callback`, `https://soukly.tn/oauth/instagram/callback`.
3. In development mode, add the Instagram professional accounts of the testers under *App roles › Roles › Instagram Testers*; each tester accepts the invitation in Instagram (*Settings › Website permissions › Apps and websites › Tester invites*).
4. Before opening the feature to everyone: **App Review** for `instagram_business_basic` and `instagram_business_content_publish`, with a screencast.

## Manual test with a Meta tester account

1. Start the infrastructure, user-service, listing-service, social-publisher (with the Meta app of step 1 above), the gateway and the front end (`pnpm nx serve shell`).
2. Sign in to Soukly, open `/fr/account/social` and click **Relier**: Facebook opens with the Soukly app and the three permissions.
3. Sign in to Facebook with a tester account that manages one Page, accept: back on `/fr/account/social`, the card shows the Page name and "reliée le …". In `soukly_social`, `SELECT token_ciphertext FROM social_connections` shows bytes only.
4. With a tester that manages several Pages: the "Choisir une Page" screen lists them; pick one.
5. Click **Annuler** on the Facebook dialog: "Vous avez annulé la connexion". Untick `pages_manage_posts` in *Edit access*: the permission message.
6. **Déconnecter**: the card is back to "Non reliée", and the Soukly app disappears from the tester's *Settings › Business integrations* on Facebook.
7. Repeat from `/ar/account/social`: the page is right to left and Facebook brings the tester back to `/ar/account/social`.

### Instagram (SOC-02)

1. Same services as above, with `INSTAGRAM_APP_ID` / `INSTAGRAM_APP_SECRET` set.
2. On `/fr/account/social`, click **Relier** on the Instagram card: Instagram opens with the Soukly app and its two permissions. Sign in with a tester **Business or Creator** account and allow: back on `/fr/account/social`, the card shows `@username · relié le …`; the Facebook card does not change. In `soukly_social`, `token_ciphertext` shows bytes only and `token_expires_at` is about 60 days ahead.
3. With a tester **personal** account (if Instagram lets it through): the "Compte personnel" screen with the 3 steps.
4. Cancel on the Instagram dialog: "Vous avez annulé la connexion".
5. Refresh: in `soukly_social`, `UPDATE social_connections SET token_expires_at = now() + interval '5 days' WHERE network = 'INSTAGRAM'`, restart social-publisher with `SOUKLY_SOCIAL_INSTAGRAM_REFRESH_INITIAL_DELAY=PT5S`: after a few seconds `token_expires_at` is about 60 days ahead again (the token must be at least 24 hours old). Remove Soukly from the tester's *Apps and websites*, repeat: the card shows "Relier à nouveau".
6. **Déconnecter** Instagram: only the Instagram card goes back to "Non relié".
7. Repeat from `/ar/account/social`: right to left, `@username` stays left to right.

### Publishing on the Page (SOC-04)

1. Same services as above, with listing-service and photos reachable by Meta over **public HTTPS** (dev: the public
   URL of the photo storage; locally the Graph API cannot fetch `localhost` photos, so this test is done in dev).
2. Connect the Page of a tester, publish a listing with 3 photos and "Partager sur Facebook" ticked.
3. Within a few seconds, the Page shows one post with the 3 photos and the text of the preview; the link opens the
   listing with `utm_source=facebook`. In `soukly_social`, the row of `social_posts` is `PUBLISHED` with its
   `permalink`. If Meta refuses the WebP photos, the row is `FAILED` `meta-324` (or another code): note it, a JPEG
   variant is then needed (`PhotoVariantService`, SOC-05).
4. Delete the listing in "Mes annonces": the post disappears from the Page and the row is `DELETED`.

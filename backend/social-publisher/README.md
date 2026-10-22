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

## Configuration

| Variable                                  | Meaning                                                                                                                                   |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `SOCIAL_TOKEN_ENC_KEY`                    | 32 random bytes in base64 (`openssl rand -base64 32`). **Required**: the service does not start without a valid key. Never change it once tokens are stored. |
| `META_APP_ID`, `META_APP_SECRET`          | the Meta app of the environment (one app per environment). Empty: Facebook cannot be connected (`503`).                                  |
| `META_GRAPH_VERSION`                      | Graph API version of the dialog and the calls, `v26.0` by default (latest version in October 2026).                                       |
| `FRONT_BASE_URL` (else `PUBLIC_BASE_URL`) | origin of the redirect URI `{FRONT_BASE_URL}/oauth/facebook/callback`                                                                    |
| `USER_SERVICE_JWKS_URI`, `JWT_ISSUER`     | validation of the access tokens, as in the gateway                                                                                        |
| `KAFKA_BOOTSTRAP_SERVERS`                 | Redpanda, for `user.deleted`                                                                                                              |

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

## Manual test with a Meta tester account

1. Start the infrastructure, user-service, listing-service, social-publisher (with the Meta app of step 1 above), the gateway and the front end (`pnpm nx serve shell`).
2. Sign in to Soukly, open `/fr/account/social` and click **Relier**: Facebook opens with the Soukly app and the three permissions.
3. Sign in to Facebook with a tester account that manages one Page, accept: back on `/fr/account/social`, the card shows the Page name and "reliée le …". In `soukly_social`, `SELECT token_ciphertext FROM social_connections` shows bytes only.
4. With a tester that manages several Pages: the "Choisir une Page" screen lists them; pick one.
5. Click **Annuler** on the Facebook dialog: "Vous avez annulé la connexion". Untick `pages_manage_posts` in *Edit access*: the permission message.
6. **Déconnecter**: the card is back to "Non reliée", and the Soukly app disappears from the tester's *Settings › Business integrations* on Facebook.
7. Repeat from `/ar/account/social`: the page is right to left and Facebook brings the tester back to `/ar/account/social`.

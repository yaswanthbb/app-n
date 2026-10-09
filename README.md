# News App

A single-activity Kotlin / Jetpack Compose Android app (`com.machine.newsapp`) and a small FastAPI publishing backend. Exactly three bottom tabs: **NEWS**, **DEALS**, and **MACHINE'S PICKS**. Material 3 uses a dark theme by default.

## Build and install the APK

Open the repository root in Android Studio. Use **JDK 17**, Android SDK **35**, and build tools **35.0.0**. There is one Gradle module, `app`; the Gradle 8.11.1 wrapper is included. Android 8.0 (API 26) and later are supported.

1. Copy `local.properties.example` to `local.properties`. Let Android Studio set `sdk.dir` to your installed SDK (or set it yourself).
2. Add your GNews key as described below. Blank keys/URLs are supported: the APK builds and displays setup/empty states instead of crashing.
3. Run at the repository root:

```bash
./gradlew assembleRelease
```

The **single, signed, installable APK** is:

```text
app/build/outputs/apk/release/app-release.apk
```

No ABI splits, app bundle, or Firebase account are needed for this command. Install with Android's package installer or:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Release signing

If no signing properties are supplied, Gradle creates `app/local-release.keystore` on the first release build and reuses it. This local install key has alias `newsapp` and password `newsapp-local`. The APK is a release build, with R8/resource shrinking enabled and debugging disabled. Keep this ignored keystore if you want future builds to update the same installation.

For distribution, generate your own private key, then set these **local.properties** values before building:

```properties
RELEASE_STORE_FILE=/absolute/path/to/private-release.jks
RELEASE_STORE_PASSWORD=your-private-password
RELEASE_KEY_ALIAS=newsapp
RELEASE_KEY_PASSWORD=your-private-password
```

Example key generation (keytool prompts for a private password):

```bash
keytool -genkeypair -keystore private-release.jks -alias newsapp -keyalg RSA -keysize 2048 -validity 10000
```

Changing signing keys requires uninstalling the previous APK, which removes its cache. Signing files, `local.properties`, and Firebase configuration are ignored by Git. No publishing token or Firebase Admin credential belongs in the Android project.

## NEWS: API key and filters

This project uses [GNews](https://gnews.io/) and its [documented REST API](https://docs.gnews.io/). Create an account, copy its API key, and put it in **local.properties**:

```properties
NEWS_API_KEY=your_gnews_api_key
```

`app/build.gradle.kts` reads this property and injects it as **BuildConfig.NEWS_API_KEY**. Swap the key here and rebuild; no Kotlin change is needed. BuildConfig values are embedded in an APK, so keep the provider's quotas/restrictions appropriate to your distribution.

GNews' [free plan](https://gnews.io/faq) currently supports development/testing, with 100 requests/day, up to 10 articles/request, and delayed news. Check its terms before a public/commercial release. A free development key is sufficient to test this project; the code also works with paid GNews keys.

Filters are server-side:

| Chip | Request |
| --- | --- |
| All | Technology + World top headlines, merged by URL and sorted newest first |
| Tech | Technology top headlines |
| AI | Search for artificial intelligence or machine learning |
| India | Nation top headlines with country `in` |
| World | World top headlines |

Each filter has its own Room cache. Automatic refreshes reuse a cache younger than 15 minutes; pull-to-refresh and Retry explicitly fetch again. All uses two API requests. Cards show title, source, time ago, a one-line description, and a Coil thumbnail with a placeholder. Taps open Chrome Custom Tabs (or another supported browser).

## DEALS and MACHINE'S PICKS: feed URLs

The two constants live in:

```text
app/src/main/java/com/machine/newsapp/FeedConfig.kt
```

After deploying the backend, set them to your public HTTPS endpoints and rebuild:

```kotlin
const val DEALS_FEED_URL = "https://YOUR-SERVICE.onrender.com/api/deals"
const val PICKS_FEED_URL = "https://YOUR-SERVICE.onrender.com/api/picks"
```

You can also supply any HTTPS JSON host using these exact shapes. No publish token is needed to read the feeds.

**Deals** (`expires` is JSON `null`, not the string `"null"`):

```json
[
  {
    "title": "A free tool for your next project",
    "description": "A free tier for small development projects.",
    "url": "https://example.com/tool",
    "source": "Example",
    "tag": "Dev tools",
    "expires": null,
    "offer_details": "Free access to the tool and its starter templates.",
    "claim_steps": ["Open the offer page", "Create a free account", "Activate the free tier"],
    "id": 37,
    "created_at": "2026-10-09T03:30:00Z"
  }
]
```

Deals are free software offers only. Accepted tags are `AI`, `Dev tools`, `SaaS`, `Software`, `Cloud`, `Data`, `Security`, `Design`, and `Learning`. The backend rejects telco/phone offers and descriptions without an explicit free/no-cost/zero-cost/$0 claim. As the publisher, verify the actual offer is free; text validation cannot verify a third-party pricing page. The app rejects unsupported/telco feed items and hides expired deals after their final local calendar day. Tap a deal card to open its full detail screen. It shows the source, tag, full description, optional **What you get**, numbered **How to claim** steps, and expiry. **Claim** opens the offer URL. `offer_details` and `claim_steps` accept JSON null or can be omitted; missing/empty sections are hidden. `id` and `created_at` are server-generated response fields, not POST input fields.

**Picks**:

```json
[
  {
    "title": "One thing worth your time",
    "body": "Why this launch, tool, idea, or learning resource matters.",
    "url": "https://example.com/resource",
    "date": "2026-10-09",
    "id": 19,
    "created_at": "2026-10-09T03:30:00Z"
  }
]
```

Publish any number of distinct picks for your intended calendar date. The backend returns `409` only when **title + URL + date** match an existing pick; changing the body alone still counts as a duplicate. A different title or URL on the same day is accepted. The feed sorts by date descending, then ID descending, so the newest publication appears first within each day. The app uses item IDs for list, detail, and delete identity, keeps each card’s date visible, and hides future-dated notes. Tap a pick card to open its full dated note and link button. The backend does not generate editorial content or automate publication for you.

### Today / Earlier and local publishing settings

All three tabs group visible items into **Today** and **Earlier**, with item counts in each section header. When Today is empty, a short friendly message replaces that header. News uses `publishedAt`; Deals and Picks use the server's `created_at` UTC timestamp converted to the device's current timezone. A pick's editorial `date` remains visible in its note but does not determine its publish-day group. The UI updates at local midnight, on resume, and as timezone changes are observed. Older cached/externally hosted items with no publish timestamp go to Earlier; no date is invented from cache time.

Use the gear icon to open **Settings**, which is separate from the three bottom tabs. Enter the backend publishing token in the masked field once and tap **Save token**. It is stored in **EncryptedSharedPreferences**, with its encryption key in Android Keystore. The saved value is never loaded back into the UI; entered text is cleared after submission, is not saved in instance state, and Settings blocks screenshots. Credential preferences are excluded from cloud backup and device transfer. You can replace or remove the saved token. It is not in BuildConfig, Room, Git, logs, feed requests, or Firebase messages.

A deal/pick card's overflow menu offers **Delete**, followed by a confirmation dialog. Confirming sends the saved token only on an HTTPS `DELETE /api/deals/{id}` or `DELETE /api/picks/{id}` request. HTTP redirects are disabled for these requests so the token cannot be forwarded to another host. A successful delete removes the cached item immediately without waiting for another refresh. If the server already removed it (404), the stale cached item is removed too. Failed/unauthorized/offline deletes leave the cache intact and show a retryable error. Deletes are never queued while offline.

Legacy cached items without IDs resolve their ID from the updated public GET feed after confirmation. If the server still doesn't return IDs, the dialog asks you to update the backend and refresh. A generic JSON host can still supply read-only feeds/detail screens; deletion requires the publisher's `/api/deals` or `/api/picks` endpoint. This is publisher access: deleting removes content for every reader, not just this device.

All three feeds open from Room without a network connection. Valid refreshes replace their cached JSON atomically, including a legitimate empty array. HTTP, parsing, quota, and connectivity errors preserve the last good cache and show a readable message with Retry. Deals and Picks also support pull-to-refresh.

## Firebase Cloud Messaging setup

1. Create a [Firebase project](https://console.firebase.google.com/) and register an Android app with package **com.machine.newsapp**.
2. Download **google-services.json** and place it at **app/google-services.json**. Use the same Firebase project as your backend service account.
3. Enable the **Firebase Cloud Messaging API (HTTP v1)** in that project's Google Cloud API library. Modern Firebase projects usually already have it enabled.
4. Rebuild/reinstall the APK. The Google Services task runs only when the real configuration file is present; without it, Firebase is disabled and the daily polling digest still works.
5. Open the app and tap **Enable alerts**. Android 13+ asks for notification permission. A denied permission can be re-enabled through the same button, which opens system settings when necessary.
6. The app subscribes to the **deals** and **picks** FCM topics. Subscription work requires connectivity and retries on failure; token changes re-run subscriptions.
7. In Firebase **Project settings → Service accounts**, generate a private Admin SDK service-account key. Set the backend env var **FIREBASE_SERVICE_ACCOUNT_JSON** to the complete JSON contents. Alternatively set **FIREBASE_SERVICE_ACCOUNT_BASE64** to base64-encoded JSON. These are server-only credentials; do not include them in the APK or commit them.
8. Publish a deal or pick using the curl commands below. A `201` response with `"push":"sent"` means Firebase accepted the topic message. Confirm actual delivery on a phone with Google Play services and enabled notifications.

The backend sends a notification payload plus data `{ "tab": "deals" | "picks", ... }`, with the item's title. Background Android handles the notification directly and passes the tab in launcher extras; foreground delivery uses `NewsMessagingService`. Both cold starts and an already-open app navigate to the matching tab and refresh it. These paths follow [Firebase's foreground/background delivery behavior](https://firebase.google.com/docs/cloud-messaging/android/receive-messages). The link is available inside the tab; a notification tap does not automatically launch an external browser.

### Daily fallback and permissions

WorkManager schedules a briefing for the **next 9:00 AM in the phone's local timezone**, then schedules the next local morning after it runs. Time/timezone changes reschedule it; WorkManager persists work across reboots. This avoids the timezone drift of a fixed 24-hour repeat.

The digest runs with or without Firebase. It refreshes all three feeds with a bounded timeout and falls back to Room offline. Counts compare offer URLs and the newest pick's date with the previous delivered digest; headlines include up to two top stories. The first digest counts existing active deals and at most the latest pick; subsequent daily publishing normally yields **N new deals, 1 new pick**. An already-counted pick shows zero; counts are never fabricated. A once-per-local-day guard prevents repeated digests. Digest taps open NEWS.

**9:00 AM is best-effort, not an exact alarm**: Doze, battery policies, a force-stopped app, or lack of notification permission can delay/prevent alerts. No exact-alarm permission is requested. This uses [WorkManager delayed work](https://developer.android.com/develop/background-work/background-tasks/persistent-work/getting-started/define-work).

The app explicitly requests `INTERNET` and, as approved for Android 13+ notifications, `POST_NOTIFICATIONS`. WorkManager/FCM merge their system permissions: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `com.google.android.c2dm.permission.RECEIVE`, and AndroidX's private receiver permission. No location, contacts, phone, camera, microphone, or storage permissions are used.

## Backend: local development

Python 3.12 is recommended. The default local database is SQLite (`backend/newsapp.db`). Supply a private publishing token; startup fails closed if it is missing.

```bash
cd backend
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Edit `.env` with a real `PUBLISH_TOKEN` (for example generate one with `openssl rand -hex 32`), then:

```bash
python migrate.py
uvicorn main:app --env-file .env --host 0.0.0.0 --port 8000 --workers 1
```

Open `http://localhost:8000/docs` for the API schema. The production Android app requires HTTPS feed URLs; use the deployed Render URL for phone testing.

| Endpoint | Authentication | Behavior |
| --- | --- | --- |
| GET /health | Public | `{ "status": "ok" }`, verifies DB connectivity |
| GET /api/deals | Public | Full deals array, latest publication first |
| GET /api/picks | Public | Full picks array, date descending then ID descending |
| POST /api/deals | X-Publish-Token | Validate/store one deal and push to `deals` |
| POST /api/picks | X-Publish-Token | Validate/store a pick (multiple per day) and push to `picks` |
| DELETE /api/deals/{id} | X-Publish-Token | Remove a deal; 204 on success, 404 if absent |
| DELETE /api/picks/{id} | X-Publish-Token | Remove a pick; 204 on success, 404 if absent |

Successful POST: `201 { "item": { ... }, "push": "sent" | "queued" | "not_configured" }`. Missing/wrong token returns `401`, invalid content returns `422`, and duplicate deal URL or exact pick title + URL + date returns `409` without a second push.

Content and a push-outbox row commit in the **same database transaction**. The API tries FCM immediately. Firebase failures leave a queued push; the single-process retry task checks every minute with increasing backoff. Without Firebase credentials, content is still available and pushes remain queued. Adding credentials and restarting dispatches those pending pushes. Malformed credentials fail startup with a configuration error. Delivery is at least once: a crash after FCM accepts a message but before recording success can cause a retry. Android uses a stable notification tag for each published item.

Run **one Uvicorn worker**, as configured on Render, to serialize outbox delivery. Free-tier service sleep can defer retries until the next request wakes the process. Neither the publisher nor FCM guarantees delivery to a phone that has disabled notifications or force-stopped the app.

## Migrate the existing Neon database before deploying

Migration execution is **manual**. The updated backend verifies its Alembic revision at startup and fails with a clear migration instruction if the database is still on the old schema. It does not execute DDL on startup or silently create a replacement database. Run migration **0003_multiple_picks** before deploying this backend. If you already ran 0002, the command applies only 0003.

The real migrations live in `backend/migrations/versions/`:

- **0001_initial** adopts the existing original `deals`, `picks`, and `push_outbox` tables without dropping/recreating them; it creates these tables for a new database. It checks existing tables for required original columns.
- **0002_deal_details** adds nullable `deals.offer_details` (**TEXT**) and `deals.claim_steps` (**JSON**). Existing values remain NULL; content, IDs, creation timestamps, and pending pushes are preserved.
- **0003_multiple_picks** removes the unique date constraint from `picks` and adds `UNIQUE (title, url, date)`. Neon uses transactional ALTER TABLE statements without changing existing rows. SQLite uses Alembic batch migration while preserving row values and IDs.

Pending migrations and their version record run in one transaction. Postgres migrations use a transaction-scoped advisory lock to serialize concurrent migration commands. They can be run again safely. **Do not use `alembic stamp head` to skip the migration**: it marks a version without adding the columns. An incompatible existing schema fails before being marked current.

Run these commands from the updated checkout on your computer, using the intended Neon connection (a direct/non-pooled connection is preferable for schema maintenance; see [Neon connection guidance](https://neon.com/docs/connect/connection-pooling)). Keep the connection value in the environment or private `backend/.env`, never in Git or chat:

```bash
cd backend
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
# Set DATABASE_URL to your existing Neon connection including sslmode=require.
# You may instead place DATABASE_URL in the ignored backend/.env file.
export DATABASE_URL='postgresql://USER:PASSWORD@NEON_HOST/DB_NAME?sslmode=require'
python migrate.py --require-postgres
```

`--require-postgres` refuses to accidentally migrate a local SQLite fallback. `migrate.py` loads a local `.env` only for variables not already set in the environment. Success prints `Migration complete: 0003_multiple_picks (postgresql)` without printing the URL or credentials.

Verify the added columns and revision in Neon's SQL editor if desired:

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'deals'
  AND column_name IN ('offer_details', 'claim_steps');
SELECT version_num FROM alembic_version;
-- Expected revision: 0003_multiple_picks
SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid = 'public.picks'::regclass AND contype = 'u';
-- Expected: UNIQUE (title, url, date), with no UNIQUE (date).
```

Run the migration **before deploying the updated backend**. The old Android APK rejects feeds with multiple same-day picks, so install the updated APK before publishing additional notes on one date. Migration 0003 changes constraints only on Neon, preserving existing rows, IDs, dates, bodies, and creation timestamps. Its downgrade refuses to restore date uniqueness while multiple rows share a day; it never deletes notes to make a downgrade fit.

## Deploy the backend to Render's free tier

The existing **render.yaml** keeps a free Python web service and accepts an externally managed **DATABASE_URL** (your Neon database). It does not provision a new Render database. Preserve your current Render **DATABASE_URL**, **PUBLISH_TOKEN**, and **FIREBASE_SERVICE_ACCOUNT_JSON**.

1. Run the Neon migration above yourself.
2. Publish the updated repository to your Git host yourself, then deploy the updated backend to your existing Render service.
3. Render installs `backend/requirements.txt` and runs `uvicorn main:app --host 0.0.0.0 --port $PORT --workers 1`. Startup checks that the database has revision `0003_multiple_picks`.
4. Visit `https://YOUR-SERVICE.onrender.com/health` and confirm `{"status":"ok"}`.
5. Check `/api/deals` and `/api/picks`: responses now include `id` and ISO-8601 UTC `created_at`; deals also include nullable `offer_details` and `claim_steps`.
6. Rebuild/install the Android APK using the same signing key. Enter **PUBLISH_TOKEN** in the app's Settings only if you want publisher deletion access.

For a fresh service, use **New → Blueprint**, select this repository, and supply the existing Neon URL and credentials as environment variables. Migrate that database before starting the service. If Firebase isn't configured yet, the backend's publishing/feeds still work and topic pushes remain queued.

Render's [free web service](https://render.com/docs/free) may sleep after idle time and uses an ephemeral disk. Continue using Neon for durable storage; SQLite is rejected on Render. The app's HTTP timeouts allow the web service time to wake up. See [Render Blueprint syntax](https://render.com/docs/blueprint-spec) for configuration changes.

### Exact publishing commands

Set these locally for curl (never hardcode or commit the token in the Android project):

```bash
export API_BASE='https://YOUR-SERVICE.onrender.com'
export PUBLISH_TOKEN='YOUR_PRIVATE_TOKEN_FROM_RENDER'
# For local testing instead: export API_BASE='http://localhost:8000'
```

Publish one free deal:

```bash
curl --fail-with-body -X POST "$API_BASE/api/deals" \
  -H "X-Publish-Token: $PUBLISH_TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "title": "A free AI model for your next project",
    "description": "A free model you can try at no cost. Replace this example with a verified real offer.",
    "url": "https://example.com/free-model",
    "source": "Example",
    "tag": "AI",
    "expires": null,
    "offer_details": "Free local model usage and downloadable examples.",
    "claim_steps": ["Open the model page", "Download the model", "Follow the included setup instructions"]
  }'
```

Publish a pick (repeat with distinct titles or URLs for more notes on the same date; uses your shell's local calendar date):

```bash
PICK_DATE="$(date +%F)"
curl --fail-with-body -X POST "$API_BASE/api/picks" \
  -H "X-Publish-Token: $PUBLISH_TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @- <<JSON
{
  "title": "A resource worth ten minutes today",
  "body": "Replace this example with the one thing you want readers to explore today and explain why it matters.",
  "url": "https://example.com/todays-pick",
  "date": "$PICK_DATE"
}
JSON
```

Check three distinct notes for one date (replace example content and URLs before publishing):

```bash
PICK_DATE="$(date +%F)"
for NUMBER in 1 2 3; do
  curl --fail-with-body -w '\nHTTP %{http_code}\n' -X POST "$API_BASE/api/picks" \
    -H "X-Publish-Token: $PUBLISH_TOKEN" \
    -H 'Content-Type: application/json' \
    --data-binary "{\"title\":\"Same-day note $NUMBER\",\"body\":\"Curated note $NUMBER\",\"url\":\"https://example.com/note-$NUMBER\",\"date\":\"$PICK_DATE\"}"
done
# Expected: 201 for each distinct note. Repeating this loop returns 409.
curl --fail-with-body "$API_BASE/api/picks"
# Within PICK_DATE, IDs appear in descending order: note 3, note 2, note 1.
```

Inspect the public feeds:

```bash
curl --fail-with-body "$API_BASE/api/deals"
curl --fail-with-body "$API_BASE/api/picks"
curl --fail-with-body "$API_BASE/health"
```

Replace example content/URLs before publishing. Repeating the same deal URL or the same pick title + URL + date intentionally returns `409`. Distinct picks on the same date return `201`.

Delete an item using the `id` returned by GET or POST:

```bash
DEAL_ID=37
PICK_ID=19
curl --fail-with-body -X DELETE "$API_BASE/api/deals/$DEAL_ID" \
  -H "X-Publish-Token: $PUBLISH_TOKEN"
curl --fail-with-body -X DELETE "$API_BASE/api/picks/$PICK_ID" \
  -H "X-Publish-Token: $PUBLISH_TOKEN"
```

Success is HTTP 204 with no response body. Nonexistent IDs return 404; missing/wrong tokens return 401. Pending pushes for an unambiguously identified deleted item are removed; already-delivered phone notifications cannot be recalled.


## Validation

Android:

```bash
./gradlew assembleRelease testDebugUnitTest lintRelease
```

Compose rendering tests exercise old/new deal details, full pick notes, card-to-detail navigation, and confirmation-before-deletion. Focused JVM tests cover local-midnight/DST grouping, nullable fields, successful and failed delete/cache behavior, and cache preservation after failure/restart, separate filter caches and freshness, invalid feed rejection, multiple same-day picks, unique item identities, legitimate empty feeds, URL validation, expiry boundaries, time-ago formatting, and the next-local-9:00 schedule across daylight saving changes.

Backend:

```bash
cd backend
pip install -r requirements-dev.txt
python -m pytest tests -q
```

API tests use temporary SQLite databases and a fake FCM sender. Migration tests run against SQLite and, when `TEST_POSTGRES_URL` is supplied, isolated schemas in a local test Postgres database. They verify original-row/ID preservation, NULL defaults, repeatable migrations, removal of date uniqueness in 0003, exact-duplicate constraints, safe downgrade refusal, fresh database setup, and transaction rollback on incompatible schemas. The Postgres test fixture creates and drops only its own temporary schema; never point test configuration at your live Neon database. Tests also cover new deal-field round trips, authenticated DELETE/204/404 behavior, and cancellation of unsent pushes. They cover authentication, exact feed shapes, categories/free claims, invalid dates/URLs, duplicate protection, persistence across restarts, push failures/retries, and operation without Firebase. Live GNews/FCM testing needs your own key and project credentials.

Manual phone smoke test: install the release APK, visit each tab and news filter, refresh populated feeds, open an article/Claim/pick, turn networking off and relaunch to verify cached content, publish a new deal/pick while the app is foregrounded and backgrounded, and verify each notification tap. Enable/deny notification permission to check both paths. Test the morning digest on a device, allowing for Android's scheduling delays.

## Project structure

```text
app/src/main/java/com/machine/newsapp/
  FeedConfig.kt                  Feed URL constants
  MainActivity.kt                Single activity and notification intents
  NewsApplication.kt             App container / Room / Retrofit
  data/                          DTOs, API, repository, Room cache
  ui/                            MVVM, Compose screens/navigation, theme, browser
  notifications/                 FCM, topic retry work, daily scheduler/digest
app/src/test/                    Focused JVM regression tests
app/schemas/                     Versioned Room schema
backend/                         FastAPI, SQLAlchemy, Admin SDK, outbox, API tests
backend/migrate.py               Manual schema migration command
backend/migrations/              Versioned Alembic schema migrations
render.yaml                      Free Render service using external Neon Postgres
```

Existing feed URLs, local news/Firebase configuration, and signing keys are preserved. Supply credentials locally as needed; the publishing token is entered through Settings and never committed. Apply the Neon migration before backend deployment; repository publishing and deployment remain under your control.

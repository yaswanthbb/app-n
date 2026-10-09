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
    "expires": null
  }
]
```

Deals are free software offers only. Accepted tags are `AI`, `Dev tools`, `SaaS`, `Software`, `Cloud`, `Data`, `Security`, `Design`, and `Learning`. The backend rejects telco/phone offers and descriptions without an explicit free/no-cost/zero-cost/$0 claim. As the publisher, verify the actual offer is free; text validation cannot verify a third-party pricing page. The app rejects unsupported/telco feed items and hides expired deals after their final local calendar day. The tag pill and **Claim** button open the offer URL.

**Picks**:

```json
[
  {
    "title": "One thing worth your time",
    "body": "Why this launch, tool, idea, or learning resource matters.",
    "url": "https://example.com/resource",
    "date": "2026-10-09"
  }
]
```

Publish one pick each day using your intended calendar date. The backend rejects a second pick for that date (`409`); the app validates unique dates, hides future-dated notes, and renders newest first. The backend does not generate editorial content or automate publication for you.

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
uvicorn main:app --env-file .env --host 0.0.0.0 --port 8000 --workers 1
```

Open `http://localhost:8000/docs` for the API schema. The production Android app requires HTTPS feed URLs; use the deployed Render URL for phone testing.

| Endpoint | Authentication | Behavior |
| --- | --- | --- |
| GET /health | Public | `{ "status": "ok" }`, verifies DB connectivity |
| GET /api/deals | Public | Full deals array, latest publication first |
| GET /api/picks | Public | Full picks array, date descending |
| POST /api/deals | X-Publish-Token | Validate/store one deal and push to `deals` |
| POST /api/picks | X-Publish-Token | Validate/store one daily pick and push to `picks` |

Successful POST: `201 { "item": { ... }, "push": "sent" | "queued" | "not_configured" }`. Missing/wrong token returns `401`, invalid content returns `422`, and duplicate deal URL or pick date returns `409` without a second push.

Content and a push-outbox row commit in the **same database transaction**. The API tries FCM immediately. Firebase failures leave a queued push; the single-process retry task checks every minute with increasing backoff. Without Firebase credentials, content is still available and pushes remain queued. Adding credentials and restarting dispatches those pending pushes. Malformed credentials fail startup with a configuration error. Delivery is at least once: a crash after FCM accepts a message but before recording success can cause a retry. Android uses a stable notification tag for each published item.

Run **one Uvicorn worker**, as configured on Render, to serialize outbox delivery. Free-tier service sleep can defer retries until the next request wakes the process. Neither the publisher nor FCM guarantees delivery to a phone that has disabled notifications or force-stopped the app.

## Deploy the backend to Render's free tier

The repository includes **render.yaml** at its root. It provisions a free Python web service and a free Postgres instance; the Android project is not part of the Render build.

1. Publish the repository to your Git host yourself.
2. In Render, select **New → Blueprint**, connect the repository, and select its `render.yaml`.
3. Supply **PUBLISH_TOKEN** and **FIREBASE_SERVICE_ACCOUNT_JSON** when prompted. Use the same Firebase project registered in the app. If Firebase is not ready, remove just the Firebase env-var entry from the blueprint before deploying, then add it in Render's Environment settings later; polling and publishing work without it.
4. Render sets **DATABASE_URL** from Postgres and runs `pip install -r requirements.txt` in `backend`, followed by `uvicorn main:app --host 0.0.0.0 --port $PORT --workers 1`.
5. Visit `https://YOUR-SERVICE.onrender.com/health` and confirm `{"status":"ok"}`. The backend creates the tables at startup.
6. Set the Android `DEALS_FEED_URL` and `PICKS_FEED_URL` constants to this service's `/api/deals` and `/api/picks` endpoints, rebuild, and install.
7. Run the publish commands below. Verify GET feeds update and notifications open the right tab.

Render's [free-tier limitations](https://render.com/docs/free) matter: free web services sleep after idle time, their disks are ephemeral, and free Postgres instances currently **expire after 30 days**. SQLite is therefore rejected when running on Render. For continued operation, upgrade/export the database before it expires or use an external persistent Postgres instance: replace the blueprint's `DATABASE_URL` entry with `sync: false`, remove the `databases` block, and supply that instance's connection URL. The free web-service configuration can stay unchanged. See [Render Blueprint syntax](https://render.com/docs/blueprint-spec) for infrastructure changes.

### Exact publishing commands

Set these locally (never put the token in the Android app):

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
    "expires": null
  }'
```

Publish today's single pick (uses your shell's local calendar date):

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

Inspect the public feeds:

```bash
curl --fail-with-body "$API_BASE/api/deals"
curl --fail-with-body "$API_BASE/api/picks"
curl --fail-with-body "$API_BASE/health"
```

Replace example content/URLs before publishing. Repeating the same deal URL or pick date intentionally returns `409`.

## Validation

Android:

```bash
./gradlew assembleRelease testDebugUnitTest lintRelease
```

Focused JVM tests cover cache preservation after failure/restart, separate filter caches and freshness, invalid feed rejection, unique pick dates, legitimate empty feeds, URL validation, expiry boundaries, time-ago formatting, and the next-local-9:00 schedule across daylight saving changes.

Backend:

```bash
cd backend
pip install -r requirements-dev.txt
python -m pytest tests -q
```

API tests use temporary SQLite databases and a fake FCM sender. They cover authentication, exact feed shapes, categories/free claims, invalid dates/URLs, duplicate protection, persistence across restarts, push failures/retries, and operation without Firebase. Live GNews/FCM testing needs your own key and project credentials.

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
render.yaml                      Free Render web service + Postgres blueprint
```

No feed URLs, news API key, Firebase credentials, or public deployment have been invented. Configure those values to connect the completed app to your content.

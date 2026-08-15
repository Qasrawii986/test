# SMS Expense Tracker

Native Android app (Kotlin + Jetpack Compose) that watches incoming bank SMS,
detects payment messages, shows a floating bubble over any app, and records the
expense with a single tap.

```
Incoming SMS → BroadcastReceiver → merge multipart parts → IngestPaymentMessageUseCase
   → SmsParser (positive/negative signals + confidence) → dedup (SHA-256 unique key)
   → Room (UNCATEGORIZED) → BubbleService (overlay) → user taps a category
   → CATEGORIZED + sync queue → WorkManager → POST /payments (optional)
```

## Requirements

| | |
|---|---|
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 35 (Android 15) |
| JDK | 17+ (project built & tested with 21) |
| Android Studio | Ladybug or newer (AGP 8.7) |

## Open & build

1. **Android Studio** → *Open* → select the `SmsExpenseTracker` folder (not the repo root).
2. Let Gradle sync (downloads dependencies).
3. Run ▶ on a device/emulator, or:

```bash
./gradlew :app:assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # unit tests (parser, DB, dedup, viewmodels)
./gradlew :app:connectedDebugAndroidTest  # UI tests (needs device/emulator)
```

## Release APK & signing

```bash
./gradlew :app:assembleRelease
```

Releases are signed with a **stable key** so the app can update itself in place —
Android refuses to update an installed app with a differently-signed APK, and a
debug keystore is regenerated per machine, which would force an uninstall (and
data loss) on every rebuild.

- `signing/release.jks.gpg` — the keystore, GPG-encrypted (safe for a public repo).
- `signing/release.jks` + `signing/keystore.properties` — plaintext, git-ignored.
- CI decrypts the keystore with the `KEYSTORE_PASSPHRASE` repository secret.

To build a signed release locally:

```bash
gpg --batch --yes --decrypt --passphrase "$KEYSTORE_PASSPHRASE" \
  --output signing/release.jks signing/release.jks.gpg
printf 'storePassword=%s\nkeyPassword=%s\nkeyAlias=smsexpense\nstoreFile=signing/release.jks\n' \
  "$KEYSTORE_PASSPHRASE" "$KEYSTORE_PASSPHRASE" > signing/keystore.properties
./gradlew :app:assembleRelease
```

Without the keystore the release build still works — it falls back to debug
signing (fine for local testing, not for updating an installed copy).

## Automatic builds & in-app updates

`.github/workflows/android-release.yml` runs on every push that touches this
project: it runs the unit tests, builds a signed release APK, and publishes a
GitHub Release with two fixed-name assets. Fixed names keep these URLs valid
forever:

```
https://github.com/<owner>/<repo>/releases/latest/download/update.json
https://github.com/<owner>/<repo>/releases/latest/download/app-release.apk
```

**Settings → About → Check for updates** reads `update.json`, compares
`versionCode` with the running build, downloads the APK, and installs it through
the `PackageInstaller` session API (`ACTION_INSTALL_PACKAGE` has been deprecated
since API 29; the session API also avoids needing a FileProvider). The app
requests `REQUEST_INSTALL_PACKAGES`, and the user grants "install unknown apps"
once. Android always shows its own install confirmation — that is mandatory for
a non-system app.

One-time repository setup: add a secret named `KEYSTORE_PASSPHRASE`
(Settings → Secrets and variables → Actions) holding the keystore passphrase.

To publish a new version, bump `versionCode` **and** `versionName` in
`app/build.gradle.kts` and push — the workflow tags `v<versionName>` and
replaces the release if that tag already exists.

### Developer screen in release builds

`BuildConfig.DEBUG` is false in release, so the debug icon is hidden. Tap the
version line in **Settings → About** seven times to open the developer screen.

## First-run setup (on the phone)

1. **Permissions onboarding** appears on first launch:
   - *SMS access* → needed to see the bank messages (processed on-device only).
   - *Display over other apps* → opens system settings; enable it for the bubble.
   - *Notifications* (Android 13+) → for the short-lived foreground service behind the bubble.
2. **Settings → Bank Sender IDs** → add your bank's SMS sender name exactly as it
   appears in your Messages app (e.g. `MYBANK`). You can add several. Only these
   senders are parsed. Matching is case-insensitive.
3. **Settings → Currency** → default currency used when an SMS has an amount but
   no currency token.
4. **Categories** (icon in the top bar) → add/edit/delete/reorder your expense
   groups. Five sensible defaults are seeded on first run.

## Testing without a real bank SMS

Open the **Debug screen** (bug icon in the dashboard top bar — visible in
**debug builds only**):

- **Simulate SMS** — sends a message through the *real* ingestion pipeline
  (sender filter → parser → threshold → dedup → Room → bubble). Uncheck
  *Respect Sender ID filter* if you haven't configured senders yet.
- **Test parser only** — shows what the parser extracts without saving anything.
- **Trigger bubble / Fake payment / Test API / Sync now / Clear DB** — each button
  is wired to the real logic, not stubs.

Sample messages the parser understands out of the box:

```
تم خصم 12.50 JOD من بطاقتك لدى Coffee Shop
تمت عملية شراء بقيمة 15.750 دينار
Purchase of JOD 25.00 at SuperMart
Your card was charged 18.50 JOD
تم استخدام البطاقة بمبلغ 32.00
```

Salary/transfer/deposit/OTP/refund messages are recognized as **not** payments
(negative signals). The confidence threshold is adjustable in Settings → Parsing.

## Choosing the Sender ID from your SMS (v1.1)

Besides typing the Sender ID manually, **Settings → Bank Sender IDs → Choose
from SMS** opens an in-app picker over your device inbox: search, multi-select
messages, and the distinct sender IDs are extracted. If the selection spans
several senders, you confirm which ones to keep before anything is added.

> Android note: there is **no system "pick an SMS" intent** (unlike the contacts
> picker), so an in-app list backed by the `READ_SMS` permission and the
> Telephony content provider is the only supported way to do this. The
> permission is requested only when you open this feature.

## Historical import (v1.1)

**Settings → Import → Import Historical Transactions** (also offered during
first-run setup, and always skippable):

1. Pick the bank sender(s) and a period — presets (last month / 3 / 6 / 12) or
   custom From/To dates.
2. **Scan SMS** reads the inbox in the background with live progress
   (messages scanned / transactions found). The scan uses the **exact same
   parser, confidence threshold and dedup key** as the realtime receiver.
3. **Review screen**: everything is selected by default; toggle rows,
   Select All / Deselect All, edit each transaction's category, or use
   **Set Category** to bulk-assign the selected rows. The bar shows the
   selected count and total.
4. **Import Selected** asks for confirmation, then saves to Room. Imported
   payments join the dashboard, monthly stats, category totals and the normal
   sync queue (`syncStatus = PENDING`).

Re-importing the same period is **idempotent**: exact duplicates are blocked by
the dedup key's unique index, and messages already captured live by the
receiver are also skipped via a same-sender+body+amount match within ±12h
(the SMSC timestamp on a received SMS differs slightly from the inbox
timestamp). Each successful import is recorded in **Import History**.

The daily flow (SMS → bubble → one tap) is untouched — historical review never
appears for new incoming messages.

## Server sync (optional, off by default)

The app is **offline-first**: Room is the source of truth and nothing requires
internet. To enable sync:

1. Settings → Server → toggle *Sync payments to server*.
2. Set **Base URL** (e.g. `https://myserver.com/api`) — the app POSTs to
   `<baseUrl>/payments` with JSON:

```json
{
  "amount": 12.5,
  "currency": "JOD",
  "merchant": "Coffee Shop",
  "category": "Food",
  "timestamp": "2026-08-09T20:30:00",
  "sender": "MYBANK",
  "originalMessage": "..."
}
```

3. Optional auth token is sent as `Authorization: Bearer <token>`.

Each payment carries a `syncStatus` (`PENDING` / `SYNCED` / `FAILED` /
`DISABLED`); WorkManager retries failed uploads with exponential backoff when
the network returns. Swapping the backend, auth scheme, or headers only touches
`ApiConfigProvider` / `HttpPaymentApiClient`.

## Architecture

```
app/src/main/java/com/smsexpense/tracker/
├── data/
│   ├── local/        Room: entities, DAOs, database
│   ├── remote/       PaymentApiClient abstraction + OkHttp impl + DTO
│   └── repository/   Room/DataStore implementations of domain interfaces
├── domain/
│   ├── model/        Payment, Category, PaymentCandidate, IncomingMessage, ...
│   ├── parser/       SmsParser (signals + confidence), AmountNormalizer, DedupKey
│   ├── repository/   Repository interfaces (domain never sees Room)
│   ├── source/       PaymentMessageSource abstraction (SMS today, notifications later)
│   └── usecase/      IngestPaymentMessageUseCase, SyncPaymentsUseCase
├── service/
│   ├── receiver/     SmsReceiver (manifest-registered, multipart-aware)
│   ├── bubble/       BubbleService (FGS + WindowManager overlay, Compose UI)
│   └── sync/         SyncWorker + SyncScheduler (WorkManager)
└── ui/               Compose screens: dashboard, payment detail, categories,
                      settings, debug, onboarding + shared components/theme
```

UI → ViewModel → Repository (interface) → DAO → Room. The parser and ingestion
pipeline are pure Kotlin (JVM-testable, no Android deps).

### Platform notes (verified, not assumed)

- **FGS from background (Android 12+):** starting the bubble service from the
  SMS broadcast is allowed both because the receiver grants a temporary
  allowlist window and because `SYSTEM_ALERT_WINDOW` is a documented exemption.
  `ForegroundServiceStartNotAllowedException` is still caught — worst case the
  payment just stays uncategorized in the app.
- **Android 14+ FGS types:** the service declares
  `foregroundServiceType="specialUse"` with the required
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation and the
  `FOREGROUND_SERVICE_SPECIAL_USE` permission.
- **Google Play:** `RECEIVE_SMS` is heavily restricted on Play. This app is
  designed for personal sideloading; the `PaymentMessageSource` abstraction
  exists so a `NotificationListenerService` source can replace SMS later
  without touching the domain layer.
- **Security:** SMS bodies are only logged in debug builds (`AppLog`); data
  stays on-device unless you explicitly enable sync.

## Tests

91 unit tests run on the JVM (no device needed):

- `SmsParserTest` — Arabic/English payments, currencies, decimal separators,
  Arabic-Indic digits, multipart bodies, merchants, salary/transfer/OTP/refund
  negatives, unknown formats, confidence.
- `AmountNormalizerTest`, `DedupKeyTest`
- `PaymentDaoTest` / `CategoryDaoTest` — Robolectric + in-memory Room: insert,
  dedup unique index, monthly totals, category totals, cascade on delete.
- `IngestPaymentMessageUseCaseTest` — sender filter, threshold, duplicates,
  default currency fallback.
- `SyncPaymentsUseCaseTest`, `DashboardViewModelTest`
- `ImportHistoricalTransactionsUseCaseTest` — date/sender filtering, parser
  reuse, default currency, import + history record, **re-import of the same
  period yields 0 new rows**, cross-source timestamp-drift dedup.
- `SenderPickerViewModelTest` — single/multi sender extraction, confirmation
  flow, no duplicate sender IDs.
- `HistoricalImportViewModelTest` — selection, select-all toggle, bulk
  category, per-item category, confirmation, sync hook.
- `MainFlowTest` (androidTest) — full UI flow on a device: create category →
  simulate payment → categorize → dashboard updates.

## Known limitations

- The bubble uses a foreground-service notification; on Android 13+ without the
  notification permission the bubble still works but the status notification is hidden.
- Amounts are stored as `Double` — fine for personal expense tracking.
- Merchant extraction is heuristic; unrecognized formats still create the
  payment, just without a merchant.
- Release APK is unsigned until you add your own keystore.

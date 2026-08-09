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

## Release APK

```bash
./gradlew :app:assembleRelease
```

The output is **unsigned**. For sideloading, either add a `signingConfig` in
`app/build.gradle.kts` or sign manually with `apksigner`. Debug APKs are signed
automatically with the debug key and install directly.

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

69 unit tests run on the JVM (no device needed):

- `SmsParserTest` — Arabic/English payments, currencies, decimal separators,
  Arabic-Indic digits, multipart bodies, merchants, salary/transfer/OTP/refund
  negatives, unknown formats, confidence.
- `AmountNormalizerTest`, `DedupKeyTest`
- `PaymentDaoTest` / `CategoryDaoTest` — Robolectric + in-memory Room: insert,
  dedup unique index, monthly totals, category totals, cascade on delete.
- `IngestPaymentMessageUseCaseTest` — sender filter, threshold, duplicates,
  default currency fallback.
- `SyncPaymentsUseCaseTest`, `DashboardViewModelTest`
- `MainFlowTest` (androidTest) — full UI flow on a device: create category →
  simulate payment → categorize → dashboard updates.

## Known limitations

- The bubble uses a foreground-service notification; on Android 13+ without the
  notification permission the bubble still works but the status notification is hidden.
- Amounts are stored as `Double` — fine for personal expense tracking.
- Merchant extraction is heuristic; unrecognized formats still create the
  payment, just without a merchant.
- Release APK is unsigned until you add your own keystore.

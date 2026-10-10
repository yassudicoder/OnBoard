# CLAUDE.md — Safar Saathi

Hackathon project for **Elevate 1.0 (DJSCE), Problem Statement 01 — "Seamless Aviation Journey Experience"**.
4-person college team, strong in **Java**. This file is the condensed project brief. Every session follows it.
If a decision changes, update this file in the same change.

- App name **Safar Saathi** · Android package / applicationId `com.safarsaathi.app` · backend base package `com.safarsaathi.backend`
  (names from the brief's placeholder; if the team picks a final name, rename everywhere in one go).

---

## 0. How we work
1. **Phase by phase** (§9). Start of a phase: show a short plan (files to create/change). End of a phase: build, run tests,
   give a short summary (what works, how to try it, known gaps), then **STOP and wait for the go-ahead**.
2. Explain anything non-obvious in plain English — in code comments and in replies. No heavy jargon.
3. **Never commit secrets.** Android keys → `android/local.properties` (git-ignored) → `BuildConfig` / manifest placeholders.
   Backend keys → environment variables (or a git-ignored root `.env`); `.env.example` lists names only.
4. **Every external service sits behind an interface with a mock implementation that needs no key.** The whole demo must run
   with zero API keys (mock data). The backend logs at startup which providers are MOCK vs REAL.
5. Simple, readable code over clever code. **Java only** in app code (Gradle Kotlin DSL build files are fine). No Hilt.
6. Unsure about a library version or API detail → check the official docs; **pin exact versions**.

---

## 1. Product
A mobile-first Android app that guides a traveller through the whole journey:
plan → book → prepare → go to airport → inside airport → flight & connections → disruptions → arrival → return.
Works in the traveller's language and degrades gracefully offline.

**Core idea — a trip is a chain of linked blocks (dominoes).**
- **Block** = one booking/event with a time (flight, cab, meeting, hotel, dinner…).
- **Link** = "B starts at least N minutes after A ends" (+ travel time between them).
- A block moves (flight delay) → **Ripple engine** walks the links and finds what breaks → **Plan B generator** builds and
  scores fix options → **Booking orchestrator** applies the chosen fix to every affected booking (flight, hotel, cab, meeting,
  dinner) in the right order, with rollback.

### Must-have features (judges check these)
| # | Feature | Short description |
|---|---|---|
| F1 | Purpose of travel | Business / Wedding-Family event / Holiday / Pilgrimage / Medical / Exam-Interview. Sets the **anchor** block (must not miss) and the scoring weights. |
| F2 | Door-to-door directions | 6 legs: home→airport, entry→gate, gate→gate (connections), plane→exit, airport→place, around the city. Show only the current leg. |
| F3 | Entire plan + real-time plan | Timeline screen (all blocks) + "Do this now" card (next unfinished block + one action). Same data, two views. |
| F4 | Disruption ripple | Detect delay/cancel/gate change → recalc all downstream blocks → status per block: OK / AUTO-ADJUSTED / NEEDS USER TAP. |
| F5 | Fallback plans | 2–3 scored options, explained in plain language in the user's language; one tap to apply. Plan B precomputed and stored offline. |
| F6 | Booking changes | Adapters change flight seat, hotel, cab, meeting (Google Calendar), restaurant. Scarcest first. Rollback on failure. Ready-made WhatsApp/email message when no API exists. |
| F7 | True cost compare + offers | Fare + bag fee + cab cost at that hour + seat − card offers the user holds = real price. Labels: Cheapest / Fastest / Least stress. |
| F8 | Offline locker | Boarding pass (barcode scan), tickets, ID, full plan, Plan B, terminal map — all readable with no internet. |
| F9 | Multilingual | UI in English, Hindi, Marathi, Tamil, Bengali. Chat accepts Hinglish/mixed input. Voice input/output. Camera sign translation (stretch). |
| F10 | Personas | First-time flyer / Family / Business / Elderly-accessibility. 3 onboarding questions change home-screen hints, text size and tone. |

**Out of scope — do NOT build:** real payments, real ticket booking with airlines, real indoor positioning, login with airline
accounts, all airports (we map **one** terminal: Mumbai T2 or Delhi T1).

---

## 2. Tech stack (locked)

### Android app — `/android`
- Java 17 · XML layouts · Material Components with a **Material 3** theme · based on the "Empty Views Activity" template.
- **minSdk 26**, compile/target = latest stable. Gradle **Kotlin DSL** build files + version catalog `gradle/libs.versions.toml`.
- **MVVM:** Activity/Fragment → ViewModel + LiveData → Repository → (Room | Retrofit). Manual DI with a `ServiceLocator` class (**no Hilt**).
- Jetpack Navigation + bottom nav: **Home** ("Do this now") · **Trip** (timeline) · **Assistant** (chat/voice) · **Wallet** (offline locker) · **Profile**.
- Room (trip, blocks, links, documents, planB, offers cache) · Retrofit + OkHttp + Gson · WorkManager (plan refresh every 15 min, reminders) · Firebase Cloud Messaging.
- Google Maps SDK for Android. **Terminal map = our own vector drawable/SVG in a custom `View`** with tappable points (gates, security, washrooms) and walking times.
- CameraX + Google ML Kit: barcode scanning (PDF417/Aztec/QR boarding passes), text recognition (Latin + Devanagari), on-device translation, language identification.
- Voice: Android `SpeechRecognizer` (hi-IN, mr-IN, ta-IN, bn-IN, en-IN) + `TextToSpeech`. Optional cloud upgrade via a backend proxy to Sarvam AI.
- Boarding pass: decode the IATA **BCBP** string from the barcode → name, PNR, from/to, flight, date, seat.
- Localization: `values/strings.xml` + `values-hi`, `values-mr`, `values-ta`, `values-bn`. In-app language switcher via `AppCompatDelegate.setApplicationLocales`.

### Backend — `/backend`
- **Spring Boot 3** (Java 17+), Maven (wrapper). Packages under `com.safarsaathi.backend`:
  `trip`, `ripple`, `planb`, `orchestrator`, `adapters`, `mock`, `watcher`, `ai`, `notify`, `cost`, `demo` (+ `config`, `health`).
- PostgreSQL via Spring Data JPA, **Flyway** migrations. Local: root `docker-compose.yml`. Hosted: Supabase Postgres (connection string via env var).
- Spring `@Scheduled` flight watcher: every **5 min** in prod mode, every **30 s** in demo mode.
- AI: Google Gen AI Java SDK (Gemini). Model from env `GEMINI_MODEL` (default `gemini-3.8-flash`). Used **only** for explaining
  options, translating, and chat with function calling. **Never for time/cost maths.**
- Push: Firebase Admin SDK (FCM). Directions: Google Routes API (server side, key stays on server); mock = distance-based estimate.
- Flight status: `FlightStatusProvider` interface → `AeroDataBoxProvider` (RapidAPI) + `MockFlightStatusProvider` (default, controllable via demo endpoints).
- Weather: Open-Meteo (no key).
- **Front-desk dashboard:** static HTML + vanilla JS at `/desk`, polls every 2 s, shows MockStay / MockCab / MockAir / MockTable
  bookings and a change log. It is the second screen in the demo.
- Hosting: Render or Railway (Dockerfile). Android emulator talks to the local backend at `http://10.0.2.2:8080`.

### Repo layout
```
/android            Android Studio project (Java)
/backend            Spring Boot app
/backend/src/main/resources/static/desk   front-desk dashboard
/mock-data          JSON seed: flights, hotels, cabs, offers, airport map points, demo trips
/docs               architecture.md, api.md, demo-script.md
docker-compose.yml  CLAUDE.md  README.md (setup in < 10 steps for a new teammate)
```

---

## 3. Domain model (backend JPA entities, mirrored as Room entities)
```
Trip            id, title, purpose(enum), persona(enum), language, travellers(int),
                status(PLANNED|ACTIVE|DONE), createdAt, updatedAt, version(int)
Block           id, tripId, type(FLIGHT|BAG_CLAIM|CAB|HOTEL|MEETING|EVENT|RESTAURANT|TRAIN|OTHER), title,
                startTime, endTime (ZonedDateTime), locationName, lat, lng,
                provider(MOCK_AIR|MOCK_STAY|MOCK_CAB|MOCK_TABLE|GOOGLE_CALENDAR|NONE), providerRef,
                flex(FIXED|AUTO_SHIFT|ASK_USER), anchor(boolean),
                status(OK|AUTO_ADJUSTED|NEEDS_USER|DONE|CANCELLED),
                freeChangeUntilMinutes(int, e.g. -30), changeFeeInr(int), notes
Link            id, tripId, fromBlockId, toBlockId, minGapMinutes, travelMinutes, travelMode(WALK|CAB|METRO|NONE)
PlanOption      id, tripId, triggerEventId, label, score, extraCostInr, minutesLost, anchorMissed(boolean),
                changes(JSON list of BlockChange), explanation(text, per language), recommended(boolean)
BlockChange     blockId, action(MOVE|CANCEL|REBOOK|NOTE), newStart, newEnd, newProviderRef,
                channel(API|MESSAGE_DRAFT), messageDraft
DisruptionEvent id, tripId, blockId, kind(DELAY|CANCEL|GATE_CHANGE|MISSED), detail, detectedAt
ChangeLog       id, tripId, blockId, adapter, request, response, success, rolledBack, at
Document        id, tripId, kind(BOARDING_PASS|TICKET|ID|HOTEL_VOUCHER), parsedJson, filePath, savedOffline(boolean)
CardOffer       id, bank, cardType, percentOff, maxDiscountInr, validAirlines[], validFrom, validTo, minSpendInr
UserCard        id, userId, bank, cardType, last4
```
- Purposes: BUSINESS, WEDDING (wedding/family event), HOLIDAY, PILGRIMAGE, MEDICAL, EXAM (exam/interview).
  Personas: FIRST_TIME, FAMILY, BUSINESS, ELDERLY.
- Use `java.time` everywhere (minSdk 26 makes it available on Android). Store times with zone **Asia/Kolkata**.

---

## 4. Core engines (backend, pure Java, fully unit-tested)

### 4.1 Ripple engine — `ripple/RippleEngine.java`
Input: trip (blocks + links) + one changed block (new times, or cancelled).
1. Build a graph from links; topologically sort the blocks.
2. From the changed block, for each downstream block: `earliestStart = max over incoming links (from.end + minGap + travelMinutes)`.
3. `earliestStart <= block.start` → **OK** (no change).
4. Else if `flex == AUTO_SHIFT` and still inside the free-change window (`freeChangeUntilMinutes` is an offset from the block's
   start, e.g. −30 = free until 30 min before) → propose MOVE to `earliestStart`, status **AUTO_ADJUSTED**, end moves by the same amount.
5. Else (`FIXED`, `ASK_USER`, or anchor) → **NEEDS_USER**, record how many minutes late.
6. Hotel special rule: check-in is a window. Flag only if arrival > 23:00 (late-arrival note, AUTO_ADJUSTED) or past midnight (NEEDS_USER).
7. Return `RippleResult`: per-block status, proposed new times, list of broken links. **Must be deterministic.**

JUnit tests: no delay · small delay (cab auto-shift) · medium delay (meeting needs user) · big delay (anchor missed, hotel late) ·
cancellation · connection missed with minimum connection time.

### 4.2 Plan B generator — `planb/PlanBGenerator.java`
1. From a `RippleResult`, build 2–3 candidate `PlanOption`s:
   - Delay: (A) keep flight, shift everything; (B) move the anchor (meeting → later / video call); (C) connections: rebook next connecting flight + airport hotel.
   - Cancel: (A) next flight same airline via MockAir; (B) another airline from mock inventory; (C) next-day flight + hotel.
2. `score = extraCostInr + timeWeight × minutesLost + (anchorMissed ? 50000 : 0) + sum(changeFees)`; **lowest score = recommended**.
   `timeWeight` by purpose: BUSINESS 10, EXAM 15, MEDICAL 12, WEDDING 10, PILGRIMAGE 6, HOLIDAY 2.
3. AI writes only the `explanation` (trip language, 2–3 short plain lines per option). AI unavailable → template strings from `messages_<lang>.properties`.
4. **Precompute Plan B** when a trip becomes ACTIVE (next flight + nearest airport hotel) and expose it to the app for offline storage.

### 4.3 Booking orchestrator — `orchestrator/BookingOrchestrator.java`
- Apply a chosen `PlanOption` change by change, ordered by scarcity: **FLIGHT → HOTEL → MEETING → CAB → RESTAURANT → OTHER**.
- Each change goes through the block's adapter; log every call in `ChangeLog`.
- A change fails → roll back all earlier successful changes in **reverse order** (`cancel()` / `change()` back to original),
  mark `rolledBack`, return a clear failure reason to the app.
- `channel = MESSAGE_DRAFT` → no API call; generate a prefilled, language-aware message and return a `wa.me` link and a `mailto` link.
- On success: increment `trip.version` and send an FCM push "Plan updated".

### 4.4 Cost engine — `cost/TrueCostCalculator.java`
`trueCost = fare + bagFee (if bag not included) + cabCost (departure hour; surge 1.8× between 00:00–05:00) + seatFee − bestApplicableOffer (user's cards, airline, date, min spend)`.
Return a line-by-line breakdown list. Labels: **Cheapest**, **Fastest** (door-to-door time), **Least stress** (fewest early-morning/connection penalties).

---

## 5. Adapters and mock partners
```java
public interface BookingAdapter {
    BookingResult book(BookingRequest req);
    BookingResult change(String providerRef, ChangeRequest req);
    BookingResult cancel(String providerRef);
    ChangeRules getRules(String providerRef);   // free-change window, fees
}
```
- Implementations: `MockAirAdapter`, `MockStayAdapter`, `MockCabAdapter`, `MockTableAdapter` — all call the mock servers **over HTTP**
  (swapping to a real partner later only changes the adapter) — and `GoogleCalendarAdapter` (real API if OAuth is configured, else a mock calendar).
- Mock partner servers live in the same Spring Boot app under `/mock/air`, `/mock/stay`, `/mock/cab`, `/mock/table`, each with its own tables and realistic rules:
  - **MockAir:** seat inventory per flight (some nearly full); change fee ₹2,500 unless disruption-caused (then free); rebook only to flights with seats.
  - **MockStay:** free cancellation until 24 h before check-in; late-arrival note always allowed; "no-show" after 23:59 without a note.
  - **MockCab:** free pickup change until 30 min before; ₹200 after; cancel free until 60 min before.
  - **MockTable:** free change/cancel until 18:00 on the day.
  - Each has a `/mock/*/fail-next` toggle to demo the rollback path.

### 5a. Real sandbox providers (addendum from the team, 2026-10-10; built in Phase 4)
Keep every Mock* adapter. Pick per provider with env vars, default `mock`; if the chosen provider's key is missing → use mock
anyway. The startup log (and the provider table) says which booking provider is active.
- `AIR_PROVIDER=duffel|mock` → **DuffelAirAdapter** (env `DUFFEL_TEST_TOKEN`, Duffel **test mode only**). Search = create an
  offer request, prefer Duffel Airways (ZZ) offers; `book()` = create order; `change()` = Duffel order-change flow (pick an
  alternative flight); `cancel()` = order cancellation flow (quote, then confirm). Duffel order id → `Block.providerRef`.
  Delays still come from `MockFlightStatusProvider`.
- `STAY_PROVIDER=liteapi|mock` → **LiteApiStayAdapter** (env `LITEAPI_SANDBOX_KEY`): search rates → prebook → book with the
  sandbox's simulated payment. No native change → `change()` = book the new room FIRST, then cancel the old one (never leave the
  traveller without a room); if that cancel fails → fall back to a MESSAGE_DRAFT late-arrival note.
- `CAB_PROVIDER=mock` only. **Uber = deep link, no API booking:** an "Open in Uber" button on cab blocks builds an Uber deep link
  with pickup/drop coordinates and nicknames. MockCab stays the booking system of record.
- Front-desk dashboard also shows ChangeLog entries for sandbox providers (provider, booking id, action, success), so the demo can
  show real sandbox booking ids changing.
- Check the official Duffel and LiteAPI docs for exact endpoints, headers and API versions before coding (no guessing). One
  integration test per adapter that runs only when its key is set.

---

## 6. Backend REST API (documented in `/docs/api.md`)
```
POST   /api/trips                              create trip (purpose, persona, language)
GET    /api/trips/{id}                         full trip: blocks, links, statuses, version
GET    /api/trips/{id}/now                     "Do this now" card for current time + location
POST   /api/trips/{id}/blocks                  add block   | PUT/DELETE /api/blocks/{id}
POST   /api/trips/{id}/links                   add link
GET    /api/trips/{id}/planb                   precomputed Plan B (for offline storage)
GET    /api/trips/{id}/options                 current fix options after a disruption
POST   /api/trips/{id}/options/{optId}/apply   run orchestrator
GET    /api/trips/{id}/directions?leg=N        directions for a leg (Routes API or mock)
POST   /api/cost/compare                       true-cost comparison for flight options
POST   /api/assistant/chat                     Gemini chat; function calling: getTrip, getFlightStatus, compareCosts,
                                               getOptions, applyOption (applyOption ONLY after explicit user confirmation)
POST   /api/devices                            register FCM token
POST   /api/demo/trigger-delay                 {tripId, blockId, minutes}
POST   /api/demo/trigger-cancel                {tripId, blockId}
POST   /api/demo/reset                         reload seed data
GET    /desk                                   front-desk dashboard (HTML)
GET    /api/health                             app status + MOCK/REAL provider modes (added in Phase 0)
```

---

## 7. Android screens
1. **Onboarding:** language picker (5 languages) → 3 persona questions (flown before? who's travelling? any help needed?) → purpose of next trip.
2. **Home — "Do this now":** one big card (title, 1–2 line detail, one primary button); below it a strip: boarding pass ✓, gate, bag allowance.
   Offline banner with "last updated HH:MM" when there is no network.
3. **Trip timeline:** RecyclerView of blocks; status chip colours (OK green, auto-adjusted amber, needs-user red, done grey); anchor marked with a star.
   Tap a block → detail with directions for that leg.
4. **Fix-it screen:** opens from push or a red banner. Shows what broke (mini ripple list) + 2–3 option cards (label, extra ₹, time lost,
   plain-language explanation, "Recommended" badge). One tap to apply → progress → result (or rollback message).
   Prefilled WhatsApp/email buttons where `MESSAGE_DRAFT`.
5. **Compare & book:** flight options with true-cost breakdown table and offer applied.
6. **Wallet (offline locker):** scan boarding pass (CameraX + ML Kit barcode → BCBP parse), list documents, open with no network.
7. **Airport map:** custom view rendering the terminal vector map, route highlight from current point to gate, walking minutes. Works offline.
8. **Assistant:** chat + mic button; accepts Hinglish/Marathi/etc.; replies in the chosen language; TTS read-aloud toggle. Camera "translate sign" (stretch).
9. **Profile:** language, persona, purpose defaults, saved cards (bank + last4 only), elderly mode (large text).

- Accessibility: min touch target 48dp, large-text mode, content descriptions on all icons, high-contrast status colours.
- Offline rules: **Room is the source of truth for the UI.** On refresh, fetch trip + Plan B and overwrite Room only if the server `version`
  is newer. Chat/compare/options show a "needs internet" state gracefully — never crash.

---

## 8. Seed / demo data (`/mock-data`)
- **Demo trip 1 (business):** Mumbai → Bengaluru. Flight 08:00–09:45 (MockAir) · bags 20 min · cab pickup 10:05 (MockCab, AUTO_SHIFT) ·
  client meeting 12:00 Whitefield (anchor, ASK_USER, Google Calendar/mock) · hotel check-in 14:00 (MockStay) · dinner 20:00 (MockTable).
  Travel: airport→office 75 min, office→hotel 40 min, airport→hotel 60 min.
- **Demo trip 2 (first-time flyer, Marathi):** Pune → Delhi → Leh, 58-year-old traveller, elderly persona, purpose HOLIDAY.
  Connection at Delhi with 75 min minimum connection time. Leh hotel + airport cab.
- Mock flight inventory: 6–8 Indian routes, 3 airlines, a few flights per day, some nearly full.
- 6–8 card offers (bank, % off, max ₹, valid airlines, dates).
- Terminal map points for one terminal (gates, security, check-in rows, washrooms, food, exits) with coordinates and walking minutes.

---

## 9. Build phases (stop after each)
- **Phase 0 — Setup:** repo layout, CLAUDE.md, README, docker-compose Postgres, Spring Boot skeleton + health endpoint, Android skeleton
  (Java, Material 3, bottom nav with 5 empty fragments), version catalog, `.env.example`, `local.properties` keys pattern. Both build cleanly.
- **Phase 1 — Trip data end to end:** JPA entities + Flyway, seed loader for demo trips, `GET /api/trips/{id}`, Android Retrofit + Room,
  Trip timeline rendering demo trip 1, offline from Room.
- **Phase 2 — Ripple engine:** `RippleEngine` + full JUnit tests, `MockFlightStatusProvider`, `/api/demo/trigger-delay|cancel`,
  watcher job updating block statuses, timeline colours update.
- **Phase 3 — "Do this now" + push:** `/api/trips/{id}/now` (next unfinished block, current leg), Home card UI, FCM end to end, WorkManager refresh.
- **Phase 4 — Mock partners + adapters + orchestrator:** four mock servers with rules + `fail-next`, adapters over HTTP,
  orchestrator with order + rollback + ChangeLog, front-desk dashboard at `/desk`. Tests for rollback.
- **Phase 5 — Plan B + Fix-it screen:** generator + scoring by purpose, precomputed Plan B synced to Room, Fix-it screen with one-tap apply,
  message drafts (wa.me/mailto).
- **Phase 6 — Languages + AI assistant:** strings in 5 languages, language switcher, Gemini service (explanations + chat with function
  calling, templates as fallback), SpeechRecognizer + TTS, persona-based UI changes.
- **Phase 7 — Wallet, map, cost:** boarding pass scan + BCBP parser (unit tests on sample strings), offline document list, terminal map
  custom view, true-cost compare screen with offers, directions per leg (Routes API or mock).
- **Phase 8 — Polish + demo:** airplane-mode test of all offline features, error/loading states, large-text check, `/docs/demo-script.md`
  (3-minute story: trip 1 delay → front-desk changes live → airplane mode → boarding pass still opens → Marathi assistant on trip 2),
  Dockerfile + deploy notes for Render/Railway, final README.
- Stretch (only after Phase 8): camera sign translation, group trip sharing, Tamil/Bengali voice via Sarvam, Gemma on-device via MediaPipe.

## 10. Definition of done (check before ending every phase)
- Android app and backend both build with zero errors; all tests pass.
- Works with **no API keys** (mocks active) and logs which providers are mock vs real at startup.
- No secrets in git. `.env.example` and README updated.
- Short summary: what was built, how to run it, what to click to see it, known gaps.

---

## Pinned toolchain (checked against official sources on 2026-10-10)
| What | Version | Why |
|---|---|---|
| JDK used to build | 21 (Temurin / Android Studio JBR); code targets Java 17 | JDK 26 is first on PATH on the dev machine but is too new for Gradle 9.3 / Spring Boot 3.5 tooling — always set `JAVA_HOME` to a JDK 21 (or 17). |
| Android Gradle Plugin | 9.1.1 | Newest AGP that the installed Android Studio **Panda 3 (2025.3.3)** supports (Panda 2/3 open AGP ≤ 9.1, Panda 4 / Quail 1 ≤ 9.2, Quail 4 / Rabbit ≤ 9.4). Compiling for API 37 needs Studio Panda 3 Patch 1 or newer. |
| Gradle (wrapper) | 9.3.1 | Default/minimum Gradle for AGP 9.1. |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | API 37 = latest stable; AGP 9.1 supports up to API 37.0. |
| Spring Boot | 3.5.16 | Latest 3.x (stack is locked to Boot 3; Boot 3.5 open-source support ended 2026-06-30 — fine for a hackathon). |
| Maven (wrapper) | 3.9.16 via Maven Wrapper 3.3.4 | |
| PostgreSQL (docker) | 17.11 (`postgres:17.11-alpine`) | Matches Supabase's major version. |
| Gemini default model | `gemini-3.8-flash` | Current stable Flash model (ai.google.dev, Oct 2026); override with `GEMINI_MODEL`. |
Exact Android library versions live in `android/gradle/libs.versions.toml`; backend versions are managed by the Spring Boot parent POM.

## Conventions & gotchas
- Times: `ZonedDateTime`/`OffsetDateTime`/`Instant` + zone Asia/Kolkata; never `java.util.Date`. Backend gets "now" from an injected `Clock`.
- **AGP 9: `R.id.*` values are not compile-time constants** → no Java `switch` on view IDs; use `if/else`.
- AGP 9: `BuildConfig` is off by default → enabled in `app/build.gradle.kts`. `android.builtInKotlin=false` keeps the app Java-only.
- targetSdk 37 = edge-to-edge is mandatory: every screen must handle system-bar insets.
- Backend reads keys from env vars; for local dev it also imports a git-ignored root `.env` (`spring.config.import`). Empty key = MOCK.
- Backend tests must not depend on a developer's `.env` — set provider properties explicitly in tests.
- Room is the UI's source of truth; only overwrite when the server `version` is newer.

## Phase status
- [x] Phase 0 — Setup (done 2026-10-10)
- [ ] Phase 1 — Trip data end to end (in progress)
- [ ] Phase 2 — Ripple engine
- [ ] Phase 3 — "Do this now" + push
- [ ] Phase 4 — Mock partners + adapters + orchestrator
- [ ] Phase 5 — Plan B + Fix-it screen
- [ ] Phase 6 — Languages + AI assistant
- [ ] Phase 7 — Wallet, map, cost
- [ ] Phase 8 — Polish + demo

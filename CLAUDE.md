# CLAUDE.md — OnBoard

Hackathon project for **Elevate 1.0 (DJSCE), Problem Statement 01 — "Seamless Aviation Journey Experience"**.
4-person college team, strong in **Java**. This file is the condensed project brief. Every session follows it.
If a decision changes, update this file in the same change.

**Direction (since 2026-10-11): Gate Navigation and Baggage Shield, plus (added 2026-10-11) Ask OnBoard, the
multilingual assistant (§3E).** The earlier plan (trip timeline, ripple engine, bookings, Plan B) is **parked**, not
deleted: see "Parked" below.

- App name **OnBoard** (was "Safar Saathi"). Code names stay on purpose so nothing breaks: Android package / applicationId
  `com.safarsaathi.app`, backend package `com.safarsaathi.backend`, database `safarsaathi`, settings prefix `safarsaathi.*`.

---

## 0. How we work
1. **Phase by phase** (§8). Start of a phase: short plan (files to touch). End of a phase: build, run tests, short "how to try
   it" summary, then **STOP and wait for the go-ahead**.
2. Explain non-obvious things in plain English — in code comments and in replies. No heavy jargon.
3. **Never invent facts the app can't know.** No made-up gate numbers, boarding times, belt numbers, legal outcomes or
   compensation amounts. Missing data says so ("Gate not announced yet"). Estimates are labelled "estimate". Map data made by
   the team is labelled "Demo schematic, not an official airport map". Facts from official pages carry their source and a
   "last checked" date and live in config files, not code.
4. **Every outside service sits behind a Java interface with a mock that needs no key.** The full demo runs with zero API keys.
   The backend logs at startup which providers are MOCK vs REAL.
5. **Never commit secrets.** Android keys → `android/local.properties` (git-ignored); backend keys → env vars or a git-ignored
   root `.env`; `.env.example` lists names only.
6. Simple, readable code over clever code. **Java only** in app code (Gradle Kotlin DSL build files are fine). No Hilt.
7. Unsure about a library version or API detail → check the official docs; **pin exact versions**.

---

## 1. Product
A mobile-first Android app (plus a Spring Boot backend and two small staff web pages) for:
- **A. Gate Navigation** — Google-Maps-style, turn-by-turn walking directions inside the airport to the boarding gate, which
  re-route automatically when the gate changes.
- **B. Baggage Shield** — timestamped, tamper-evident photo evidence of the bag before hand-over; a guided flow for damaged /
  delayed / missing bags; a Lost & Found guide for things lost at the airport.

| PS 01 requirement | Where we meet it |
|---|---|
| Navigate airports | Indoor map, blue dot, turn-by-turn, landmarks |
| Gate changes / disruptions | Gate watcher → push → automatic re-route |
| Track baggage | Bag tag registered, status from drop to belt |
| Baggage problems | Damage / delay / loss guide, PIR help, claim pack, deadlines |
| Offline, on-device vs cloud | Map, routing, evidence photos and guides work offline; photos upload when online |
| Traveller profiles | Step-free route, slower walking speed, large text, voice, assistance info |
| Multilingual | English, Hindi, Marathi now (Tamil, Bengali next); voice via TTS |
| UI/UX | One clear next instruction at a time; nothing extra on screen |

**Out of scope:** real indoor positioning hardware, airline system integration, submitting forms on the user's behalf, legal
advice, any airport other than the pilot.

---

## 2. Feature A — Gate Navigation
- **Pilot airport: Mumbai CSMIA Terminal 2 only.** Facility names from the official CSMIA site / Adani One CSMIA passenger guide.
  Those pages have no usable digital indoor map, so **we draw our own simplified schematic** (GeoJSON + graph JSON) in
  `/mock-data/airports/BOM-T2/`. Never copy or trace a copyrighted map image. Every file has a header:
  `"source"`, `"preparedBy"`, `"verified": false`, `"note": "Demo schematic, not an official airport map"`.
- **Screens:** flight card on Home (flight, terminal, gate or "Gate not announced yet", boarding start/close with source label
  `airline-confirmed` / `estimate`, "Navigate to gate"); Navigation (top banner = next instruction only, landmark based; map with
  route line, blue dot, destination pin, floor switcher, recenter; bottom sheet with time, distance, "Boarding closes 07:35 —
  you'll arrive 07:12 (estimate)", gate chip, Steps list, Step-free toggle, I'm lost / Help); checkpoint order Entry → Check-in /
  bag drop → Security → (Immigration if international) → Pier → Gate; gate-change alert (banner + push + vibration + voice, old
  gate struck through, route redrawn); Help sheet (nearest help desk, washroom, lift, buggy stop; wheelchair assistance = link to
  the airport's info, never pretend to book); ongoing notification while navigating.
- **Map model (`AirportMap`):** floors; nodes (id, type ENTRY / CHECKIN_ROW / BAG_DROP / SECURITY / IMMIGRATION / PIER_JUNCTION /
  GATE / LIFT / ESCALATOR / STAIRS / TRAVELATOR / WASHROOM / HELP_DESK / BUGGY_STOP / LANDMARK / BAGGAGE_BELT / BAGGAGE_SERVICES /
  LOST_FOUND; x/y in metres in a local frame; floor; names en/hi/mr; landmark hint); edges (from, to, distanceM, kind WALK /
  TRAVELATOR / LIFT / ESCALATOR / STAIRS, stepFree, oneWay, zone LANDSIDE / AIRSIDE, passesCheckpoint); anchors (QR id → node id).
- **Routing (`routing-core`, pure Java, shared by app and backend, JUnit tested):** A* with cost = walking seconds (1.2 m/s;
  elderly 0.8 m/s; travelator faster; lift adds wait). Step-free excludes STAIRS / ESCALATOR. Landside → airside only through
  security. **Instruction generator:** turn angles + nearest landmark → "turn left at …", "take the lift to Level 3"; templates
  per language in resources.
- **Positioning (honest):** 1. QR "You are here" anchors (exact node); 2. pedestrian dead reckoning (step detector + rotation
  vector) along the route; 3. simulated walk for demos; 4. BLE / Wi-Fi RTT documented as future only. **Off-route:** > 25 m from
  the route for > 10 s → re-route from the nearest node and say so.
- **Gate data:** backend `GateWatcher` reuses `FlightStatusProvider` (mock, or AeroDataBox: check its docs for BOM gate/terminal
  fields; a missing field = "not announced"). Poll every 2 min (demo 15 s); compare gate / terminal / boarding times.
  `GateChangeEvent` → FCM high-priority data message → app updates Room → if navigating, re-route at once, banner, vibrate, speak
  (TTS in the chosen language). Missed push → the app's own refresh (60 s while navigating, WorkManager otherwise) catches it.
- **Offline:** map, graph, routing engine and last known gate cached; "Offline · gate as of 07:02".
- **Rendering (decided in G1):** our own Canvas `View` (`TerminalMapView`), not MapLibre: MapLibre needs downloaded
  glyph (font) files for any text label, which fights offline use, adds ~20-30 MB of native code, and our map is in
  local metres, not longitude/latitude. Routing and instructions never depend on the renderer.
- **Map storage (decided in G1):** the map is fixed reference data shipped with the code: `mock-data/airports/BOM-T2/`
  (`map.json` + `areas.geojson`), served by `GET /api/airports/{code}/{terminal}/map` from memory (no database table),
  bundled in the app as assets and saved in Room (`airport_maps`); a higher `version` replaces the saved copy.
- **Built in G2-G3 (2026-10-11; the user asked for "Google Maps navigation using user location"):**
  - Routing in `routing-core/.../route/` (`Router` A*, `WalkProfile`, `StepBuilder`, `Route` progress maths). Instruction
    sentences in `routing-core/src/main/resources/com/safarsaathi/routing/route/instructions_{en,hi,mr}.properties`,
    loaded by both builds (hi/mr to be reviewed by native speakers in P).
  - Navigate tab looks and works like Google Maps:
    - Search bar, floor picker, and a place card ("Directions" / "I'm here").
    - Route preview: time, distance, "Step-free", "Slower pace", "Start".
    - Green turn banner with "Then …", and a heading-up map following a blue arrow.
    - Bottom bar: minutes, metres, arrival "(estimate)".
    - Steps sheet; "Help nearby": nearest help desk / washrooms / lift / buggy, with a detour and "Continue to …".
    - Arrival card, voice guidance (TTS), dark mode.
    - No toolbar on this tab, and the bottom tabs are hidden while navigating.
  - Positioning as built:
    - QR anchors via Google's code scanner: Google Play services shows the camera, so no camera permission is needed.
    - "I'm here" on a tapped place, or "Where are you now?" (choose a place).
    - Steps along the route: `TYPE_STEP_DETECTOR`, which needs `ACTIVITY_RECOGNITION`.
    - Turn confirmation from `TYPE_GAME_ROTATION_VECTOR`: only CHANGES of heading near the next turn are used, so no
      true north is needed.
    - Demo walk in debug builds, labelled as a simulation.
    - Off-route = a QR / chosen place that is not on the route: re-route from there and say so.
    - Not built: free 2D dead reckoning and the "> 25 m for > 10 s" rule. Both need the map's real orientation and
      scale checked on site.
  - **Phone GPS is not used inside the terminal:** it can't tell floors and is tens of metres off indoors. Getting TO the
    airport hands off to the Google Maps app. It uses a Maps URL (driving, `dir_action=navigate`, no key), opened from
    Home and from "Where are you now?".
  - Printable QR cards for the demo: `docs/qr-anchors.html`, generated from the map's anchors and labelled demo-only.

## 3. Feature B — Baggage Shield
- **Before drop — evidence:** (1) passenger self-capture: guided CameraX flow, 4 required shots (front, back, side with handle and
  wheels, close-up of existing marks) + **bag tag scan** (10-digit licence plate, usually ITF barcode, ML Kit; manual entry
  fallback); (2) counter capture (pilot concept): staff page `/counter` scans/types the tag, takes 2 photos, "Accept bag" → copy to
  the passenger's app and to the arrival station inbox `/station` (filtered by station code, e.g. BLR).
- **Integrity (tamper-evident):** SHA-256 of each image on the device; the server recomputes and compares, adds **its own
  timestamp**, signs `{bagTag, flight, stage, photoHashes, serverTime}` with **Ed25519** (Java built-in; public key in the docs);
  every evidence event goes into an **append-only hash chain** (`prevHash` → `hash`) in Postgres; `/api/evidence/verify/{bagTag}`.
  Offline captures queue (WorkManager) and upload later; receipt shows "captured on device 06:48, received by server 07:05".
- **Privacy:** bag photos only (hint: keep faces and documents out of frame); keep 30 days after arrival unless a claim is open;
  the passenger can delete their copies.
- **On arrival:** bag status REGISTERED → DROPPED → (LOADED) → ARRIVED → ON_BELT → COLLECTED (mock unless a real source exists);
  belt number or "Belt not announced yet"; "Check my bag": after photos, **before vs after side by side** with timestamps. The app
  never decides damage; the passenger does. Station page can add an unloading photo (stage ARRIVAL_BELT).
- **Something went wrong — "What happened?":** damaged bag / bag didn't arrive / item missing from bag / I lost something at the
  airport.
  - Damaged or missing checked bag (airline's responsibility): before leaving the baggage hall go to the airline's baggage services
    counter and ask for a **Property Irregularity Report (PIR)**; the app shows the counter on the map and a one-screen evidence
    summary + QR for staff. PIR reference → **claim pack PDF** (details, tag, PIR, before/after photos with server timestamps and
    hashes, description, damaged items, receipts). **Deadlines** (config values with a source note): Montreal Convention Art. 31 —
    damage complaint within **7 days** of receipt, delay complaint within **21 days** of the bag being made available; countdown +
    reminders. Indian domestic flights: link the airline's Conditions of Carriage, "check the airline's terms". **No compensation
    amounts, no promised outcomes; not legal advice.** Escalation (information only): airline customer relations → grievance /
    nodal officer → AirSewa → consumer commission; pre-filled email the user sends from their own mail app.
  - Lost at the airport: in the terminal → CSMIA Lost & Found; on the aircraft → the airline; checked bag → airline baggage
    services (PIR flow). CSMIA facts (from the team brief, 2026-10-11; **verify against the official CSMIA page in B3**, keep in a
    config file with source + last-checked date): T1 Departures next to the Airport Police Station; T2 Ground Transportation
    Lobby, Level 1; report portal https://adaniairports.powerappsportals.com/lf-portal/ ; collection 09:30–17:30 except 2nd and
    4th Saturdays, Sundays and public holidays; unclaimed items stay up to 24 h then go to a warehouse (perishables up to 48 h or
    expiry); to collect: government ID, boarding pass/ticket, authorisation letter if someone else collects. The app **pre-fills**
    the report fields and opens the official portal; it never submits for the user.

---

## 3E. Ask OnBoard — multilingual assistant (added 2026-10-11; A1 built)
- **What:** the traveller types or speaks a question in English, Hindi, Marathi or Hinglish ("Mera gate kahan hai?",
  "Majhi bag kuthe aahe?") and gets the answer in that language (text + optional voice). Three layers, picked by a plain
  Java router (A4): **Layer 1 Quick answers** (offline, no AI model, phone's saved data), **Layer 2 on-device AI** (A2:
  small Gemma via MediaPipe LLM Inference, offline, grounded only in phone facts, translate in/out with ML Kit, a guard
  rejects any gate/time/flight/belt/amount not in the facts), **Layer 3 cloud AI** (A3: backend `/api/assistant/chat`,
  Gemini with function calling: trip summary, flight search (Duffel test / mock), hotel search (LiteAPI sandbox / mock),
  Uber rider deep link, `prepare_booking` = quote only). A chip under every answer: "Answered on your phone / by
  on-device AI / in the cloud" + "as of HH:MM".
- **Golden rules:** facts (gates, times, belts, flight numbers, prices, deadlines) come from data and are put into the
  sentence by code; missing = "not announced yet". The AI never books, pays or cancels: booking only after a Confirm tap
  that creates a signed token; `/api/bookings/confirm` refuses without it; the model has no booking tool. Cabs = "Opened
  in Uber", never "booked". Mock/sandbox results say SAMPLE DATA / TEST BOOKING. Passport/visa never go to the cloud.
- **Layer 1 as built (A1), all in `data/ask/` (pure Java except `MlKitLanguageHints`, `AskSettings`):**
  - `AskText` normalises (case, punctuation, Devanagari nukta/nasal marks, digits). `Lexicon` reads
    `resources/.../data/ask/lexicon_{en,hi,mr,hinglish,mr_latn}.properties` (one line per `Concept`; longest phrase
    first; Marathi endings cut off; typos forgiven in words of 5+ letters; `MARKERS` = language giveaways).
  - `IntentMatcher`: rules over concepts (same for every language) → intent + confidence (HIGH only without a close
    rival that saw another topic). Intents: GATE, BOARDING_TIME, BOARDING_CLOSE, FLIGHT_STATUS, CONNECTION_STATUS,
    BAGGAGE_BELT, BAG_STATUS, NEXT_STEP, DOCUMENT_EXPIRY, DOCUMENT_MISSING, WHERE_IS (washroom / help desk / lift),
    HELP; recognised but
    not answered on the phone: SEARCH_FLIGHTS, SEARCH_HOTELS, CAB.
  - `LanguagePicker`: marker words decide; ML Kit language-id (bundled, offline) breaks ties; Marathi in English
    letters is answered in Marathi script; the language chip overrides (saved in `AskSettings`).
  - `QuickAnswerer` + `AnswerText` (`answers_{en,hi,mr,hinglish}.properties`, `{name}` placeholders filled by code;
    a sentence never contains a number). `TripFacts` = snapshot of the saved trip (Room, same as Home), the T2 map,
    and later gate (G4) and bag (B1) data. `TerminalPlaces` describes places by the nearest landmark on the map.
  - Screen `ui/ask/` (`AskFragment`, opened from the "Ask OnBoard" bar on Home; tabs hidden; voice in via
    `SpeechRecognizer` + `EXTRA_PREFER_OFFLINE`, voice out via TTS in en-IN / hi-IN / mr-IN).
  - **Documents (user decision 2026-10-11): narrow, read-only vault access.** The Vault track writes
    `VaultSummaryReader.getSummaries(tripId)` (branch `feature/document-vault`; Ask never adds files under
    `data/vault/**`): per document type, holder FIRST name, expiry, covered dates + countries, status (READY /
    EXPIRES_SOON / EXPIRED / MISSING / MISMATCH / NOT_OFFLINE_YET). Never numbers, MRZ, images, file paths, full names,
    birth dates. Only Layer 1 and Layer 2 may use it; Layer 3 (cloud) never receives vault data (a test checks it).
    Vault locked: "Unlock your vault to see this" + an Unlock button (biometric prompt), then the answer.
    Ask side built: `DocumentFacts` (only those fields; `DocumentFactsTest` fails if one is added), DOCUMENT_EXPIRY +
    DOCUMENT_MISSING answers. MISSING = the saved file is missing on the phone, NOT "a required document"; which
    documents a country requires needs sourced country packs (V4), so "saved?" answers add "I can't tell which
    documents a country requires: check with the airline or embassy". Wiring to the reader (`currentTrip()`,
    `getSummaries`, `isUnlocked`, `VaultUnlockPrompt`) follows when the reader is on `feature/document-vault`.
  - Connection answers state the gap only until Disruption Recovery C1's minimum-connection-time data is wired in
    (then labelled "based on a demo minimum connection time"); never "enough" without that data.
- **Branch:** `feature/ask-onboard` (worktree `../OnBoard-ask`) = main + a merge of `feature/gate-navigation`
  (the map and `setTabsVisible` are G1-G3 code). It must reach main AFTER the gate branch. When G4 and C1 are in main,
  merge main and wire GATE / BOARDING / CONNECTION_STATUS to them; BAG_STATUS waits for B1.
- **Debug builds:** Ask's toolbar menu "Load demo trip on phone" saves the backend's demo trip 1 (`DemoTripOnPhone`,
  flight IJ 512 tomorrow) so trip answers show without the backend; the next real sync replaces it.
- **Tests:** `IntentGoldenSetTest` (golden_phrases.tsv, 135 phrases, 4 languages, ≥ 90% intents AND languages; chips
  100%), `QuickAnswererTest`, `AnswerTextTest` (same keys/placeholders everywhere), `LexiconTest`, `LanguagePickerTest`,
  `DocumentFactsTest`, `DemoTripOnPhoneTest`, UI `AskFragmentTest`. Native-speaker review: `docs/ask-strings-review.md`
  (generated from the properties files; regenerate after edits).

---

## 4. Tech stack (locked; add only what a phase needs)

### Android app — `/android`
- Java 17 · XML layouts · Material Components with a **Material 3** theme. **minSdk 26**, compile/target 37. Gradle Kotlin DSL +
  version catalog `gradle/libs.versions.toml`.
- **MVVM:** Fragment → ViewModel + LiveData → Repository → (Room | Retrofit). Manual DI with `ServiceLocator` (**no Hilt**).
- Jetpack Navigation + bottom nav: **Home · Navigate · Bags · Help · Profile**.
- Room (map cache, gate info, bag records, evidence queue; already: trip copy, documents, ID documents, cards) · Retrofit + OkHttp +
  Gson · WorkManager · FCM · own Canvas map view (G1) · CameraX + ML Kit barcode scanning (bag tag ITF, boarding passes) ·
  Google code scanner (QR anchors, no camera permission) ·
  sensors (step detector, rotation vector) · TextToSpeech · `java.security.MessageDigest` (SHA-256).
- Localization: `values/` + `values-hi/`, `values-mr/` now; `values-ta/`, `values-bn/` later. Instruction templates localised too.

### Backend — `/backend`
- **Spring Boot 3**, Maven wrapper, packages under `com.safarsaathi.backend`. PostgreSQL via Spring Data JPA + **Flyway**; local
  `docker-compose.yml`; hosted = Supabase / Render / Railway.
- `GateWatcher` (reuses `FlightStatusProvider`) · FCM sender (mock without credentials) · evidence store (local disk in dev;
  MinIO optional) · Ed25519 + hash chain · claim pack PDF (PDFBox or OpenPDF) · static pages `/counter`, `/station`, `/demo`.
- Shared: `routing-core` Java module (graph, A*, instructions) with JUnit tests.

### Repo layout
```
/android  /backend  /routing-core (shared plain Java, compiled by both builds; see routing-core/README.md)
/mock-data (airports/BOM-T2/)  /docs  docker-compose.yml  CLAUDE.md  README.md
```

---

## 5. Data model (backend JPA + matching Room entities where the app needs them offline)
```
AirportMap      airport, terminal, version, floors, nodes, edges, anchors (JSON), verified, source
FlightGateInfo  flightNo, date, airport, terminal, gate, gateStatus(NOT_ANNOUNCED|ANNOUNCED|CHANGED|BOARDING|CLOSED),
                boardingStart, boardingClose, boardingCloseSource(AIRLINE|ESTIMATE), previousGate, source, updatedAt
GateChangeEvent flightNo, fromGate, toGate, detectedAt, notifiedAt
NavSession      userId, flightNo, startNodeId, targetGate, stepFree, startedAt, lastNodeId, rerouteCount
BagRecord       bagTag(10 digits), flightNo, pnr, userId, originStation, destStation,
                status(REGISTERED|DROPPED|LOADED|ARRIVED|ON_BELT|COLLECTED|DELAYED|DAMAGED|MISSING), beltNo, updatedAt
EvidencePhoto   bagTag, stage(PASSENGER_PRE_DROP|COUNTER_DROP|ARRIVAL_BELT|PASSENGER_POST), sha256, storagePath,
                deviceTime, serverTime, capturedByRole(PASSENGER|COUNTER|STATION), stationCode
EvidenceEvent   id, bagTag, type, payloadJson, prevHash, hash, signature, createdAt   (append-only)
Claim           bagTag, type(DAMAGE|DELAY|LOSS|PILFERAGE), airline, pirRef, filedAt, deadlineAt, status, pdfPath
LostItemReport  userId, whereLost(TERMINAL|AIRCRAFT|CHECKED_BAG|OTHER), terminal, location, date, pnr, flightNo,
                category, description, photoPath, routedTo, status
```
Demo roles: PASSENGER (app), COUNTER (staff page), STATION (arrival page), ADMIN (demo page); simple demo login, no real auth.
Times: `java.time` everywhere, zone **Asia/Kolkata**.

## 6. API (documented in `docs/api.md`)
```
GET  /api/airports/{code}/{terminal}/map            map + graph (versioned, cacheable)
GET  /api/flights/{flightNo}/{date}/gate           current gate info
POST /api/nav/route        {airport, terminal, fromNodeId, toGate, stepFree, elderly} -> path + steps (same engine as the app)
POST /api/devices                                   register FCM token
POST /api/bags              register bag (tag, flight, pnr)
POST /api/bags/{tag}/photos multipart + sha256 + stage + deviceTime -> signed receipt
GET  /api/bags/{tag}        status + evidence timeline
GET  /api/evidence/verify/{tag}                     chain + signature check
POST /api/claims            create claim -> claim pack PDF
POST /api/lost-items        save the pre-filled report (we don't submit to the airport)
GET  /api/station/{code}/inbox                      arriving bags with counter photos
--- demo only (APP_MODE=demo; 404 in prod) ---
POST /api/demo/trigger-gate-change {flightNo, toGate}
POST /api/demo/bag-status {tag, status, beltNo}
POST /api/demo/reset
GET  /api/health                                    app status + MOCK/REAL provider modes
```

## 7. App screens (bottom tabs: Home · Navigate · Bags · Help · Profile)
1. **Home:** flight card with gate status and boarding countdown, "Navigate to gate", bag summary.
2. **Navigate:** map + instruction banner + bottom sheet, QR "You are here", simulate-walk (demo builds only), steps list,
   step-free toggle, help sheet.
3. **Bags:** register bag (scan tag) → guided 4-photo capture → signed receipt; status timeline; before/after compare;
   "Something went wrong".
4. **Help:** "What happened?" decision tree; CSMIA Lost & Found card with source and last-checked date.
5. **Profile:** language, voice guidance, elderly / step-free defaults, large text, notifications; "More" links to the Wallet
   (boarding passes, Aadhaar last-4 / masked PAN / visas, PDF) and, with the flag on, the parked screens.
- UX rules: one primary action per screen; next instruction always visible while navigating; colour is never the only signal
  (icon + text); touch targets ≥ 48dp; works in large-text mode; every estimate and all demo data labelled.

## 8. Build phases (stop after each)
- **R0 — Refocus:** this CLAUDE.md + README, new bottom tabs, park the old Trip screens behind the flag, all tests green.
- **G1 — Airport map:** BOM-T2 demo schematic (≈40–70 nodes: 2 entries, check-in rows, bag drop, security, 2 piers, ~10 gates,
  lifts, washrooms, help desks, baggage services, lost & found), map endpoint, Room cache, map rendering with floors and POIs.
- **G2 — Routing:** `routing-core` A*, step-free, walking-speed profiles, landmark instructions en/hi/mr. JUnit: shortest path,
  step-free avoids stairs, can't skip security, instruction text for 3 sample routes.
- **G3 — Navigation UX + positioning:** banner, route line, blue dot, QR anchors, step detector + heading, simulate-walk,
  off-route re-route, steps list, help sheet, ongoing notification, TTS.
- **G4 — Gate changes:** `GateWatcher`, gate info endpoint, FCM push, in-app banner, automatic re-route mid-walk, `/demo` trigger.
  Test: gate change during a simulated walk → new route within 2 s of the push.
- **B1 — Bag Shield capture:** register bag, tag scan, guided photos, SHA-256, upload queue, signed receipt, evidence timeline.
- **B2 — Counter & Station:** `/counter`, `/station`, forwarding, hash chain + `/verify`, Ed25519. Test: tampering one stored photo
  makes verify fail.
- **B3 — Problems & claims:** arrival status, before/after, PIR guidance + evidence QR, claim pack PDF, deadline reminders,
  escalation, Lost & Found tree with pre-filled CSMIA fields + official portal link.
- **P — Polish & demo:** airplane-mode test, large text, Hindi/Marathi review, `docs/demo-script.md`, deploy notes.
- Ask OnBoard (its own track, §3E, same stop-after-each rule):
  - **A1 — Quick answers:** intent matcher, lexicons, templates (en/hi/mr/Hinglish), voice in/out, Ask screen, golden tests.
  - **A2 — On-device AI:** model manager (Wi-Fi download, size, delete; disabled with a message on weak phones),
    MediaPipe LLM Inference, fact collection + keyword retrieval, translate in/out, grounding guard, fallbacks.
  - **A3 — Cloud AI:** backend Gemini + function calling, flight search + ranking (MCT check), hotel search, cab hand-off,
    quote cards, confirmation-token flow, sandbox booking (TEST BOOKING CONFIRMED / PENDING / FAILED).
  - **A4 — Router and polish:** router rules (offline, documents never cloud, battery < 15% / saver, search → cloud),
    source chips, settings (reply language, voice, model, "never send to cloud"), airplane-mode test, demo script.

## 9. Demo story (`docs/demo-script.md` in phase P)
1. Home: **IJ 512, Gate 32, boarding closes 07:35** (from the mock gate provider) → Navigate to gate → scan the "Entry 4" QR →
   route → simulated walk through bag drop and security.
2. At bag drop the `/counter` page photographs the bag → phone shows "Bag photographed at counter 14" + signed receipt; the BLR
   `/station` inbox lists it.
3. Mid-walk, `/demo` → **gate change to 41**: vibration, Marathi voice, banner **32 → 41**, route redraws, estimate updates.
4. Airplane mode: map, route and gate ("as of" time) still work.
5. Arrival: bag ON_BELT, belt 7; "after" photo with a broken wheel → Damaged bag → PIR guidance → PIR ref → **claim pack PDF**
   with before/after photos, server timestamps and a passing verification; 7-day countdown starts.
6. Lost & Found: "left my headphones near security" → CSMIA Lost & Found, form pre-filled, official portal link.

## 10. Definition of done (every phase)
- App and backend build cleanly; **all tests pass**; zero API keys still runs the full demo.
- No invented gates, belts, times or legal claims; estimates and demo data labelled.
- README and CLAUDE.md updated; short summary with how to try it and known gaps.

---

## Parked (old trip-timeline plan; not deleted)
- **What:** trip timeline, trip status (old Home), ripple engine, Plan B / bookings plan, AI assistant placeholder, `/desk`
  placeholder. Code and tests stay and keep passing.
- **Flag:** `feature.tripTimeline` in `android/local.properties` (or `-Pfeature.tripTimeline=true`), default **false** →
  `BuildConfig.FEATURE_TRIP_TIMELINE`. Off = the app has no link to the parked screens; on = Profile > More links to them.
  Tests open them directly (`testing/Screens`), so they run either way.
- **Reused by the new plan:** `FlightStatusProvider` + `FlightWatcher` (gate changes), demo controller pattern and `/demo`
  page, Room offline pattern + sync rule, mock/real provider switch, BCBP boarding-pass parser (flight + date + PNR), Wallet.
- Backend still serves `/api/trips/{id}` and the delay/cancel demo controls; the Home flight card reads the trip's flight from it
  until the gate source exists (G4).

## Pinned toolchain (checked against official sources on 2026-10-10)
| What | Version | Why |
|---|---|---|
| JDK used to build | 21 (Temurin / Android Studio JBR); code targets Java 17 | JDK 26 is first on PATH on the dev machine but is too new for Gradle 9.3 / Spring Boot 3.5 tooling — always set `JAVA_HOME` to a JDK 21 (or 17). |
| Android Gradle Plugin | 9.1.1 | Newest AGP that the installed Android Studio **Panda 3 (2025.3.3)** supports (Panda 2/3 open AGP ≤ 9.1). Compiling for API 37 needs Studio Panda 3 Patch 1 or newer. |
| Gradle (wrapper) | 9.3.1 | Default/minimum Gradle for AGP 9.1. |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | API 37 = latest stable; AGP 9.1 supports up to API 37.0. |
| Spring Boot | 3.5.16 | Latest 3.x (stack locked to Boot 3). |
| Maven (wrapper) | 3.9.16 via Maven Wrapper 3.3.4 | |
| PostgreSQL (docker) | 17.11 (`postgres:17.11-alpine`) | Matches Supabase's major version. |
| ML Kit barcode scanning | 17.3.0 (bundled model) | Latest on Google Maven (checked 2026-10-10); works without Play services. |
| ZXing core | 3.5.4 | Latest on Maven Central (checked 2026-10-10); draws QR codes into PDFs. |
| Google code scanner (play-services-code-scanner) | 16.1.0 | Latest on Google Maven (checked 2026-10-11); QR "You are here" scanner, needs Google Play services. |
| ML Kit language identification | 17.0.6 (bundled model) | Newest (checked 2026-10-11); Ask OnBoard's reply language, offline, no Play services; release added 16 KB support. |
Exact Android library versions live in `android/gradle/libs.versions.toml`; backend versions come from the Spring Boot parent POM.

## Conventions & gotchas
- Times: `Instant` / `ZonedDateTime` + zone Asia/Kolkata; never `java.util.Date`. Backend gets "now" from an injected `Clock`.
- **AGP 9: `R.id.*` values are not compile-time constants** → no Java `switch` on view IDs; use `if/else`.
- AGP 9: `BuildConfig` is off by default → enabled in `app/build.gradle.kts`. `android.builtInKotlin=false` keeps the app Java-only.
- targetSdk 37 = edge-to-edge is mandatory: every screen must handle system-bar insets (MainActivity does it for the tabs).
- targetSdk 37 = Android 17 "local network protection": without `ACCESS_LOCAL_NETWORK` the app's requests to the laptop
  (`10.0.2.2`, LAN IPs) silently time out. Debug builds declare it and MainActivity asks once; UI tests pre-grant it.
- Espresso: Hamcrest pinned to 2.2 for androidTest; `scrollTo()` does not work inside `NestedScrollView` (use the test's
  `scrollIntoView()` helper).
- **Room:** since database version 3 it holds phone-only data (boarding passes, ID documents, cards), so every new version needs a
  migration (`@AutoMigration`); never rely on the destructive fallback. Room is the UI's source of truth.
- **ID privacy:** Aadhaar = last 4 digits only, ever; PAN always shown masked; IDs never leave the phone; the database is
  excluded from cloud backup (`res/xml/data_extraction_rules.xml`).
- Backend reads keys from env vars (and a git-ignored root `.env` for local dev). Empty key = MOCK. Tests set providers explicitly.
- Backend binds to `127.0.0.1` by default (`SERVER_ADDRESS`); demo controls exist only with `APP_MODE=demo` (see backend/README.md).

## Phase status
- [x] Phase 0 — Setup (done 2026-10-10) · old phases 1–2 built (now parked) · backend hardening done (branch
  `feature/backend-hardening`, PR not opened yet)
- [x] R0 — Refocus (done 2026-10-11)
- [x] G1 — Airport map (done 2026-10-11)
- [x] G2 — Routing (done 2026-10-11)
- [x] G3 — Navigation UX + positioning (done 2026-10-11; the ongoing notification moved to G4)
- [ ] G4 — Gate changes (waits for Disruption Recovery C1 in main: GateWatcher builds on its flight model)
- [ ] B1 — Bag Shield capture
- [ ] B2 — Counter & Station
- [ ] B3 — Problems & claims
- [ ] P — Polish & demo
- [x] A1 — Ask OnBoard Quick answers (done 2026-10-11) · [ ] A2 — On-device AI (go-ahead 2026-10-11) ·
  [ ] A3 — Cloud AI + bookings · [ ] A4 — Router & polish (§3E)

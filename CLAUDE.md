# CLAUDE.md — OnBoard

Hackathon project for **Elevate 1.0 (DJSCE), Problem Statement 01 — "Seamless Aviation Journey Experience"**.
4-person college team, strong in **Java**. This file is the condensed project brief. Every session follows it.
If a decision changes, update this file in the same change.

**Direction (since 2026-10-11): four main features, Gate Navigation, Disruption Recovery (§3D), Baggage Shield and the
Offline Travel Document Vault (§3C), plus (added 2026-10-11) Ask OnBoard, the multilingual assistant (§3E).** The
trip timeline UI and the old bookings / Plan B plan stay **parked**, not deleted (see "Parked" below); the ripple engine,
flight watcher and disruption service are **un-parked** as the one engine behind Disruption Recovery (never a second
disruption engine).

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
- **C. Document Vault** — an encrypted, trip-by-trip folder of travel documents (passport, visas, tickets, boarding passes,
  hotel, insurance, ...) that opens with no internet, with pre-departure checks and offline country guidance (§3C).
- **D. Disruption Recovery** — when a delay or cancellation breaks a connection, OnBoard spots it early, explains it in local
  airport time, prepares the airline request, ranks realistic alternatives, helps ask for overnight assistance and keeps the
  recovery plan on the phone for offline use (§3D).

| PS 01 requirement | Where we meet it |
|---|---|
| Navigate airports | Indoor map, blue dot, turn-by-turn, landmarks |
| Gate changes / disruptions | Gate watcher → push → automatic re-route |
| Missed connections / cancellations | Disruption Recovery: server keeps watching, connection check with sourced minimum times, request draft, ranked alternatives, offline Plan B pack |
| Track baggage | Bag tag registered, status from drop to belt |
| Baggage problems | Damage / delay / loss guide, PIR help, claim pack, deadlines |
| Offline, on-device vs cloud | Map, routing, evidence photos and guides work offline; photos upload when online; the Document Vault is on-device only |
| Travel documents / disruptions abroad | Document Vault: encrypted offline copies, pre-departure check, "If you're stuck" guide, emergency card |
| Traveller profiles | Step-free route, slower walking speed, large text, voice, assistance info |
| Multilingual | English, Hindi, Marathi now (Tamil, Bengali next); voice via TTS |
| UI/UX | One clear next instruction at a time; nothing extra on screen |

**Out of scope:** real indoor positioning hardware, airline system integration, submitting forms on the user's behalf, legal
advice, any airport other than the pilot (for navigation). Disruption Recovery: trains and buses (no reliable data source) and
live safety scoring (a country pack's official advisory link at most).

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
    - Ongoing notification while navigating (`NavNotification`): "Gate 32 · 4 min", "30 m · Turn left at …",
      "Arrive 07:12 (estimate)" ("Demo walk (simulation)" during the demo walk). It is shown as a foreground
      service (`NavigationService`, type `health`) only when step counting is allowed, because Android only sends
      step events to a background app that runs one. Otherwise it is a plain notification. `POST_NOTIFICATIONS` is
      asked at Start; navigation works the same without it. G4 adds the gate source and "Boarding closes" to it.
    - No toolbar on this tab, and the bottom tabs are hidden while navigating.
  - Positioning as built:
    - QR anchors via Google's code scanner: Google Play services shows the camera, so no camera permission is needed.
    - "I'm here" on a tapped place, or "Where are you now?" (choose a place).
    - Steps along the route: `TYPE_STEP_DETECTOR`, which needs `ACTIVITY_RECOGNITION`.
    - Turn confirmation from `TYPE_GAME_ROTATION_VECTOR`: only CHANGES of heading near the next turn are used, so no
      true north is needed.
    - Demo walk in debug builds, labelled as a simulation.
    - Off-route = a QR / chosen place that is not on the route: re-route from there and say so.
    - "Missed a turn? Scan a QR code or tap I'm here" (`NavSession.missedTurn`): counted steps went > 20 m past a
      turn that nothing confirmed (no matching heading change, QR code or chosen place). Shown as a card, said once,
      and put in the notification. Only on phones that report a heading, never for slight bends, never on the demo
      walk. A matching turn up to 20 m AFTER the corner still counts (the steps ran ahead), so the hint stays rare.
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

## 3D. Ride handoff to Uber (built 2026-10-11)
- **Deep links only.** We never use the Uber Drivers API (it serves a logged-in driver's own data) or the rider
  ride-request API (needs partner approval). The universal link `https://m.uber.com/ul/?action=setPickup&client_id=…&
  pickup=my_location&dropoff[latitude|longitude|nickname|formatted_address]=…` comes from Uber's own Android SDK
  (`Deeplink.APP_LINK_URI`, `RideRequestDeeplink`) and the deep-link guide's `action=setPickup` example (checked
  2026-10-11). Built in `data/ride/UberDeepLink` (pure Java, unit tested).
- `RideProvider` interface: `UberDeepLinkProvider` (real, needs no key; ACTION_VIEW, then a forced browser, then a friendly
  message) and `MockRideProvider` (demo dialog). `RIDE_PROVIDER=mock` / `UBER_CLIENT_ID` in `android/local.properties`.
  Add other providers only with a link format from their official docs.
- One shared button `ui/ride/RideButtonView`, on Home: flight card (to the pilot terminal, with "Leave by …
  (estimate)"; hidden when the flight is cancelled) and "After you land" (hotel from the trip, or a typed / saved
  place). **Recovery-alternative ride button to be added after C3 merges:** a ride to the nearby airport an alternative
  leaves from, with the configured transfer time labelled "estimate". The data part is ready (`RidePoints.nearby` +
  `nearbyAirports` in ride-points.json, unit tested); the button and a `RidePlacement.RECOVERY` come with C3's screens.
- **Honesty:** after a tap only "Opened in Uber"; never booked / driver / car / ETA / price. Airport points live in
  `mock-data/rides/ride-points.json` with `sourceUrl` + `lastChecked` + `verified`; unverified = "Approximate
  drop-off point". Current points = OpenStreetMap terminal-building centres, `verified: false`. Offline: the link
  still opens, with "You're offline. Uber needs internet to book."
- **Privacy:** pickup is `my_location` (Uber finds it), so no location permission. Typed places stay on the phone
  (excluded from backup). The Document Vault is NOT read (no address field; vault data stays behind its lock; using it
  for rides needs the user's consent and a vault-side change). Tap counts per button are local only (Profile shows them).

---

## 3C. Feature C — Offline Travel Document Vault
- **Goal / demo persona:** Paramvir (Indian passport) flies Delhi (DEL) → Kigali, Rwanda (KGL, transit) → Kinshasa, DRC (FIH),
  is stranded in Kigali by an outage, and must show passport details, visa, tickets, boarding passes, hotel, insurance, onward
  flight, emergency contacts and transit/entry documents **fully offline**. His data is a made-up SAMPLE (`SampleVault`).
- **Features:** trip folders grouped by leg and by type, shared documents stored once and linked to many trips; add by camera
  scan (auto edge-crop, multi-page; manual crop fallback), PDF/image import or manual entry; passport MRZ (ICAO 9303, check
  digits) and BCBP read on the phone, the user always confirms fields; pre-departure checklist PASS / WARN / FAIL / UNKNOWN with
  reason + source; expiry reminders 90/30/7 days; "make offline-ready" (decrypt-test every file, total size); viewer (zoom, page
  swipe, number masked until biometric unlock, PdfRenderer); present mode (full screen, max brightness, one document, swipe
  locked, banner "Copy for reference. Original document available." in English + local language); offline country guidance
  (IN, RW, CD); "If you're stuck" guide linked to the folder's documents; emergency card in two languages (medical notes opt-in,
  phone only); settings: lock, auto-lock, screenshot blocking, optional encrypted backup, "emergency packet" PDF export.
- **Security (built in V1):** separate SQLCipher database + files in `no_backup/vault/` (never in cloud backup / device
  transfer); envelope encryption: Android Keystore master key (AES-256-GCM, not exportable) wraps the database key, one AES-256-GCM
  key per file, and document numbers (encrypted a second time); SHA-256 per file, checked on every open and in "Check all
  files"; BiometricPrompt (strong biometric or PIN; weak biometric or PIN on Android 9–10), auto-lock 1/5/15 min (default 5),
  FLAG_SECURE on every vault screen and dialog unless switched off; full delete = folder + Keystore key + settings. The Keystore
  key is deliberately NOT bound to biometrics in V1 (reasons in `docs/privacy.md`). No Jetpack security-crypto.
- **Honesty (strict):** never "verified": the app says "Checked". Vault home and present mode say copies don't replace
  originals. Never invent visa/transit/vaccination rules, embassy addresses or phone numbers. Country data lives in versioned
  JSON packs (IN, RW, CD) filled only from official sources, each item with `sourceUrl` + `lastChecked` + "Confirm with the
  airline or embassy before you fly."; unchecked rules are left out and show UNKNOWN. Paid rule services (IATA Timatic) only
  behind a `RequirementProvider` interface; the demo uses the JSON pack provider.
- **Privacy:** India's DPDP Act 2023 basics (minimum data, clear consent, full delete) in `docs/privacy.md`. Backup (V6) is off
  by default, encrypted on the phone before upload, server stores ciphertext only, restore needs a recovery phrase.
- **Read-only API for other features (`VaultSummaryReader`, decided 2026-10-11):** the ONLY way Ask OnBoard and the
  Ride hand-off may learn about vault documents. `ServiceLocator.get().getVault().summaryReader()` →
  - `List<DocumentSummary> getSummaries(String tripId)` (background thread; or the overload with a main-thread
    `VaultRepository.Callback`); `TripRef currentTrip()` (dates cover today, else next, else most recent; title null
    while locked); `boolean isUnlocked()`.
  - `DocumentSummary`: `documentId` (opaque), `type`, `holderFirstName`, `expiryDate`, `coveredFrom`/`coveredTo`,
    `coveredCountries` (ISO2), `status` (`SummaryStatus`: MISSING / EXPIRED / MISMATCH / EXPIRES_SOON (printed expiry within 90 days) /
    NOT_OFFLINE_YET / READY, worst first), `sample`, and for HOTEL only `hotelName` + `hotelAddress`.
  - **Never** returned: document numbers, MRZ lines, images, file paths or ids, full names, dates of birth (not even
    fields of the class; `VaultSummariesTest` checks fields and values).
  - **Locked vault:** only HOTEL bookings, with only `hotelName`, `hotelAddress` and `sample` (`lockedView` = true).
    Everything else needs unlocking: `ui.vault.VaultUnlockPrompt.attach(fragment, onUnlocked)` in onViewCreated, then
    `.show()` on the traveller's tap (one BiometricPrompt per screen).
  - **Callers allowed:** Ask OnBoard (on-device answers only, never sent to a cloud model) and Ride (hotel address only,
    used on the phone). Nothing from the reader goes to the backend or any server. MISSING means a saved file is gone
    from the phone, NOT "a required document is not saved" (requirements need sourced packs, V4).
- **Open question for the team:** the older Wallet (Profile > More; boarding passes, Aadhaar last-4, PAN, visas) is NOT
  encrypted. Folding it into the vault is not decided yet.

---

## 3D. Feature D — Disruption Recovery (branch `feature/disruption-recovery`)
- **Three triggers, one `RecoveryPlan`:** DELAY_MISSED_CONNECTION (flight 1 late, connection at risk or missed);
  CANCELLED_BEFORE_DEPARTURE (plan starts at the departure airport; demo: `trigger-cancel`); CONNECTION_CANCELLED_AT_HUB
  (plan starts at the hub; demo: `cancel-connection`). Demo trip 3: **IJ 1907 Delhi 17:50 → Doha 19:35, MJ 31 Doha 21:55 →
  London 03:10** (+1 day; fictional airlines IndiJet / Marjan Air), flight 1 leaves 95 min late.
- **The one technical truth:** a phone in the air without Wi-Fi learns nothing. The SERVER keeps watching (FlightWatcher) and
  rebuilds the plan; the phone gets it on reconnect (push + refresh). A late departure known before boarding → the app downloads
  a **Plan B pack** at the gate. Everything offline shows "as of HH:MM" and "may be outdated".
- **Engine:** RippleEngine + FlightWatcher + DisruptionService (un-parked). `DisruptionService.apply` rechecks the trip's
  connections (and from C2 rebuilds the plan) in the SAME transaction. Shared plain Java in **`recovery-core`** (same pattern as
  `routing-core`): connection checker now, ranking in C2, so the phone does the same maths offline.
- **Connection check (built in C1, `ConnectionChecker`):** available = flight 2 departure − flight 1 projected landing (provider's
  revised time, else timetable + delay). MCT from `mock-data/recovery/airport-rules.json` by airport + connection type
  (DOM/INTL each side) + terminal change (unknown terminals → the stricter rule, said in the reason). Practical time = taxi +
  deplaning + transfer security (team ESTIMATES) + gate closing. Order: RESOLVED / REBOOKED_BY_AIRLINE (confirmed facts) →
  CANCELLED → CONFIRMED_MISSED (flight 2 left while flight 1 had not landed, or confirmed earlier; kept once known) →
  LIKELY_MISSED (flight 2 leaves before landing) → UNKNOWN (no MCT) → LIKELY_MISSED (available < MCT) → AT_RISK (available <
  max(MCT, practical) + 15 min margin, a product choice) → OK. Only the official/demo MCT can say "likely missed"; estimates only
  warn. Every status has a certainty: PREDICTED (OK, AT_RISK, LIKELY_MISSED) / CONFIRMED / UNKNOWN; the UI styles them apart.
  Reason in plain words, local airport time: "You land in Doha at 21:10, your next flight leaves at 21:55. Doha needs at least
  60 min (demo value). You have 45 min."
- **Sourced numbers (checked 2026-10-11):** Doha MCT = **60 min DEMO VALUE** (no official airport-wide figure is public; airline
  figures cover only their own flights) → every result that depends on it says "based on a demo minimum connection time".
  Doha gate closes 20 min before departure = OFFICIAL (Hamad International Airport, Transfers page). Delhi and Dubai: no official
  MCT found → UNKNOWN (Dubai's handling for the C2 "via DXB" route is a team decision). Default gate close 20 min and transfer
  times 10 + 10 + 20 min are team ESTIMATES.
- **Ticket type:** SINGLE_BOOKING (airline normally responsible; check if already rebooked, send the request, go to the transfer
  desk) · SEPARATE_TICKETS (second airline may see a no-show; fare differences, each ticket's rules, bags may not transfer) ·
  UNKNOWN (ask once, explain the difference).
- **Airline request (C4):** built on the phone (passenger names from the phone only, never on the server): PNR, flights, scheduled
  vs projected times, delay/cancellation, minutes short, the request (rebooking or refund; meals/hotel if overnight); email +
  short chat/SMS text, English + Hindi or Marathi; `AirlineChannel` (default: mail app / share sheet; a labelled MOCK airline API
  for the demo). States DRAFT, OPENED_IN_MAIL_APP, MARKED_SENT_BY_PASSENGER, SUBMITTED_VIA_API (only after a real success). Never
  say "sent" otherwise.
- **Alternatives (C2):** `AlternativeFlightProvider` (mock schedule file = SAMPLE DATA; optional Duffel test mode). Same airline,
  other airlines, other hubs (e.g. via DXB), nearby airports only if the rules file lists a sourced ground transfer time. Every
  connection checked against MCT; transit visa via `TransitRequirementProvider` (Vault checks if present, else UNKNOWN "check
  transit requirements"); seats VERIFIED only from a provider, else "availability unknown"; separate-ticket risk shown. Urgency
  NORMAL (arrival, then fewest connections, then cost) or URGENT (arrival dominates). ONE recommended option with "why this
  one" + up to 2 others. **Never book automatically**; booking/payment only after explicit approval of price and conditions
  (sandbox only in the demo).
- **Overnight (C2):** best option more than N hours away (config) or next day → Overnight section from `AirlinePolicyProvider`
  (JSON pack per airline, sourceUrl + lastChecked); unknown → "Ask the airline whether meals, a hotel or transport are provided
  under your ticket." Passenger-rights rules "may apply" with official links; never promise accommodation or compensation; never
  book a hotel without approval; "Keep all receipts".
- **Next steps (all triggers):** 1) check whether the airline already rebooked you; 2) ask for rebooking or refund (prepared
  request); 3) compare alternatives; 4) check transit/visa for the chosen route; 5) overnight: the airline's policy (UNKNOWN if
  not in the pack); 6) confirm your choice (booking/payment only after explicit approval). At a hub: "Follow signs to Transfers,
  go to the airline's transfer desk, show this screen; if closed, use the contact channels below." No invented desk locations
  or gates.
- **Offline + resume (C3, C5):** Room keeps itinerary, flights, connection status + reason, drafts, top alternatives as last
  fetched, overnight guidance, contact channels, emergency contacts, next steps; each with last-updated time and "may be
  outdated" past a threshold. Offline uses only downloaded data and says so. On reconnect: refresh, recompute, "What changed
  since you went offline", then the next action.
- **Working vs mock vs future:** working = watcher, ripple, connection check, ranking, drafts, mail-app channel, offline pack,
  sync. MOCK / SAMPLE DATA = flight status (demo buttons), alternative schedules, fictional airlines' policies, the airline-API
  channel, the push sender without Firebase credentials. Future = Duffel test adapter, real airline APIs, a real MCT feed.


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
- Document Vault: SQLCipher for Android + androidx.sqlite (encrypted Room DB) · androidx.biometric · Android Keystore +
  `javax.crypto` AES-256-GCM · (V2) ML Kit Document Scanner + text recognition (MRZ) · (V3) `PdfRenderer` · WorkManager
  (reminders, pack updates).
- Localization: `values/` + `values-hi/`, `values-mr/` now; `values-ta/`, `values-bn/` later. Instruction templates localised too.
- **Look & brand (2026-10-11, from the team's Figma file "Tea"):** colours are VALUES of the existing `md_theme_*` names
  (blue #1B4DFF, warm background #F5F4EF, navy ink #0B1626, orange tertiary; white cards); screens use `?attr/` theme
  colours, never hex codes. App icon = the "A" plane mark, redrawn as vector paths (`ic_launcher_foreground.xml`).
  Android 12+ launch screen = `Theme.SafarSaathi.Splash` on MainActivity (`values-v31/themes_splash.xml`, no library).
  The logo badge (`drawable-xxxhdpi/splash_branding.png`) is a capture of the Figma canvas: replace it with the
  designer's PNG/SVG export when we get edit access. The full Figma redesign (41 screens, 4 tabs) is NOT adopted.

### Backend — `/backend`
- **Spring Boot 3**, Maven wrapper, packages under `com.safarsaathi.backend`. PostgreSQL via Spring Data JPA + **Flyway**; local
  `docker-compose.yml`; hosted = Supabase / Render / Railway.
- `GateWatcher` (reuses `FlightStatusProvider`) · FCM sender (mock without credentials) · evidence store (local disk in dev;
  MinIO optional) · Ed25519 + hash chain · claim pack PDF (PDFBox or OpenPDF) · static pages `/counter`, `/station`, `/demo`.
- Shared: `routing-core` Java module (graph, A*, instructions) with JUnit tests; `recovery-core` (connection checker, later
  ranking of alternatives) with JUnit tests, same pattern.

### Repo layout
```
/android  /backend  /routing-core (G2)  /recovery-core (shared plain Java, compiled by both builds; see its README.md)
/mock-data (airports/BOM-T2/ from G1, recovery/)  /docs  docker-compose.yml  CLAUDE.md  README.md
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
Disruption Recovery (backend; Room copies from C3):
```
Block (+C1)      startZone, endZone (every block); scheduledStart, scheduledEnd (never change; startTime/endTime = latest plan);
                 FLIGHT only: flightNo, depAirport, arrAirport, depTerminal, arrTerminal, flightPhase(SCHEDULED|BOARDING|
                 IN_AIR|LANDED). API "flight": depZone/arrZone, scheduledDep/Arr, estimatedDep/Arr, status (derived)
Connection (C1)  id, tripId, inboundBlockId, outboundBlockId, airport, connectionType, terminalChange, mctMinutes, mctKind,
                 mctSource(+Url, lastChecked), transferMinutes, gateCloseMinutes(+kind, source), ticketType, status,
                 availableMinutes, projectedSlackMinutes, basedOnDemoValue, reason, missConfirmedAt, updatedAt, dataAsOf
RecoveryPlan (C2)       id, tripId, trigger, startAirport, version, generatedAt, basedOnDataAt, urgency, steps (JSON),
                        recommendedOptionId, alternatives, overnightInfo, isStale
AlternativeOption (C2)  id, planId, legs (JSON), departs, arrives, stops, connectionChecks, transitVisaStatus(OK|REQUIRED|
                        UNKNOWN), seatAvailability(VERIFIED|UNKNOWN), fareDifference, separateTickets, changeRules,
                        source(SAMPLE|PROVIDER), rank, reason
AirlineRequestDraft (C4, phone only)  id, planId, channel, subject, bodyEnglish, bodyLocal, shortText, state, stateChangedAt
AirportPack (C1, file)  iata, city(en/hi/mr), country, timeZone, connectionRules, gateClose, transfer, nearbyAirports,
                        sourceUrl, lastChecked                          -> mock-data/recovery/airport-rules.json
AirlinePolicyPack (C2, file)  airlineCode, delayAssistanceText, contactChannels, sourceUrl, lastChecked
```
Document Vault (app only, its own SQLCipher Room DB `VaultDatabase`, ids are UUID strings):
```
Traveller        id, name, nationality(ISO2), dateOfBirth, sample
TripVault        id, title, startDate, endDate, offlineReadyAt, sizeBytes, sample   (+ trip_travellers)
TripLeg          id, tripId, position, kind(FLIGHT|TRANSIT|STAY), title, fromCode, toCode, countryIso2, startDate, endDate
TripCountry      tripId, iso2, role(ORIGIN|TRANSIT|DESTINATION), fromDate, toDate
TravelDocument   id, travellerId, placeName + address (HOTEL, v2), type(PASSPORT|ID_CARD|VISA|E_VISA|TRANSIT_VISA|ENTRY_FORM|TICKET|BOARDING_PASS|HOTEL|
                 INSURANCE|VACCINATION_CERT|OTHER), title, holderName, numberEncrypted, issuingCountry, issueDate, expiryDate,
                 validFrom, validTo (tickets: the flight day), coversCountries, entries, flightNo, bookingRef,
                 source(SCAN|PDF|MANUAL|IMPORT), fieldsConfirmed, sample
VaultFile        id(=file name), documentId, page, mimeType, sizeBytes, sha256, wrappedKey   ("fileRef + sha256", per page)
TripDocumentLink tripId, documentId, legId (null = whole trip)
Later: CountryPack(iso2, version, requirements, embassies, emergencyNumbers, phrases, layoverTips) · Requirement(iso2,
nationality, role ENTRY|TRANSIT, docType, ruleText, params, mandatory, sourceUrl, lastChecked) · CheckResult · EmergencyCard
```
Demo roles: PASSENGER (app), COUNTER (staff page), STATION (arrival page), ADMIN (demo page); simple demo login, no real auth.
Times: `java.time` everywhere. Stored as an exact moment + the zone of its place (a flight: departure / arrival airport) and
shown in that local time; times that belong to no place (createdAt, "as of") in **Asia/Kolkata**.

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
GET  /api/country-packs/{iso2}                      vault country pack (cacheable JSON)        (V5)
GET  /api/country-packs/versions                    pack versions for the update check          (V5/V6)
PUT|GET /api/backup/{userId}                        encrypted vault backup blobs only           (V6)
GET  /api/trips/{id}                                trip + blocks (+C1: local zones, "flight" fields) + links
GET  /api/trips/{id}/connections                    connection status, certainty, reason, sources       (C1, built)
GET  /api/connections/{id}/plan                     the recovery plan                                    (C2)
POST /api/connections/{id}/ticket-type   POST /api/connections/{id}/urgency                              (C2)
GET  /api/connections/{id}/alternatives                                                                  (C2)
--- demo only (APP_MODE=demo; 404 in prod) ---
POST /api/demo/trigger-delay {tripId, blockId, minutes}   POST /api/demo/trigger-cancel {tripId, blockId}   (built)
POST /api/demo/cancel-connection · set-flight-phase {flightNo, phase} · flight-departed {flightNo} ·
     airline-rebooked {connectionId, newFlight}                                                          (C2)
POST /api/demo/trigger-gate-change {flightNo, toGate}
POST /api/demo/bag-status {tag, status, beltNo}
POST /api/demo/reset
POST /api/demo/seed-vault   Paramvir's SAMPLE documents (the app can also load them offline from Vault home)
GET  /api/health                                    app status + MOCK/REAL provider modes
```

## 7. App screens (bottom tabs: Home · Navigate · Bags · Help · Profile)
- **Start (2026-10-11):** Android 12+ launch screen (navy, A mark) held 1.5 s; first start opens a demo sign-in (`ui/login/LoginActivity`: name or guest, kept on the phone, no password/account/server). Brand font Plus Jakarta Sans bundled in `res/font` (OFL, `docs/licenses/`), Material type scale in `values/themes_type.xml`; filled buttons navy per the Figma (`values/themes_buttons.xml`). The login screen is NOT a Figma frame (none exists).
1. **Home:** flight card with gate status and boarding countdown, "Navigate to gate", bag summary.
2. **Navigate:** map + instruction banner + bottom sheet, QR "You are here", simulate-walk (demo builds only), steps list,
   step-free toggle, help sheet.
3. **Bags:** register bag (scan tag) → guided 4-photo capture → signed receipt; status timeline; before/after compare;
   "Something went wrong". Until B1 is in the app, the tab starts with ONE hardcoded bag labelled SAMPLE DATA
   (`layout/view_sample_bag.xml`, Figma B01; belt "not announced yet"); remove it when B1 lists real bags.
4. **Help:** "What happened?" decision tree; CSMIA Lost & Found card with source and last-checked date.
5. **Profile:** language, voice guidance, elderly / step-free defaults, large text, notifications; "More" links to the
   **Document Vault** (nested nav graph `vault_graph`), the Wallet (boarding passes, Aadhaar last-4 / masked PAN / visas, PDF)
   and, with the flag on, the parked screens.
6. **Disruption Recovery (C3-C5):** Home "At risk" connection card (independent of the parked timeline flag; chip On track /
   At risk / Likely missed / Missed / Cancelled / Rebooked) → connection detail (two-flight diagram, local times, minutes
   available vs needed, reason, data time stamp, ticket-type question) · Plan B pack banner before boarding · Recovery screen
   (trigger summary, numbered next steps, urgency toggle, recommended option + up to 2 others, request draft card, overnight,
   contacts) · alternative detail · request draft preview (Send / Copy / Share, honest state) · "What changed" after reconnect.
   Predicted and confirmed look clearly different, never by colour alone.
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
- Disruption Recovery (its own track and branch `feature/disruption-recovery`, same stop-after-each rule; every phase summary
  lists its changes to shared files: CLAUDE.md, README, demo page, Home screen):
  - **C1 — Data and connection check:** zone-aware times, scheduled vs estimated on the server, flight block fields, airport
    rules file (zones, MCT with source / demo label, nearby airports), demo trip 3, `recovery-core` connection checker,
    MISSED → LIKELY_MISSED, `GET /api/trips/{id}/connections`, tests.
  - **C2 — Recovery plan on the server:** built in `DisruptionService.apply` for all three triggers, ticket type, urgency, mock
    alternatives + ranking + MCT and transit checks, recommended option with reason, overnight from policy packs, endpoints,
    demo buttons, rewrite `planb/` and `docs/architecture.md` (rank options, never book automatically).
  - **C3 — Offline plan on the phone:** Room v6 (`@AutoMigration`), Plan B pack at the gate, Home "At risk" card, Recovery
    screen, alternative detail, predicted vs confirmed styling, "as of / may be outdated". Airplane-mode test.
  - **C4 — Airline request:** drafts (English + Hindi/Marathi) assembled on the phone with local names, `AirlineChannel` (mail app
    / share sheet + labelled mock), honest draft states.
  - **C5 — Push and reconnect:** FCM with mock sender, WorkManager refresh, "What changed" summary, demo script, README and
    CLAUDE.md updates.
- Ask OnBoard (its own track, §3E, same stop-after-each rule):
  - **A1 — Quick answers:** intent matcher, lexicons, templates (en/hi/mr/Hinglish), voice in/out, Ask screen, golden tests.
  - **A2 — On-device AI:** model manager (Wi-Fi download, size, delete; disabled with a message on weak phones),
    MediaPipe LLM Inference, fact collection + keyword retrieval, translate in/out, grounding guard, fallbacks.
  - **A3 — Cloud AI:** backend Gemini + function calling, flight search + ranking (MCT check), hotel search, cab hand-off,
    quote cards, confirmation-token flow, sandbox booking (TEST BOOKING CONFIRMED / PENDING / FAILED).
  - **A4 — Router and polish:** router rules (offline, documents never cloud, battery < 15% / saver, search → cloud),
    source chips, settings (reply language, voice, model, "never send to cloud"), airplane-mode test, demo script.

- Document Vault (its own track, same stop-after-each rule):
  - **V1 — Secure vault core:** Keystore key, encrypted files, SQLCipher, biometric lock, auto-lock, FLAG_SECURE, trip folders,
    sample data, `docs/privacy.md`. Tests: encrypt/decrypt round trip, wrong key fails, hash check.
  - **V2 — Capture:** document scanner (check Play-services need + offline; manual crop fallback), PDF/image import, manual
    entry, MRZ parser (valid + bad check digits), BCBP parsing, confirm-fields screen.
  - **V3 — Viewing:** viewer, masked numbers (biometric reveal), present mode, offline PDF rendering.
  - **V4 — Checks engine (pure Java):** all pre-departure rules, check screen, expiry reminders. Golden tests: Paramvir happy
    path, missing DRC visa, passport expiring too soon, name mismatch, insurance ending early, unknown rule.
  - **V5 — Country guidance:** pack format, IN/RW/CD packs (sourced or left out), embassy + emergency info, phrases ("translation
    needs review"), stuck-in-transit guide, emergency card.
  - **V6 — Offline-ready and polish:** make offline-ready, pack update check, optional encrypted backup, emergency-packet PDF,
    airplane-mode test of every screen, Hindi + Marathi strings, vault part of `docs/demo-script.md`.

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
7. Disruption Recovery (trip 3, full script in C5): at the Delhi gate a 95-min delay → "Likely missed" with the reason in Doha
   time, Plan B pack downloads → in the air (airplane mode) the plan opens offline with "as of" times → on the server flight 2
   departs (confirmed missed) and the airline rebooks on tomorrow 07:40 → landing: push + "What changed" → draft opened in the
   mail app ("Opened in mail app", not "Sent") → second case: Doha-London cancelled at the hub, URGENT → route via DXB.

## 10. Definition of done (every phase)
- App and backend build cleanly; **all tests pass**; zero API keys still runs the full demo.
- No invented gates, belts, times or legal claims; estimates and demo data labelled.
- README and CLAUDE.md updated; short summary with how to try it and known gaps.

---

## Parked (old trip-timeline plan; not deleted)
- **What:** trip timeline, trip status (old Home), the old Plan B / bookings plan (purpose scoring, booking orchestrator), AI
  assistant placeholder, `/desk` placeholder. Code and tests stay and keep passing.
- **Un-parked (2026-10-11):** RippleEngine, FlightWatcher and DisruptionService: the engine of Disruption Recovery (§3D).
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
| SQLCipher for Android | 4.19.1 (+ androidx.sqlite 2.7.0) | Latest (checked 2026-10-11); its native lib is 16 KB-page aligned. |
| androidx.biometric | 1.1.0 | Newest stable (checked 2026-10-11); 1.2.0 never left alpha, 1.4.0 is alpha. |
| ML Kit document scanner | 16.0.0 (Play services) | Newest (checked 2026-10-11); screen + models from Play services, then offline. |
| ML Kit text recognition | 16.0.1 (bundled Latin model) | Newest (checked 2026-10-11); MRZ reading offline, no Play services; 16 KB aligned. |
| CameraX | 1.6.2 | Newest stable (checked 2026-10-11); the vault's own camera when Google's scanner cannot run. |
Exact Android library versions live in `android/gradle/libs.versions.toml`; backend versions come from the Spring Boot parent POM.

## Conventions & gotchas
- Times: `Instant` / `ZonedDateTime`; never `java.util.Date`. Backend gets "now" from an injected `Clock`. A time at a place is
  stored as the moment + that place's zone (`blocks.start_zone` / `end_zone`) and sent with its own offset (`TripMapper`; Jackson's
  `write-dates-with-context-time-zone` is off so +03:00 stays +03:00); times of no place use Asia/Kolkata.
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
- **Document Vault:** everything lives in `no_backup/vault/` (`Vault` class); `VaultDatabase` is separate from `AppDatabase` and
  phone-only, so every new version needs a migration (no destructive fallback). Every vault screen extends `VaultScreen` (lock
  cover + the screen's ONE BiometricPrompt; use `confirmIdentity()` for extra prompts) and sits inside nav `vault_graph`, which
  is what makes MainActivity set FLAG_SECURE; vault dialogs call `VaultWindow.secure(dialog)`. Decrypted bytes stay in memory,
  never on disk. Vault tests use their own folder + Keystore alias + settings file, never the real vault.
- **Vault V2 (adding documents):** pure-Java parts in `data/vault/capture/` (MRZ per ICAO 9303 with check digits,
  `DraftReader`, `DocumentDraft`, `DraftFields`, `CropMath`, `CountryCodes` from the JDK/Android ISO list). The add flow
  shares one `CaptureViewModel` scoped to `vault_graph` (pages in memory only). Google's scanner temp JPEGs are deleted
  right after reading. Other features read the vault ONLY through `VaultSummaryReader` (see 3C), never the DAO, the
  repository or the files. Vault database version 2 = hotel `placeName` + `address` (`@AutoMigration`; `VaultMigrationTest`
  builds a real v1 SQLCipher file from app/schemas/.../1.json, added as androidTest assets, and opens it with the app's
  own `VaultDatabase.open`).
- Backend reads keys from env vars (and a git-ignored root `.env` for local dev). Empty key = MOCK. Tests set providers explicitly.
- Backend binds to `127.0.0.1` by default (`SERVER_ADDRESS`); demo controls exist only with `APP_MODE=demo` (see backend/README.md).

## Phase status
- [x] Phase 0 — Setup (done 2026-10-10) · old phases 1–2 built (now parked) · backend hardening done (branch
  `feature/backend-hardening`, PR not opened yet)
- [x] R0 — Refocus (done 2026-10-11)
- [x] G1 — Airport map (done 2026-10-11)
- [x] G2 — Routing (done 2026-10-11)
- [x] G3 — Navigation UX + positioning (done 2026-10-11; ongoing notification + "Missed a turn?" added 2026-10-11)
- [ ] G4 — Gate changes (waits for Disruption Recovery C1 in main: GateWatcher builds on its flight model)
- [ ] B1 — Bag Shield capture
- [ ] B2 — Counter & Station
- [ ] B3 — Problems & claims (phone-only slice built 2026-10-11 on release/demo: Help/Bags "What happened?", claim screen with way to baggage services, photo SHA-256, Montreal deadline + calendar, claim pack PDF, escalation, Lost & Found; facts in mock-data/baggage/baggage-facts.json. Not yet: arrival status, station photos, server signature (need B1/B2), hi/mr strings. Waiting for the go-ahead)
- [ ] P — Polish & demo
- [x] C1 — Data and connection check (done 2026-10-11, merged into `main`)
- [ ] C2 — Recovery plan on the server · [ ] C3 — Offline plan on the phone · [ ] C4 — Airline request · [ ] C5 — Push and
  reconnect
- [x] A1 — Ask OnBoard Quick answers (done 2026-10-11) · [ ] A2 — On-device AI (go-ahead 2026-10-11) ·
  [ ] A3 — Cloud AI + bookings · [ ] A4 — Router & polish (§3E)
- [x] V1 — Secure vault core (done 2026-10-11)
- [x] V2 — Capture (done 2026-10-11)
- [ ] V3 — Viewing · [ ] V4 — Checks engine · [ ] V5 — Country guidance · [ ] V6 — Offline-ready & polish

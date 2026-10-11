# OnBoard ✈️ — your airport companion, even offline

**Elevate 1.0 (DJSCE) · Problem Statement 01 — "Seamless Aviation Journey Experience"**

OnBoard is an Android app (with a Spring Boot backend) that helps a traveller through the hardest parts of an airport
day: **finding the gate**, **handling a bag problem**, **keeping travel documents safe and usable offline**, and
**getting answers in their own language**. Pilot airport: **Mumbai CSMIA, Terminal 2**.

> Live backend: <https://onboard-backend-vkbe.onrender.com/api/health> (Render free plan: the first request after
> 15 idle minutes takes ~1 min to wake up).

---

## The problem

Airports are stressful in exactly the moments when phones are least useful: no signal inside the terminal, GPS that
can't tell floors apart, gate changes announced over a speaker in one language, damaged bags with no proof, and
documents locked in an email you can't open offline. Travellers in India also switch between English, Hindi and
Marathi. OnBoard is built around three rules: **works offline**, **one clear next step at a time**, **never invents a
fact** (no made-up gates, times, belts, legal outcomes or amounts).

## What OnBoard does

| Feature | What the traveller gets | Status |
|---|---|---|
| **Gate Navigation** | Google-Maps-style walking directions inside Mumbai T2: search, route preview, turn banner, heading-up map with a blue arrow, steps list, "Help nearby", voice guidance in English/Hindi/Marathi, step-free and slower-pace routes. Position from QR "You are here" codes, "I'm here", and step counting along the route. Ongoing notification (Android 16+: status-bar Live Update chip). Hand-off to Google Maps to get *to* the airport. | Built (G1–G3) |
| **Baggage Shield** | "What happened?" guide: damaged bag / bag didn't arrive / item missing / lost something. Report before leaving the hall with **"Show the way"** to airline baggage services on the T2 map and a QR for staff; before/after photos with **SHA-256 fingerprints**; the written-complaint **deadline** (Montreal Convention, source shown) with a calendar reminder; a **claim pack PDF**; escalation steps; CSMIA **Lost & Found** with official facts and portal. | Built on the phone (B3); server receipts on a branch (B1) |
| **Document Vault** | An encrypted, offline folder of passport, visas, tickets, boarding passes, hotel and insurance, grouped by trip and leg. Scan (auto edge-crop), import PDF/photo or type; passport MRZ and boarding-pass barcodes read on the phone with check digits; biometric lock, auto-lock, screenshot blocking. Sample trip: Delhi → Kigali → Kinshasa. | Built (V1–V2) |
| **Ask OnBoard** | Type or speak "Mera gate kahan hai?" / "Majhi bag kuthe aahe?" and get the answer in the same language, read aloud — offline, from the trip saved on the phone. | Built (A1, quick answers) |
| **Ride hand-off** | "Get a ride" opens Uber with pickup and drop-off filled in (public deep link, no key). The app only ever says "Opened in Uber". | Built |
| **Disruption engine (server)** | Trip timeline, flight watcher, ripple engine for delays/cancellations, connection check with sourced minimum connection times. | Built on the backend (C1); phone screens parked |
| **Polish** | Animated launch screen (Figma "Loading 1–3"), demo sign-in, Plus Jakarta Sans font, Figma colours, Home flight card, demo-scenario alerts for presentations. | Built |

## Demo in 3 minutes

1. **Launch** — the plane takes off, leaves a contrail and becomes the OnBoard badge. Sign in (demo, no password).
2. **Home** — flight IJ 512 Mumbai → Bengaluru. Gate says *"Not announced yet"* (we never guess).
3. **Navigate** — search "32" → Directions → scan the "Entry 4" QR (print `docs/qr-anchors.html`) → Start → **Demo
   walk**: turn banner, voice, steps, arrival estimate. Leave the app: the walk stays in the notification.
4. **Gate change** — Profile → **Demo scenario → Gate changed (32 → 41)**: a real notification, labelled DEMO.
5. **Bag damaged** — Bags → **My bag is damaged** → **Show the way** (route to baggage services) → **Show to staff
   (QR)** → add before/after photos (each fingerprinted) → PIR + date → *"Complain by Sat 17 Oct · 6 days left"* →
   **Save claim pack (PDF)**.
6. **Lost something** — Help → I lost something at the airport → CSMIA Lost & Found (official facts, source + date)
   → copy the report → official portal.
7. **Vault** — Profile → Document vault → fingerprint → Load sample trip → open the passport (number masked).
8. **Ask** — "Mera gate kahan hai?" → Hindi answer, spoken.
9. **Airplane mode** — map, routes, vault, Ask and the bag guide keep working.

The older trip-delay demo (server `/demo` page, parked trip timeline): [docs/demo-script.md](docs/demo-script.md).

## How it is built

```
Android app (Java 17, MVVM, Room, offline-first)  ──HTTPS──▶  Spring Boot 3.5 backend (Render, Docker)  ──▶  PostgreSQL 17
        │  shared plain-Java cores compiled into BOTH builds:            │
        ├── routing-core   airport map model, A* routing, turn-by-turn sentences (en/hi/mr)
        └── recovery-core  connection checker (minimum connection times with sources)
```

- **Offline first:** the T2 map, routing engine, instruction sentences, bag facts, vault and Ask all ship inside the
  app; the server adds fresher data when there is signal.
- **Every outside service has a mock:** the full demo runs with **zero API keys**; the backend logs which providers
  are MOCK vs REAL (`/api/health`).
- **Privacy by design:** vault data never leaves the phone (SQLCipher + Android Keystore, excluded from backup);
  bag photos and drafts stay on the phone; Aadhaar last 4 only, PAN always masked.

Details, diagrams and data flows: **[docs/architecture.md](docs/architecture.md)**.

### Tech stack

| Layer | Choices |
|---|---|
| Android | Java 17 · XML layouts · Material 3 · MVVM (ViewModel + LiveData) · Room · Retrofit/OkHttp/Gson · Jetpack Navigation · own Canvas map view · CameraX · ML Kit (barcode, document scanner, text recognition, language ID) · Google code scanner · TextToSpeech + SpeechRecognizer · SQLCipher · androidx.biometric · PdfDocument + ZXing · minSdk 26, target 37 |
| Backend | Spring Boot 3.5.16 · Spring Data JPA · Flyway · PostgreSQL 17 · scheduled watchers · Docker |
| Shared | `routing-core`, `recovery-core` (plain Java, JUnit-tested, compiled by both builds) |
| Hosting | Render Blueprint (`render.yaml`): free Docker web service + free Postgres 17, Singapore |
| Quality | 284 app unit tests · 192 backend tests (incl. shared cores) · instrumented UI tests per feature |

## Run it

**Just try it:** install the debug APK (built from branch `release/demo`) on an Android 8+ phone. It talks to the
hosted backend.

**Build the app:** JDK 21 (not 26), Android Studio Panda 3 or newer.

```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest      # APK in app/build/outputs/apk/debug/
```

`android/local.properties` (git-ignored): `BACKEND_BASE_URL=https://onboard-backend-vkbe.onrender.com/` for the
hosted server, or leave it empty for the emulator → laptop (`http://10.0.2.2:8080/`).

**Run the backend locally:** Docker Desktop running.

```bash
docker compose up -d                # PostgreSQL 17
cd backend && ./mvnw spring-boot:run   # http://localhost:8080/api/health  ·  demo controls at /demo
```

**Deploy:** Render → New → Blueprint → this repo, branch `release/demo`. `APP_MODE=prod` on a public address (demo
buttons answer 404); set `APP_MODE=demo` only while presenting. More: [backend/README.md](backend/README.md).

Everything else (troubleshooting, commands, keys, parked features): [docs/developer-guide.md](docs/developer-guide.md).

## Honesty rules we built in

- **No invented facts.** Missing data says so ("Gate not announced yet", "Belt not announced yet").
- **Estimates are labelled** "estimate"; demo data is labelled **SAMPLE DATA** / **DEMO SCENARIO**; the T2 map is
  labelled *"Demo schematic, not an official airport map"*.
- **Official facts carry their source and "last checked" date** and live in config files
  (`mock-data/baggage/baggage-facts.json`, `mock-data/rides/ride-points.json`), not in code.
- **No legal advice, no promised outcomes or amounts.** The app never books, pays or submits forms for the user.

## Known gaps and what comes next

- **Live gate changes (G4):** server gate watcher + push → automatic re-route mid-walk (today: demo alerts).
- **Bag evidence on the server (B1/B2):** upload with server timestamp, Ed25519-signed receipt, hash chain, counter
  and arrival-station pages — server part built on branch `feature/bag-shield`, not merged yet.
- **AI layers (A2/A3):** on-device Gemma and cloud Gemini with function calling; confirm-only bookings.
- **Vault V3–V6:** document viewer and present mode, pre-departure checks, country guidance packs, encrypted backup.
- **Disruption Recovery C2–C5** on the phone; real indoor positioning (BLE / Wi-Fi RTT) after an on-site survey.
- Hindi/Marathi strings need a native speaker's review; new B3 screens are English only for now.

## Repo layout

```
android/        Android app (Java)            backend/       Spring Boot backend
routing-core/   shared map + routing (Java)   recovery-core/ shared connection checker (Java)
mock-data/      demo trips, T2 map, sourced facts (bags, rides, airport rules)
docs/           architecture, API, demo script, privacy, string reviews, licences
render.yaml, Dockerfile   hosting          CLAUDE.md   project brief and rules for every work session
```

## Credits

Team OnBoard (4 students, DJSCE). Font: Plus Jakarta Sans (SIL OFL 1.1, `docs/licenses/`). Icons: Material Icons
(Apache 2.0). Map of T2 drawn by the team (not an official map). Facts from official CSMIA, Montreal Convention
(via the Canadian Transportation Agency) and Government of India pages, each with its source in the app.

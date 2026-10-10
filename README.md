# OnBoard

**Find your gate. Protect your bag.** OnBoard is an Android app with a Spring Boot backend that does two things for
air travellers, in their own language, even offline:

- **Gate Navigation:** Google-Maps-style walking directions inside the airport (pilot: Mumbai Terminal 2) to your
  boarding gate, with landmarks and voice. If the gate changes, you are told at once and the route changes with it.
- **Baggage Shield:** photograph your bag and scan its tag before you hand it over, and get a time-stamped record that
  cannot be changed later. If the bag arrives damaged, late or not at all, the app tells you exactly what to do, where
  and by when. Lost something at the airport? It points you to the right Lost & Found, with the form pre-filled.
- **Disruption Recovery** (added 11 Oct): when a late or cancelled flight breaks your connection, OnBoard spots it early,
  tells you why in local airport time, prepares the request to the airline and ranks realistic alternatives, and keeps
  the plan on your phone for when you have no internet. It never books anything without your explicit OK.

Built for **Elevate 1.0 (DJSCE), Problem Statement 01 — "Seamless Aviation Journey Experience"**.
The full project brief (rules, stack, phases) is in [CLAUDE.md](CLAUDE.md). Details are in [docs/](docs/).

---

## Where we are now (11 Oct 2026)

**Team:** Yash: backend · Divyesh: Android app · Anushree: integration testing.

**New direction (11 Oct):** the app now focuses on **Gate Navigation** and **Baggage Shield** ([CLAUDE.md](CLAUDE.md)
has the full brief and the phases R0 → P). We build it phase by phase and stop after each one for the team's go-ahead.

**What you see in the app now (R0):** the bottom bar is **Home · Navigate · Bags · Help · Profile**.

- **Home:** greets Aditi (the demo's sample traveller) and shows a flight card: the flight and its times from the
  backend, and **Gate / Boarding: "Not announced yet"**, because there is no gate source until phase G4 and the app never
  invents a gate or a time. "Navigate to gate" and "Register a bag" open the new tabs.
- **Navigate (G1-G3): Google-Maps-style walking directions inside Mumbai T2.** The map is always labelled **"Demo
  schematic, not an official airport map"**. Everything below works offline (map, routes and words ship inside the app).
  - **Find a place:** search bar ("32" finds Gate 32), Departures / Arrivals, icons for gates, security, washrooms,
    lifts and help desks.
  - **Directions:** tap a place → **Directions**. The app first asks **"Where are you now?"**: scan a QR "You are here"
    code, or choose your place. A phone can't locate itself inside a terminal, so the app never guesses.
  - **Preview:** e.g. "4 min (280 m) · via Security check", with the note "Walking time only, an estimate · security
    queues not included". Toggles: **Step-free** (lifts only) and **Slower pace**.
  - **Start:** green banner ("10 m · Turn left at Check-in row D", then "Then …"), and the map turns so you walk "up",
    following a blue arrow. Time, distance and arrival time (estimate) at the bottom. Voice guidance, **Steps** list,
    **Help nearby** (nearest help desk / washrooms / lift / buggy stop, then "Continue to Gate 32").
  - **What moves the arrow:** your steps along the route (an estimate, corrected when the phone notices you turning at
    the next corner), a QR code, or "I'm here" on a place. Off the route → a new route from there. Debug builds have
    **Demo walk** (×1 / ×4 / ×8), labelled as a simulation.
  - **Instructions:** in English, Hindi or Marathi (follows the phone's language).
  - **Getting to the airport:** "Directions to T2 (Google Maps)" on Home opens the real Google Maps app.
  - **For the demo:** print [docs/qr-anchors.html](docs/qr-anchors.html) (8 "You are here" cards).
- **Home → ride handoff to Uber (11 Oct):** "Leave by 06:10 (estimate)" + **Get a ride to Mumbai T2** on the flight card,
  and **After you land → Ride to your hotel** (the hotel from the trip, or a place you type). Each button only opens Uber (app, or
  Uber's website if the app is missing) with pickup and drop-off filled in, using Uber's public deep link: no Uber API,
  no key. Price and booking happen in Uber, so the app only ever says "Opened in Uber". Airport points come from
  OpenStreetMap and are labelled "Approximate drop-off point" until the team checks them
  (`mock-data/rides/ride-points.json`).
- **Bags, Help:** say what they will do and which phase builds them (B1, B3).
- **Profile:** traveller, this trip, large text, saved cards, and **More → Wallet** (boarding passes by photo or code,
  Aadhaar last 4 digits / masked PAN / visas, everything as one PDF).

**Disruption Recovery (C1 done and in `main`, backend only so far; C2 onward on `feature/disruption-recovery`):**
every flight now knows its
airports and shows its times in local airport time (landing in Doha = Doha time), keeps its timetable times next to the
latest estimate, and every change of planes is a **connection** with a status and a reason. Try it with the backend
running: `curl -X POST http://localhost:8080/api/demo/trigger-delay -H "Content-Type: application/json" -d
'{"tripId":3,"blockId":12,"minutes":95}'`, then <http://localhost:8080/api/trips/3/connections> says **LIKELY_MISSED**:
"You land in Doha at 21:10, your next flight leaves at 21:55. Doha needs at least 60 min (demo value). You have 45 min."
Doha's 60 minutes are a team-set **demo value** (no official figure is public); airports with no figure say **UNKNOWN**.
Sources: [mock-data/recovery/airport-rules.json](mock-data/recovery/airport-rules.json). The phone screens come in C3.

**Parked, not deleted:** the old trip-timeline plan (trip status, timeline, the delay demo). Its code and tests stay and
keep passing; its engine (ripple engine, flight watcher) now drives Disruption Recovery. To see it again, put `feature.tripTimeline=true` in `android/local.properties` and rebuild:
Profile → More then links to the parked screens. Details: "Parked" in [CLAUDE.md](CLAUDE.md).

| Branch | What's on it | State |
|---|---|---|
| `main` | backend hardening + R0 (new tabs, Home, Wallet, Profile) + Disruption Recovery C1 | stable |
| `feature/gate-navigation` | Gate Navigation G1–G3 (map, routing, Google-Maps-style navigation); G4 next | pushed; pull request to `main` after G4 |
| `feature/disruption-recovery` | Disruption Recovery C2 onward (C1 is in `main`) | separate worktree |
| `feature/backend-hardening` | already in `main`; its last commit `14c880b` is an accidental "commit all" of everyone's work | **do not merge** |
| `feature/ride-handoff` | Ride handoff to Uber (deep links): flight card + "After you land". Recovery-alternative ride button to be added after C3 merges. | committed locally (worktree `../OnBoard-ride`), not pushed |

**Backend hardening, in short** (details: [backend/README.md](backend/README.md)): listens on `127.0.0.1` by default
(the emulator still reaches it at `http://10.0.2.2:8080`); demo buttons exist only with `APP_MODE=demo`; two changes at
the same moment can't overwrite each other; a hosted server uses `SERVER_ADDRESS=0.0.0.0` and `APP_MODE=prod`.

---

## Quick start (new teammate)

1. **Install** [Git](https://git-scm.com/downloads), a **JDK 21** with `JAVA_HOME` pointing at it (Android Studio's
   bundled JDK works, see [Troubleshooting](#troubleshooting)), **Android Studio Panda 3 Patch 1 (2025.3.3 Patch 1)
   or newer** (Help → About shows your version), and **Docker Desktop**.
2. **Clone** the repo: `git clone <repo-url>`, then `cd` into the new folder.
3. **API keys (optional):** in the repo root, copy `.env.example` to `.env`: `cp .env.example .env` (macOS / Linux /
   Git Bash), `Copy-Item .env.example .env` (PowerShell) or `copy .env.example .env` (cmd). Then fill in any keys you
   have. No keys = mock data, and everything still works.
4. **Start the database** (required: from Phase 1 the backend stops at startup without it). Start Docker Desktop and
   wait for "Engine running", then run `docker compose up -d` in the repo root. After a few seconds,
   `docker compose ps` shows `(healthy)`. It keeps running in the background (stop it with `docker compose down`).
5. **Run the backend:** `cd backend`, then `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`).
   The first run downloads Maven and libraries, so give it a few minutes. On its first start it also creates the
   database tables and loads the three demo trips. It's ready when the log shows `OnBoard backend is ready` and a
   MOCK/REAL table. It stopped with "Connection ... refused"? See [Troubleshooting](#troubleshooting).
6. **Check it:** open <http://localhost:8080/api/health>. You should see `"status":"UP"` and every provider `MOCK`
   except `weather` (a service whose key you added in step 3 shows `REAL`).
7. **Open the app:** in Android Studio choose **File → Open** and pick the **`android/`** folder (not the repo root).
   If you have Android keys, copy the lines from `android/local.properties.example` into `android/local.properties`
   (Android Studio creates that file; keep its `sdk.dir` line).
   If Android Studio offers to upgrade the Android Gradle plugin ("Project update recommended" or the AGP Upgrade
   Assistant), decline. We stay on AGP 9.1.1 and Gradle 9.3.1 so every teammate's Android Studio can open the
   project; upgrading is a team decision.
8. **Run it:** in **Device Manager** create an emulator (API 26 or newer; choose a *Google Play* image, because push
   notifications need it later), select it and press the green **Run** button.

---

## Parked demo: trip delay (old plan)

**Parked since 11 Oct.** The new demo story (gate change mid-walk, bag evidence, claim pack) arrives with the new
phases. This one still works: put `feature.tripTimeline=true` in `android/local.properties`, rebuild, and open the
screens from **Profile → More**.

The story: demo trip 1 on the phone, a 90-minute flight delay fired from the laptop, and the phone showing which
bookings moved by themselves and which one needs the traveller's decision. It keeps working offline too.
[docs/demo-script.md](docs/demo-script.md) has the old 3-minute script.

1. **Database:** Docker Desktop running, then `docker compose up -d` in the repo root. Wait until `docker compose ps`
   shows `(healthy)`. The backend can't start without it.
2. **Backend:** `cd backend`, then `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`), and wait for
   `OnBoard backend is ready`. On an empty database it first loads the three demo trips from
   [mock-data/demo-trips.json](mock-data/demo-trips.json). Check <http://localhost:8080/api/trips/1>: trip 1, "Client
   visit: Mumbai to Bengaluru", with 6 blocks, dated tomorrow.
3. **App:** run it on the emulator. On Android 17 (API 37) it first asks "Allow OnBoard to find, connect to, and
   determine the relative position of nearby devices?": tap **Allow**, or it can't reach the backend on your laptop
   (see [Troubleshooting](#troubleshooting)). Profile → More → **Trip status** shows "Your trip is on track" with the
   flight, 08:00 → 09:45. **Trip timeline**: flight 08:00 → bags → cab 10:05 → client meeting
   12:00 (the star marks the anchor, the one block the traveller must not miss) → hotel 14:00 → dinner 20:00. All green.
4. **Demo page:** on the laptop, open <http://localhost:8080/demo>. It shows trip 1 as a live timeline, and its
   "Booking to disrupt" list starts on the flight.
5. **Delay:** press **Delay +90 min**. Within about 10 seconds (the screens ask the backend every 10 seconds while
   on screen; pull down to ask at once) Trip status turns red ("1 thing needs your decision", the flight "Delayed ·
   Planned for 08:00 · 90 min later"), and the timeline shows the flight at 09:30, bag claim and the cab moved by themselves
   (amber "Auto-adjusted": the cab change is still free), the client meeting in red ("Needs you", 50 minutes short)
   and a red banner: "1 thing needs your decision: Client meeting in Whitefield". Hotel and dinner stay green ("On
   track") because they have time to spare. Every press adds another 90 minutes.
6. **Reset:** press **Reset demo**. The demo trips are loaded again (all green, dated tomorrow), and the phone shows
   the original plan within about 10 seconds.
7. **Offline:** stop the backend (Ctrl+C) or turn on airplane mode in the emulator. The screens keep showing the
   saved plan, with "Offline · showing your saved plan from HH:MM". The app keeps its own copy of the trip in Room (a
   database on the phone), and the screen only ever reads that copy.

Press **Reset demo** before every run: it also re-dates the trip to tomorrow, so the free-change windows (the cab's
ends 30 minutes before pickup) haven't passed yet. [docs/api.md](docs/api.md) has the endpoints behind the buttons.

---

## No API keys? No problem

Every outside service sits behind a Java interface with two versions: a **REAL** one that calls the service, and a
**MOCK** one that returns realistic fake data and needs no key. The backend picks one per service at startup:

| Service | Key (in `.env`) | Empty / missing means |
|---|---|---|
| Flight status (AeroDataBox) | `AERODATABOX_API_KEY` | MOCK: delays we trigger ourselves |
| AI text + chat (Gemini) | `GEMINI_API_KEY` | MOCK: ready-made template text |
| Directions (Google Routes) | `GOOGLE_ROUTES_API_KEY` | MOCK: estimate from distance |
| Push (Firebase Cloud Messaging) | `FIREBASE_CREDENTIALS_PATH` (file must exist) | MOCK |
| Meetings (Google Calendar) | `GOOGLE_CALENDAR_CREDENTIALS_PATH` (file must exist) | MOCK calendar |
| Voice (Sarvam AI) | `SARVAM_API_KEY` | MOCK: Android's built-in voice |
| Weather (Open-Meteo) | none needed | always REAL |

The backend logs the MOCK/REAL choice for each service when it starts, and `GET /api/health` shows it too. Key values
are never printed. To switch a service to REAL, put its key in `.env` (`.env.example` says where to get each one) and
restart the backend. For now, REAL only means "key found"; the real calls are built in the phase that needs them.

The Android app works the same way: an empty `MAPS_API_KEY` in `android/local.properties` means a mock/offline map
instead of Google Maps. **The whole demo runs on mocks, so never wait for a key.**
The ride buttons need no key at all: `UBER_CLIENT_ID` (optional) only credits sign-ups to our Uber developer app, and
`RIDE_PROVIDER=mock` shows a demo dialog (pickup, drop-off and the exact link) instead of opening Uber, for emulators
without Uber. Profile → Connection shows which one is on, and the ride button taps counted on this phone.

---

## Everyday commands

Run backend commands in `backend/`, Android commands in `android/` and Docker commands in the repo root.

| What | macOS / Linux / Git Bash | Windows (PowerShell or cmd) |
|---|---|---|
| Start the database | `docker compose up -d` | `docker compose up -d` |
| Check the database (wait for `(healthy)`) | `docker compose ps` | `docker compose ps` |
| Stop the database (data is kept) | `docker compose down` | `docker compose down` |
| Empty the database (**deletes local data**; the next backend start reloads the demo trips) | `docker compose down -v`, then `docker compose up -d` | `docker compose down -v`, then `docker compose up -d` |
| Run the backend (stop: Ctrl+C) | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
| Reload the demo trips, dated tomorrow (backend running; same as **Reset demo** on `/demo`) | `curl -X POST http://localhost:8080/api/demo/reset` | `curl.exe -X POST http://localhost:8080/api/demo/reset` |
| Backend tests | `./mvnw test` | `.\mvnw.cmd test` |
| Build the app (debug APK) | `./gradlew assembleDebug` | `.\gradlew.bat assembleDebug` |
| Android unit tests | `./gradlew testDebugUnitTest` | `.\gradlew.bat testDebugUnitTest` |
| Android UI tests (emulator running) | `./gradlew connectedDebugAndroidTest` | `.\gradlew.bat connectedDebugAndroidTest` |
| Stop Gradle's background process (frees RAM) | `./gradlew --stop` | `.\gradlew.bat --stop` |

PowerShell needs the `.\` in front; in cmd it's optional. The debug APK lands in `android/app/build/outputs/apk/debug/`.

---

## Repo layout

```text
.
├── android/             Android app (Java, XML layouts, Material 3). Open THIS folder in Android Studio.
├── backend/             Spring Boot 3 backend (Java 17, Maven): REST API, airport map, /demo page, ...
├── routing-core/        plain Java shared by backend and app: airport map model + checks, route finder, instructions
├── docs/                architecture.md, api.md, demo-script.md
├── recovery-core/       plain Java shared by backend and app: connection checker (Disruption Recovery)
├── mock-data/           demo seed data (JSON): demo-trips.json; airports/BOM-T2/ (demo schematic) from G1;
│                        recovery/airport-rules.json (time zones, minimum connection times with sources)
├── docker-compose.yml   local PostgreSQL 17 (the backend needs it from Phase 1)
├── .env.example         backend setting NAMES. Copy it to .env (git-ignored) for your own keys.
├── CLAUDE.md            the full project brief and source of truth
└── README.md            this file
```

---

## Progress

New plan (11 Oct 2026): Gate Navigation + Baggage Shield. Details of every phase: [CLAUDE.md](CLAUDE.md), section 8.

| Phase | What it delivers | Status |
|---|---|---|
| R0 | Refocus: new brief, new tabs (Home · Navigate · Bags · Help · Profile), old screens parked behind a flag | **Done** (11 Oct 2026) |
| G1 | Airport map: Mumbai T2 demo schematic, map endpoint, offline cache, map with floors and points of interest | **Done** (11 Oct 2026) |
| G2 | Routing: shortest walk, step-free option, walking speeds, landmark instructions (English, Hindi, Marathi) | **Done** (11 Oct 2026) |
| G3 | Navigation screen: next instruction, route line, blue dot, QR "You are here", simulated walk, re-route, voice | **Done** (11 Oct 2026; the ongoing notification moved to G4) |
| G4 | Gate changes: gate watcher, push alert, automatic re-route mid-walk, `/demo` trigger | Planned |
| C1 | Disruption Recovery data + connection check: local airport times, scheduled vs estimated, sourced minimum connection times, demo trip 3, `GET /api/trips/{id}/connections` | **Done** (11 Oct 2026, in `main`; backend) |
| C2 | Recovery plan on the server: three triggers, ticket type, urgency, ranked alternatives (SAMPLE DATA), overnight guidance | Planned |
| C3 | Offline plan on the phone: Plan B pack, Home "At risk" card, Recovery screen | Planned |
| C4 | Airline request drafts (English + Hindi/Marathi), mail app / share sheet, honest states | Planned |
| C5 | Push + reconnect: "What changed", demo script | Planned |
| B1 | Bag Shield capture: register bag, scan tag, guided photos, fingerprints (SHA-256), signed receipt | Planned |
| B2 | Counter & Station pages, tamper-evident hash chain, verification | Planned |
| B3 | Problems & claims: before/after, PIR guidance, claim pack PDF, deadlines, Lost & Found guide | Planned |
| P | Polish & demo: airplane-mode test, large text, Hindi/Marathi review, demo script, deploy notes | Planned |

Before the change of direction: Phase 0 (setup) was done, phases 1–2 (trip data, ripple engine, delay demo) were built
and are now **parked**, and the backend hardening is done on `feature/backend-hardening`.

A phase becomes **Done** only after the team's go-ahead. Then tick it in CLAUDE.md's "Phase status" list in the same
change, so the two files always agree.

---

## Troubleshooting

**Build fails mentioning Java 26 or "Unsupported class file major version 70".**
A JDK 26 is first on your PATH, and it's too new for our build tools. Point `JAVA_HOME` at a JDK 21. Android Studio's
bundled one works:

| Shell | Command (for the current terminal) |
|---|---|
| PowerShell | `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` |
| cmd | `set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"` |
| Git Bash | `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` |
| macOS | `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` |

To make it permanent on Windows, go to Start → "Edit the system environment variables" → Environment Variables and set
`JAVA_HOME`, then open a new terminal. Check it with `./mvnw -v` in `backend/` (Windows: `.\mvnw.cmd -v`), which
should say `Java version: 21...`.

**`./mvnw: Permission denied` or `./gradlew: Permission denied` (macOS / Linux).**
The script lost its "may be run" (executable) flag. Fix your copy with `chmod +x mvnw` in `backend/` (or
`chmod +x gradlew` in `android/`). Lasting fix for everyone: Git on Windows doesn't record that flag for new files
by itself. So whoever makes the first commit runs `git add --chmod=+x backend/mvnw android/gradlew` in the repo root
before committing (on Windows, a plain `git add .` leaves the flag out), then checks that
`git ls-files -s backend/mvnw android/gradlew` shows `100755` for both. Already committed as `100644`? Run
`git update-index --chmod=+x backend/mvnw android/gradlew` in the repo root and commit. Add any new shell script
with `git add --chmod=+x <file>` for the same reason.

**Android Studio says the project uses "an incompatible version" of the Android Gradle plugin (AGP).**
We use AGP 9.1.1 (needs Android Studio Panda 2 or newer) and compile against Android 17, API 37 (needs Panda 3
Patch 1, 2025.3.3 Patch 1, or newer). So use Panda 3 Patch 1 or newer: Help → About shows your version, Help → Check
for Updates installs a newer one. Don't downgrade the project.

**Backend stops with "Port 8080 was already in use".**
Something else is using port 8080. Either stop it, or run the backend on 8081: add `PORT=8081` to `.env` and set
`BACKEND_BASE_URL=http://10.0.2.2:8081/` in `android/local.properties`. To find the other program:
`netstat -ano | findstr :8080` (Windows; the last column is the process ID) or `lsof -i :8080` (macOS/Linux).

**I added a key to `.env`, but `/api/health` still says `MOCK`.**
First read the WHY column of the MOCK/REAL table in the backend's startup log (for example `GEMINI_API_KEY not set`
or `file not found: ...`). Then check that:

- the file is named exactly `.env` and sits in the repo root, next to `.env.example`. Windows Explorer hides file
  extensions, so a file made there may really be `.env.txt`: check with `dir` (Windows) or `ls -a`;
- it is saved as UTF-8. In Windows PowerShell 5, `echo ... > .env` writes UTF-16, which the backend can't read;
- each line is `NAME=value`, with no quotes and no `export` in front, and paths use forward slashes
  (`C:/Users/you/keys/firebase-key.json`);
- you restarted the backend. It reads `.env` only when it starts.

**The app can't reach the backend.**
Inside the emulator, `localhost` means the emulator itself. The emulator reaches your laptop at **`10.0.2.2`**, so the
app's default `BACKEND_BASE_URL` is `http://10.0.2.2:8080/` (keep the trailing `/`). Check that
<http://localhost:8080/api/health> opens on the laptop. On a real phone, the easiest way is a USB cable: turn on USB
debugging, plug the phone in and run `adb reverse tcp:8080 tcp:8080` on the laptop (again after every reconnect).
Now `localhost:8080` on the phone leads to the laptop, so set `BACKEND_BASE_URL=http://localhost:8080/` in
`android/local.properties` and rebuild. No other file needs changing. Wi-Fi only is the fallback: Windows Firewall or
college Wi-Fi often blocks it, and it needs a network-settings change you must not commit.
`android/local.properties.example` has both ways step by step, including where to find `adb`.

**A phone on Wi-Fi or another laptop can't reach the backend, but the emulator can.**
The backend listens on this computer only (`127.0.0.1`), so nobody else on the network can press the demo buttons.
The emulator (`10.0.2.2`) and a USB phone (`adb reverse`) still work. If you really need Wi-Fi, put
`SERVER_ADDRESS=0.0.0.0` in `.env` and restart the backend. Everyone on that Wi-Fi can then press the demo buttons,
so switch it back afterwards ([backend/README.md](backend/README.md) explains the settings).

**The Trip tab says "could not be reached", but <http://localhost:8080/api/health> works on the laptop.**
Android 17 (API 37) blocks an app's connections to the local network (your laptop, `10.0.2.2`, `192.168.x.x`) until
the user allows it, and the requests just time out. Debug builds ask once, on the first start: tap **Allow**. Tapped
"Don't allow"? Open **Settings → Apps → OnBoard → Permissions → Nearby devices → Allow**, or run
`adb shell pm grant com.safarsaathi.app android.permission.ACCESS_LOCAL_NETWORK`.

**Backend stops at startup with "Connection to localhost:5432 refused".**
The database isn't running, and from Phase 1 the backend needs it. Start Docker Desktop, run `docker compose up -d` in
the repo root, wait until `docker compose ps` shows `(healthy)`, then start the backend again.

**`docker compose` says "failed to connect to the docker API" or "Is the docker daemon running?".**
Docker Desktop isn't running. Start it, wait for "Engine running", and try again. The database runs inside Docker, so
Docker Desktop must be running whenever you use the backend.

**Starting the database fails on port 5432 ("port is already allocated", "address already in use" or "Ports are not available").**
Another PostgreSQL already uses port 5432. Either one is installed on your laptop (Windows: Services app →
`postgresql-x64-<version>` → Stop), or another Docker container has it (`docker ps` lists it; stop it with
`docker stop <name>`). Then run `docker compose up -d` again.

**Backend stops with `password authentication failed for user "safarsaathi"` (or `database "safarsaathi" does not exist`).**
Your local database was created earlier with other settings: for example from leftover `POSTGRES_*` variables before
Phase 1, or before you changed a `DB_*` value. Postgres reads the name, user and password only when it first creates
the database. Start over with `docker compose down -v` (**deletes the local data**) and `docker compose up -d`, then
start the backend. It creates the tables and the demo trips again.

**Which database settings count?**
Only the `DB_*` names: `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD` (read by docker compose and the backend) and `DB_URL`
(backend only). Each comes from your terminal / Windows environment variables first, then `.env`, then the default.
Leftover `POSTGRES_*` variables from other projects no longer matter. Leftover `DB_*` or `SPRING_DATASOURCE_*` ones
would win over `.env`, so remove them. To list them (names only, never values), run
`env | grep -oE '^(DB|SPRING_DATASOURCE)_[A-Z_]*'` (macOS / Linux / Git Bash) or
`(Get-ChildItem Env:DB_*, Env:SPRING_DATASOURCE_*).Name` (PowerShell). `docker compose config` shows the values
compose will really use, **including the password**, so never paste or screenshot its output.

**Backend stops with a Flyway error: "Validate failed" and "checksum mismatch for migration version 1".**
Someone edited a migration file (`backend/src/main/resources/db/migration/V1__....sql`) after it had already run on
your database. Flyway, the tool that builds our tables from those files, remembers every file it ran and refuses to
start when one has changed. So **never edit a migration that has already run**: put the old file back
(`git restore <file>`) and write the change as a new file with the next number, for example
`V2__add_gate_to_blocks.sql`. Only experimenting on your own laptop? `docker compose down -v` (**deletes the local
data**) lets your edited file run again from scratch.

**The demo trip is in the past (or you want fresh demo data).**
The demo trips are dated from the moment they were loaded (the trip day is the next day). So after a day they lie in
the past, and the cab's free-change window may have closed, which changes what the delay demo shows. With the backend
running, press **Reset demo** on <http://localhost:8080/demo> (or run the "Reload the demo trips" command from
[Everyday commands](#everyday-commands)). It deletes all trips and loads the demo trips again, dated tomorrow. The app
notices the reloaded trip (its `createdAt` changed) and replaces its saved copy the next time it fetches the trip.
Backend won't start at all? `docker compose down -v` (**deletes all local data**) and `docker compose up -d` give you
an empty database, and the next backend start loads the demo trips into it.

**I pressed "Delay +90 min", but the phone doesn't change.**
First look at "What just happened" on the `/demo` page: it shows the answer to each button press. An error there
means the backend refused the request; for example "Error 409" after **Cancel booking**, because a cancelled flight
can't be delayed. "Error 404" on every button means the backend runs with `APP_MODE=prod`, which switches the demo
buttons off: start it with the default `APP_MODE=demo`. Press **Reset demo** and try again. If the press worked, check the phone: the Trip tab must be open (it
asks the backend every 10 seconds, only while it's on screen), and the line under the trip title must not say
"Offline" or "Could not update". If it does, the app can't reach the backend: see "The app can't reach the backend"
above. To ask at once instead of waiting, pull the timeline down.

**The first build takes ages, or the laptop gets slow.**
The first Gradle and Maven builds download a few hundred MB, which is normal. Gradle also keeps a background process
alive to speed up the next build. `./gradlew --stop` frees that memory.

---

## Learn more

- [CLAUDE.md](CLAUDE.md): the full brief (rules, stack, data model, engines, phases, definition of done).
- [backend/README.md](backend/README.md): backend settings for local vs hosted, the reset before every demo, notes
  for testers.
- [docs/architecture.md](docs/architecture.md): how the pieces fit, with diagrams.
- [docs/api.md](docs/api.md): every backend endpoint and its status.
- [docs/demo-script.md](docs/demo-script.md): tonight's 3-minute demo script, plus the draft of the full demo.
- [mock-data/README.md](mock-data/README.md): the seed files (the demo trips now, more later).

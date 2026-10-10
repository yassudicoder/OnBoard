# OnBoard

**A travel companion from your door to your destination.** OnBoard is an Android app with a Spring Boot backend
that guides a traveller through the whole journey (plan, book, get to the airport, find the gate, handle delays,
arrive) in their own language, and keeps working when the internet doesn't.

**The big idea:** a trip is a row of dominoes. When one block moves (a late flight), the app finds every booking that
falls over (cab, meeting, hotel, dinner), suggests 2–3 fixes, and applies the one you pick in a single tap.

Built for **Elevate 1.0 (DJSCE), Problem Statement 01 — "Seamless Aviation Journey Experience"**.
The full project brief (rules, stack, phases) is in [CLAUDE.md](CLAUDE.md). Details are in [docs/](docs/).

---

## Where we are now (10 Oct 2026, evening)

**Team:** Yash: backend · Divyesh: Android app · Anushree: integration testing.

**Name:** the project is now called **OnBoard** (it was Safar Saathi). Code names stay as they were on purpose, so
nothing breaks: the `com.safarsaathi.*` Java packages, the app id `com.safarsaathi.app` and the `safarsaathi` database.
The Android app still shows "Safar Saathi" on screen until the Android side renames it.

**Working end to end** (checked live on the emulator, 10 Oct, 22:15): the app's **Trip** tab loads demo trip 1 from
the backend → a delay pressed on `/demo` reaches the phone within about 10 seconds (cards turn amber or red, plus a red
banner for the meeting) → in airplane mode the app shows the saved plan marked "Offline" → **Reset demo** turns
everything green again.

| Branch | What's on it | State |
|---|---|---|
| `main` | Phases 0–2 as of the first commit | stable |
| `feature/backend-hardening` | backend hardening (below) + this README update | pushed; pull request to `main` not opened yet |

**Backend hardening, in short** (details: [backend/README.md](backend/README.md)):

- The backend now listens on **this computer only** (`127.0.0.1`) by default, so nobody else on the Wi-Fi can press
  our demo buttons. The emulator still reaches it at `http://10.0.2.2:8080`.
- The demo buttons (`POST /api/demo/...`) exist only with `APP_MODE=demo` (the default). With `APP_MODE=prod` they
  answer 404. A hosted server uses `SERVER_ADDRESS=0.0.0.0` **and** `APP_MODE=prod`.
- Two delays at the same moment can no longer overwrite each other (the trip is locked while a change is saved),
  and a change that fails to save is retried instead of lost.
- A delay that breaks a flight connection is recorded as a `MISSED` event (demo trip 2: Pune → Delhi → Leh).
- No database change: an existing database keeps its data. Tests: 119 backend tests, 34 Android unit tests and
  10 emulator tests pass.

**Known gaps** (not built yet):

- The app shows trip 1 only. Trip 2's missed connection shows on `/demo` and in `GET /api/trips/2`, not in the app,
  and `MISSED` events have no API yet.
- A delayed flight's own card still says "On track"; only the bookings after it change colour.
- Every delay press adds to the last one (two presses of +90 = +180 minutes). A shorter delay doesn't move bookings
  back earlier. A cancelled first flight doesn't record a `MISSED` event for the connecting flight.
- **Home** ("Do this now") is a placeholder until Phase 3. A Home mock-up with fixed sample text exists on Yash's
  laptop but is not committed: it needs real trip data first, otherwise it contradicts the Trip tab during the demo
  (it also makes `MainActivityTest` fail).

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
   database tables and loads the two demo trips. It's ready when the log shows `OnBoard backend is ready` and a
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

## Demo tonight (Phases 1–2)

The story: demo trip 1 on the phone, a 90-minute flight delay fired from the laptop, and the phone showing which
bookings moved by themselves and which one needs the traveller's decision. It keeps working offline too.
[docs/demo-script.md](docs/demo-script.md) has the 3-minute script: who clicks what, and what to say.

1. **Database:** Docker Desktop running, then `docker compose up -d` in the repo root. Wait until `docker compose ps`
   shows `(healthy)`. The backend can't start without it.
2. **Backend:** `cd backend`, then `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`), and wait for
   `OnBoard backend is ready`. On an empty database it first loads the two demo trips from
   [mock-data/demo-trips.json](mock-data/demo-trips.json). Check <http://localhost:8080/api/trips/1>: trip 1, "Client
   visit: Mumbai to Bengaluru", with 6 blocks, dated tomorrow.
3. **App:** run it on the emulator. On Android 17 (API 37) it first asks "Allow Safar Saathi to find, connect to, and
   determine the relative position of nearby devices?": tap **Allow**, or it can't reach the backend on your laptop
   (see [Troubleshooting](#troubleshooting)). Open the **Trip** tab: flight 08:00 → bags → cab 10:05 → client meeting
   12:00 (the star marks the anchor, the one block the traveller must not miss) → hotel 14:00 → dinner 20:00. All green.
4. **Demo page:** on the laptop, open <http://localhost:8080/demo>. It shows trip 1 as a live timeline, and its
   "Booking to disrupt" list starts on the flight.
5. **Delay:** press **Delay +90 min**. Within about 10 seconds (the Trip tab asks the backend every 10 seconds; pull
   the timeline down to ask at once) the phone shows the flight at 09:30, bag claim and the cab moved by themselves
   (amber "Auto-adjusted": the cab change is still free), the client meeting in red ("Needs you", 50 minutes short)
   and a red banner: "1 thing needs your decision: Client meeting in Whitefield". Hotel and dinner stay green ("On
   track") because they have time to spare. Every press adds another 90 minutes.
6. **Reset:** press **Reset demo**. The demo trips are loaded again (all green, dated tomorrow), and the phone shows
   the original plan within about 10 seconds.
7. **Offline:** stop the backend (Ctrl+C) or turn on airplane mode in the emulator. The Trip tab keeps showing the
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
├── backend/             Spring Boot 3 backend (Java 17, Maven): REST API, ripple engine, /demo page, later more
├── docs/                architecture.md, api.md, demo-script.md
├── mock-data/           demo seed data (JSON): demo-trips.json since Phase 1, more files in later phases
├── docker-compose.yml   local PostgreSQL 17 (the backend needs it from Phase 1)
├── .env.example         backend setting NAMES. Copy it to .env (git-ignored) for your own keys.
├── CLAUDE.md            the full project brief and source of truth
└── README.md            this file
```

---

## Progress

| Phase | What it delivers | Status |
|---|---|---|
| 0 | Setup: repo, backend skeleton + `/api/health`, Android skeleton with 5 tabs, Docker Postgres, docs | **Done** (10 Oct 2026) |
| 1 | Trip data end to end: database tables, demo trip seed, `GET /api/trips/{id}`, timeline screen, offline copy | **In progress** |
| 2 | Ripple engine: a delay or cancellation finds what breaks; demo triggers and the `/demo` page; coloured timeline | **In progress** |
| 3 | "Do this now" card, push notifications, background refresh | Planned |
| 4 | Mock partners (air, hotel, cab, restaurant), booking orchestrator with rollback, front-desk dashboard | Planned |
| 5 | Plan B: 2–3 scored fixes, Fix-it screen, one-tap apply, WhatsApp/email drafts | Planned |
| 6 | Five languages, Gemini assistant with voice, persona-based screens | Planned |
| 7 | Wallet (boarding pass scan), terminal map, true-cost compare, directions per leg | Planned |
| 8 | Polish, airplane-mode test, 3-minute demo script, deploy notes | Planned |

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
"Don't allow"? Open **Settings → Apps → Safar Saathi → Permissions → Nearby devices → Allow**, or run
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

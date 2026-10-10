# OnBoard backend: operations notes

Spring Boot 3.5 + PostgreSQL (Flyway). Owner: backend. The project-wide setup is in the root `README.md`;
this file covers how the backend should be configured and how to run the demo reliably.

## Who can reach the backend

| Setting | Default | Meaning |
|---|---|---|
| `SERVER_ADDRESS` | `127.0.0.1` | Listen on this computer only. The Android emulator still reaches it at `http://10.0.2.2:8080` (10.0.2.2 *is* this computer's 127.0.0.1); a phone on USB reaches it after `adb reverse tcp:8080 tcp:8080`. Other devices on the Wi-Fi cannot. |
| `APP_MODE` | `demo` | `demo`: the demo controls exist. `prod`: they do not (every `POST /api/demo/...` answers 404); health and trip APIs work as usual. |
| `PORT` | `8080` | Port to listen on. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local Docker DB | Where PostgreSQL is. |

**Local development / demo laptop:** keep the defaults (`127.0.0.1`, `demo`).

**Hosted (Render, Railway, Docker):** the platform must reach the app, so set `SERVER_ADDRESS=0.0.0.0` **and**
`APP_MODE=prod`. Binding to 127.0.0.1 is not a security boundary on its own: any address other devices can reach
must run in prod mode. The startup log prints the address, whether the demo controls are on, and a WARNING for
demo mode on a public address.

**Phone over Wi-Fi (fallback only):** needs `SERVER_ADDRESS=0.0.0.0`, which exposes the demo controls to everyone on
that network. Prefer USB + `adb reverse`.

## Before every demo: reset

The demo trips are stored with real dates. When they are seeded (or reset), the trip day is **today in India + 1**
(`safarsaathi.seed.day-offset`), so the plan is "tomorrow". The ripple rules depend on the current time:

| Block (trip 1) | Free to move automatically until | After that |
|---|---|---|
| Bag claim | its start (09:45) | needs the traveller |
| Cab pickup 10:05 | 30 min before pickup (09:35 on the trip day) | needs the traveller (₹200 fee) |
| Hotel check-in 14:00 | check-in window rule: fine until 23:00 arrival | late-arrival note, then needs the traveller after midnight |
| Dinner 20:00 | 18:00 on the trip day | needs the traveller |
| Client meeting (anchor) | never automatic | always needs the traveller |

So a demo run on the trip day itself (e.g. the morning after a reset the night before) behaves differently once
those times pass: after 09:35 the cab turns red instead of amber. **Reset right before presenting:**

1. `POST /api/demo/reset` (or "Reset demo" on `http://localhost:8080/demo`).
2. Check: `GET /api/trips/1` shows `"version":1`, every block `"status":"OK"`, and the flight on tomorrow's date at 08:00.
3. The app re-syncs by itself (the reset gives the trip a new `createdAt`).
4. Story check: delay the first flight by 90 min. Expected: bag claim and cab `AUTO_ADJUSTED` (cab 11:35), meeting
   `NEEDS_USER` (50 min late), hotel and dinner `OK`.

Reset deletes and reloads **all** trips. Only press it on a database you own (never on a teammate's shared demo
without asking).

## What a disruption does (for testers)

- `DisruptionService.apply` locks the trip row (`SELECT ... FOR UPDATE`) and saves everything in one transaction:
  new times and statuses, a `DELAY`/`CANCEL` event, `MISSED` events, version + 1. Competing writers (even two backend
  instances) are applied one after the other.
- A delay is relative ("+90 min" on top of the block's current times). Pressing a delay button twice delays twice.
- A missed connection = the ripple engine reports a broken link into a FLIGHT or TRAIN block. It is recorded once per
  block as a `MISSED` event.
- A flight-status change is forgotten only after it is saved. If saving fails, the 30-second watcher retries it; a
  failed demo button press is taken back (no hidden delay later).

## Tests

- `./mvnw test` uses an in-memory H2 database in PostgreSQL mode (no Docker needed).
- Real PostgreSQL was checked separately on a throwaway container (two backend instances, concurrent delays,
  MISSED events, reset, prod mode). To repeat it without touching the shared demo database, start a separate
  `postgres:17.11-alpine` container on another port and point `DB_URL` at it.
- Do not run `./mvnw clean` or `package` in `backend/` while a demo backend is running from `target/*.jar`:
  Windows locks the jar.

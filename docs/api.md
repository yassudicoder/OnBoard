# Safar Saathi: Backend API

**Base URL:** `http://localhost:8080` from your laptop, `http://10.0.2.2:8080` from the Android emulator (the emulator's
name for your laptop). The port changes if you set `PORT`. Requests and responses are JSON unless noted.

> **Status (Phases 1–2 in progress):** `GET /api/trips/{id}` returns the demo trips (Phase 1). The demo controls
> `POST /api/demo/trigger-delay`, `trigger-cancel` and `reset` and the demo page at `GET /demo` run the ripple engine
> (Phase 2, built for tonight's demo). From Phase 0: `GET /api/health`, `GET /actuator/health`, `GET /actuator/info`
> (still empty) and a placeholder page at `GET /desk`. Everything else is the plan from [CLAUDE.md](../CLAUDE.md) §6.
> Each phase replaces its TODO stubs below with real request/response examples.

---

## All endpoints

| Method | Path | Purpose | Phase | Status |
|---|---|---|---|---|
| GET | `/api/health` | App status + MOCK/REAL mode of every provider | 0 | **Live** |
| GET | `/actuator/health` | Standard Spring Boot health check (for hosting platforms) | 0 | **Live** |
| GET | `/actuator/info` | Standard Spring Boot info endpoint | 0 | Live (returns `{}` for now) |
| GET | `/desk` | Front-desk dashboard (HTML page, not JSON) | 0 → 4 | Placeholder (real dashboard in Phase 4) |
| GET | `/api/trips/{id}` | Full trip: blocks, links, statuses, version | 1 | **Live** |
| GET | `/api/airports/{code}/{terminal}/map` | Terminal walking map for Gate Navigation (demo schematic) | G1 | **Live** |
| POST | `/api/trips` | Create a trip (purpose, persona, language) | later* | Planned |
| POST | `/api/trips/{id}/blocks` | Add a block (flight, cab, meeting, ...) | later* | Planned |
| PUT | `/api/blocks/{id}` | Change a block | later* | Planned |
| DELETE | `/api/blocks/{id}` | Delete a block | later* | Planned |
| POST | `/api/trips/{id}/links` | Add a link ("B starts at least N min after A") | later* | Planned |
| POST | `/api/demo/trigger-delay` | Demo: make a block (the flight) late and run the ripple engine | 2 | **Live** |
| POST | `/api/demo/trigger-cancel` | Demo: cancel a block (the flight) and run the ripple engine | 2 | **Live** |
| POST | `/api/demo/reset` | Demo: delete all trips and reload the demo trips | 2 | **Live** |
| GET | `/demo` | Demo page: live trip timeline + buttons for the three demo calls (HTML page, not JSON) | 2 | **Live** |
| GET | `/api/trips/{id}/now` | "Do this now" card for the current time and place | 3 | Planned |
| POST | `/api/devices` | Register the phone's FCM push token | 3 | Planned |
| GET | `/api/trips/{id}/planb` | Precomputed Plan B, for offline storage | 5 | Planned |
| GET | `/api/trips/{id}/options` | Current fix options after a disruption | 5 | Planned |
| POST | `/api/trips/{id}/options/{optId}/apply` | Apply one option (runs the booking orchestrator) | 5 | Planned |
| POST | `/api/assistant/chat` | Chat with the AI assistant (Gemini, function calling) | 6 | Planned |
| GET | `/api/trips/{id}/directions?leg=N` | Directions for one leg (Routes API or mock) | 7 | Planned |
| POST | `/api/cost/compare` | True-cost comparison of flight options | 7 | Planned |

\* CLAUDE.md doesn't pin these to a phase, and Phase 1 didn't need them: the demo trips come from the seed file. We
pick a phase when a screen needs them.

Mock partner servers (CLAUDE.md §5) live in the same backend and arrive in **Phase 4**: `/mock/air`, `/mock/stay`,
`/mock/cab`, `/mock/table`, each with a `/mock/{partner}/fail-next` switch that makes the next call fail, so we can demo
the rollback. Their exact paths are documented in Phase 4.

---

## Live in Phase 0

### GET /api/health

Our own health check. It shows whether the backend is up and which outside services run on **mock** data and which
are **real**. It's the quickest way to check your setup.

Try it: open <http://localhost:8080/api/health> in a browser, or run `curl http://localhost:8080/api/health`
(Windows PowerShell: `curl.exe http://localhost:8080/api/health`).

Example response, with no API keys set:

```json
{
  "status": "UP",
  "app": "safar-saathi-backend",
  "mode": "demo",
  "time": "2026-10-10T14:05:30+05:30",
  "zone": "Asia/Kolkata",
  "providers": {
    "flightStatus": "MOCK",
    "ai": "MOCK",
    "directions": "MOCK",
    "push": "MOCK",
    "calendar": "MOCK",
    "voice": "MOCK",
    "weather": "REAL"
  }
}
```

The real response comes on one line. It's shown pretty-printed here.

| Field | Example | What it means |
|---|---|---|
| `status` | `UP` | The backend is running and answering. |
| `app` | `safar-saathi-backend` | Which app answered (`spring.application.name`). Handy when several servers run on your laptop. |
| `mode` | `demo` | `APP_MODE`: `demo` (flight watcher every 30 s, for live demos) or `prod` (every 5 min). Default `demo`. |
| `time` | `2026-10-10T14:05:30+05:30` | The backend's "now" in Indian time, to the whole second, in ISO-8601 format with the `+05:30` offset. It comes from the same clock the engines use. |
| `zone` | `Asia/Kolkata` | The time zone all trip times are stored and shown in. |
| `providers` | *(object)* | One entry per outside service: `MOCK` (built-in fake data, no key needed) or `REAL` (key or credentials file found). |

What each provider entry means:

| `providers` key | Service | Becomes `REAL` when… | When `MOCK` | Built in |
|---|---|---|---|---|
| `flightStatus` | AeroDataBox (RapidAPI) | `AERODATABOX_API_KEY` is set | Flight status we control with `/api/demo/*` | Phase 2 |
| `ai` | Google Gemini | `GEMINI_API_KEY` is set | Ready-made template text | Phase 6 |
| `directions` | Google Routes API | `GOOGLE_ROUTES_API_KEY` is set | Travel time estimated from distance | Phase 7 |
| `push` | Firebase Cloud Messaging | `FIREBASE_CREDENTIALS_PATH` points to a file that exists | No real push is sent | Phase 3 |
| `calendar` | Google Calendar | `GOOGLE_CALENDAR_CREDENTIALS_PATH` points to a file that exists | Mock calendar inside the backend | Phase 4 |
| `voice` | Sarvam AI | `SARVAM_API_KEY` is set | The app uses Android's built-in voice | Stretch |
| `weather` | Open-Meteo | Always (no key needed) | (never mock) | When a screen needs it |

Good to know:

- So far, `REAL` only means "key or file found". The code that actually calls each service arrives in the phase
  listed above.
- Key values are **never** shown, not here and not in the logs. The same MOCK/REAL list is printed in the backend's
  startup log.
- Keys come from environment variables or the git-ignored root `.env` file. See [.env.example](../.env.example).

### GET /actuator/health

The standard health check from **Spring Boot Actuator**, a built-in Spring module. Hosting platforms such as Render or
Railway (Phase 8) call it to see whether the app is alive. With default settings it answers:

```json
{"status":"UP"}
```

Use `/api/health` when *you* want details. `/actuator/health` is for machines. Actuator exposes only `health` and
`info` (nothing sensitive), and `/actuator/info` is still empty (`{}`).

### GET /desk

Returns an HTML page, not JSON (`/desk`, `/desk/` and `/desk/index.html` all show it). In Phase 0 it is a placeholder
that says the dashboard arrives in Phase 4 and links to `/api/health`. In **Phase 4** it becomes the front-desk
dashboard: plain HTML + JavaScript that polls the backend every 2 seconds and shows the MockStay / MockCab / MockAir /
MockTable bookings and a change log, live, as the second screen of the demo.

---

## Live in Phase 1

### GET /api/trips/{id}

The whole trip in one answer: the trip itself, its **blocks** (flight, bag claim, cab, meeting, hotel, dinner, ...) and
the **links** between them. The app saves this answer in Room (its database on the phone), so the Trip tab also works
offline.

Try it: open <http://localhost:8080/api/trips/1> in a browser, or run:

```bash
# macOS / Linux / Git Bash. -i also prints the status line and the headers.
curl -i http://localhost:8080/api/trips/1
```

```powershell
# Windows PowerShell: type curl.exe, because plain "curl" can be PowerShell's own, different command.
curl.exe -i http://localhost:8080/api/trips/1
```

**Which ids exist?** When the backend starts with an empty database, its seed loader adds the two demo trips from
[mock-data/demo-trips.json](../mock-data/demo-trips.json), in file order:

| Trip id | Title | Block ids | Link ids |
|---|---|---|---|
| 1 | Client visit: Mumbai to Bengaluru (business, English) | 1–6 | 1–5 |
| 2 | Leh holiday via Delhi (holiday, elderly first-time flyer, Marathi) | 7–11 | 6–9 |

These ids hold for a freshly created database (`docker compose down -v` gives you one; it deletes the local data).

Example: `GET /api/trips/1` from a database seeded on 10 October 2026, so the trip day is 11 October:

```json
{
  "id": 1,
  "title": "Client visit: Mumbai to Bengaluru",
  "purpose": "BUSINESS",
  "persona": "BUSINESS",
  "language": "en",
  "travellers": 1,
  "status": "PLANNED",
  "version": 1,
  "createdAt": "2026-10-10T17:30:05+05:30",
  "updatedAt": "2026-10-10T17:30:05+05:30",
  "blocks": [
    {
      "id": 1,
      "tripId": 1,
      "type": "FLIGHT",
      "title": "Flight IJ 512 Mumbai to Bengaluru",
      "startTime": "2026-10-11T08:00:00+05:30",
      "endTime": "2026-10-11T09:45:00+05:30",
      "locationName": "Mumbai Airport T2 (BOM)",
      "lat": 19.0896,
      "lng": 72.8656,
      "provider": "MOCK_AIR",
      "providerRef": "K7Q2ZM",
      "flex": "FIXED",
      "anchor": false,
      "status": "OK",
      "freeChangeUntilMinutes": null,
      "changeFeeInr": 2500,
      "notes": "Arrives at Bengaluru T1 (BLR). Change fee 2,500 rupees, waived when the airline causes the disruption."
    },
    {
      "id": 2,
      "tripId": 1,
      "type": "BAG_CLAIM",
      "title": "Collect bags at Bengaluru T1",
      "startTime": "2026-10-11T09:45:00+05:30",
      "endTime": "2026-10-11T10:05:00+05:30",
      "locationName": "Bengaluru Airport T1 (BLR), baggage belt",
      "lat": 13.1989,
      "lng": 77.7068,
      "provider": "NONE",
      "providerRef": null,
      "flex": "AUTO_SHIFT",
      "anchor": false,
      "status": "OK",
      "freeChangeUntilMinutes": 0,
      "changeFeeInr": 0,
      "notes": "About 20 minutes from landing to bags in hand."
    },
    {
      "id": 3,
      "tripId": 1,
      "type": "CAB",
      "title": "Cab pickup to the Whitefield office",
      "startTime": "2026-10-11T10:05:00+05:30",
      "endTime": "2026-10-11T10:05:00+05:30",
      "locationName": "Bengaluru Airport T1 pickup zone",
      "lat": 13.1993,
      "lng": 77.7074,
      "provider": "MOCK_CAB",
      "providerRef": "CAB-BLR-4471",
      "flex": "AUTO_SHIFT",
      "anchor": false,
      "status": "OK",
      "freeChangeUntilMinutes": -30,
      "changeFeeInr": 200,
      "notes": "Free pickup change until 30 minutes before pickup, 200 rupees after. Ride to Whitefield takes about 75 minutes."
    },
    {
      "id": 4,
      "tripId": 1,
      "type": "MEETING",
      "title": "Client meeting in Whitefield",
      "startTime": "2026-10-11T12:00:00+05:30",
      "endTime": "2026-10-11T13:00:00+05:30",
      "locationName": "Client office, ITPL Main Road, Whitefield",
      "lat": 12.9857,
      "lng": 77.7365,
      "provider": "GOOGLE_CALENDAR",
      "providerRef": "evt-demo-client-meeting",
      "flex": "ASK_USER",
      "anchor": true,
      "status": "OK",
      "freeChangeUntilMinutes": null,
      "changeFeeInr": 0,
      "notes": "The reason for the trip (anchor). Moving it needs the traveller's OK; a video call is a possible fallback."
    },
    {
      "id": 5,
      "tripId": 1,
      "type": "HOTEL",
      "title": "Hotel check-in: Whitefield Residency",
      "startTime": "2026-10-11T14:00:00+05:30",
      "endTime": "2026-10-11T14:15:00+05:30",
      "locationName": "Whitefield Residency, Whitefield",
      "lat": 12.9698,
      "lng": 77.7499,
      "provider": "MOCK_STAY",
      "providerRef": "STAY-WFR-2231",
      "flex": "AUTO_SHIFT",
      "anchor": false,
      "status": "OK",
      "freeChangeUntilMinutes": -1440,
      "changeFeeInr": 0,
      "notes": "Check-in is a window, a late-arrival note is always allowed. Free cancellation until 24 hours before check-in. From the airport it is about 60 minutes by cab. Check-out tomorrow 11:00."
    },
    {
      "id": 6,
      "tripId": 1,
      "type": "RESTAURANT",
      "title": "Team dinner at Tandoor House",
      "startTime": "2026-10-11T20:00:00+05:30",
      "endTime": "2026-10-11T21:30:00+05:30",
      "locationName": "Tandoor House, Whitefield",
      "lat": 12.9784,
      "lng": 77.7408,
      "provider": "MOCK_TABLE",
      "providerRef": "TBL-TH-0912",
      "flex": "AUTO_SHIFT",
      "anchor": false,
      "status": "OK",
      "freeChangeUntilMinutes": -120,
      "changeFeeInr": 500,
      "notes": "Free change or cancel until 18:00 on the day."
    }
  ],
  "links": [
    {
      "id": 1,
      "tripId": 1,
      "fromBlockId": 1,
      "toBlockId": 2,
      "minGapMinutes": 0,
      "travelMinutes": 0,
      "travelMode": "NONE"
    },
    {
      "id": 2,
      "tripId": 1,
      "fromBlockId": 2,
      "toBlockId": 3,
      "minGapMinutes": 0,
      "travelMinutes": 0,
      "travelMode": "WALK"
    },
    {
      "id": 3,
      "tripId": 1,
      "fromBlockId": 3,
      "toBlockId": 4,
      "minGapMinutes": 0,
      "travelMinutes": 75,
      "travelMode": "CAB"
    },
    {
      "id": 4,
      "tripId": 1,
      "fromBlockId": 4,
      "toBlockId": 5,
      "minGapMinutes": 0,
      "travelMinutes": 40,
      "travelMode": "CAB"
    },
    {
      "id": 5,
      "tripId": 1,
      "fromBlockId": 5,
      "toBlockId": 6,
      "minGapMinutes": 0,
      "travelMinutes": 20,
      "travelMode": "CAB"
    }
  ]
}
```

The real response comes on one line, with `Content-Type: application/json`; it's shown pretty-printed here. Your
dates will differ: they follow the day your database was seeded (see the [mock-data README](../mock-data/README.md)).

#### Trip fields

| Field | Example | What it means |
|---|---|---|
| `id` | `1` | The trip's number in the database. |
| `title` | `Client visit: Mumbai to Bengaluru` | The trip's name, shown above the timeline. |
| `purpose` | `BUSINESS` | Why the person travels: `BUSINESS`, `WEDDING` (wedding or family event), `HOLIDAY`, `PILGRIMAGE`, `MEDICAL` or `EXAM` (exam or interview). Later it sets the scoring weights for Plan B. |
| `persona` | `BUSINESS` | Who travels: `FIRST_TIME`, `FAMILY`, `BUSINESS` or `ELDERLY`. Later it changes hints, text size and tone. |
| `language` | `en` | The trip's language as a short code: `en`, `hi`, `mr`, `ta` or `bn`. |
| `travellers` | `1` | How many people travel. |
| `status` | `PLANNED` | `PLANNED` (not started yet), `ACTIVE` (on the way) or `DONE`. |
| `version` | `1` | Starts at 1. Every applied delay or cancellation adds 1. The app compares it with its saved copy. |
| `createdAt` | `2026-10-10T17:30:05+05:30` | When this trip was created. For the demo trips, that's when they were seeded, so a new value means the database was refilled. |
| `updatedAt` | `2026-10-10T17:30:05+05:30` | When the trip last changed. Equal to `createdAt` until something changes the plan (Phase 2 onwards). |
| `blocks` | *(list)* | The trip's blocks, earliest first. |
| `links` | *(list)* | The links between those blocks. |

#### Block fields

| Field | Example | What it means |
|---|---|---|
| `id` | `1` | The block's number. Unique across all trips. |
| `tripId` | `1` | The trip it belongs to. |
| `type` | `FLIGHT` | `FLIGHT`, `BAG_CLAIM`, `CAB`, `HOTEL`, `MEETING`, `EVENT`, `RESTAURANT`, `TRAIN` or `OTHER`. |
| `title` | `Flight IJ 512 Mumbai to Bengaluru` | What the timeline shows. |
| `startTime`, `endTime` | `2026-10-11T08:00:00+05:30` | When it starts and ends. Both are equal for a single moment, such as a cab pickup. |
| `locationName` | `Mumbai Airport T2 (BOM)` | Where it happens. May be `null`. |
| `lat`, `lng` | `19.0896`, `72.8656` | Position on a map (latitude, longitude). May be `null`. |
| `provider` | `MOCK_AIR` | Who holds the booking: `MOCK_AIR`, `MOCK_STAY`, `MOCK_CAB`, `MOCK_TABLE`, `GOOGLE_CALENDAR`, or `NONE` when nothing is booked (bag claim). |
| `providerRef` | `K7Q2ZM` | The booking reference at that provider (PNR, booking number, calendar event id). `null` when there is none. |
| `flex` | `FIXED` | What may happen when an earlier block runs late: `FIXED` (can't move), `AUTO_SHIFT` (we may move it ourselves) or `ASK_USER` (the traveller decides). |
| `anchor` | `false` | `true` = the reason for the trip, the one block that must not be missed (the client meeting in trip 1). |
| `status` | `OK` | `OK` (fine), `AUTO_ADJUSTED` (we moved it, free), `NEEDS_USER` (the traveller must decide), `DONE` or `CANCELLED`. Every block starts as `OK`; the ripple engine changes it after a delay or cancellation ([demo controls](#live-in-phase-2-demo-controls)). The app colours it: green, amber, red, grey. |
| `freeChangeUntilMinutes` | `-30` | Until when a change is free, in minutes counted from `startTime`: `-30` = until 30 minutes before the start, `0` = until the start. `null` = never free, so `changeFeeInr` always applies. |
| `changeFeeInr` | `200` | The fee in whole rupees for a change outside the free window. `0` = no fee. |
| `notes` | `About 20 minutes from landing to bags in hand.` | Extra information in plain English. May be `null`. |

#### Link fields

| Field | Example | What it means |
|---|---|---|
| `id` | `3` | The link's number. |
| `tripId` | `1` | The trip it belongs to. |
| `fromBlockId`, `toBlockId` | `3`, `4` | The link runs from this block to that one. |
| `minGapMinutes` | `0` | The minimum wait between the two, for example 75 for the flight connection in Delhi (trip 2). |
| `travelMinutes` | `75` | How long it takes to get from the first place to the second. |
| `travelMode` | `CAB` | How: `WALK`, `CAB`, `METRO`, or `NONE` (same place, e.g. flight → bag claim). |

A link means: the "to" block can start at the earliest at the "from" block's `endTime` + `minGapMinutes` +
`travelMinutes`. Example from trip 1: the cab picks up at 10:05 and the ride takes 75 minutes, so the meeting could
start from 11:20. It starts at 12:00, which leaves 40 minutes to spare. A delay bigger than the spare time is what the
ripple engine catches: see [trigger-delay](#post-apidemotrigger-delay).

#### What the app can rely on

- **Times** are ISO-8601 text with the `+05:30` offset (Asia/Kolkata) and whole seconds, e.g.
  `2026-10-11T08:00:00+05:30`. Java's `OffsetDateTime.parse(...)` reads them as they are.
- **Order:** `blocks` are sorted by `startTime`, and blocks that start at the same moment by `id`. `links` are sorted by
  `id`.
- **Nulls:** a field without a value is sent as `null`, never left out, so every field name is always there.
- **Ids** are the server's ids. The app uses them as the primary keys of its own Room tables.
- **When the app replaces its saved copy:** it replaces the trip, its blocks and its links in one go (one Room
  transaction) when it has no copy yet, when the server's `version` is higher, or when `createdAt` differs (the
  database was refilled, so `version` started again at 1). Otherwise it keeps its copy and only notes when it last
  heard from the server.

#### Errors

Errors come as **Problem Details**, a standard JSON shape for errors (RFC 9457), with
`Content-Type: application/problem+json`. `detail` is the plain-English reason. A response never contains a Java
stack trace; that only goes to the backend log.

**Unknown id → `404 Not Found`.** For example `curl -i http://localhost:8080/api/trips/99`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Trip 99 not found",
  "instance": "/api/trips/99"
}
```

**The id isn't a number → `400 Bad Request`.** For example `curl -i http://localhost:8080/api/trips/abc`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to convert 'id' with value: 'abc'",
  "instance": "/api/trips/abc"
}
```

`"type": "about:blank"` means there is no extra help page for this error, and `instance` is the path that failed.

---

## Live in Phase 2: demo controls

Built for tonight's demo. These endpoints play the airline. They tell the mock flight-status provider (our pretend
airline) "block 1 is 90 minutes late" or "block 1 is cancelled", and then make the **flight watcher** check that block
at once, instead of waiting up to 30 seconds for its timer. From there it is the same path a real delay takes: the
watcher hands the change to the **ripple engine**, which walks the trip's links like a row of dominoes and gives every
block further down the chain a status. The backend saves the new times and statuses, notes the disruption (table
`disruption_events`), adds 1 to the trip's `version` and sets `updatedAt`, all in one transaction. At its next
refresh, the app sees the higher `version`, replaces its saved copy and recolours the timeline.

The easiest way to call them is the [demo page](#get-demo). The ids come from the seed (see
[Which ids exist?](#get-apitripsid)): trip 1 has blocks 1–6, and block 1 is its flight.

### POST /api/demo/trigger-delay

Makes one block of a trip late (normally the flight) and runs the ripple engine.

Request body (`Content-Type: application/json`):

```json
{"tripId": 1, "blockId": 1, "minutes": 90}
```

| Field | Example | What it means |
|---|---|---|
| `tripId` | `1` | The trip. |
| `blockId` | `1` | The block that is late. It must belong to that trip. Block 1 is trip 1's flight. |
| `minutes` | `90` | How many minutes later than its **current** times: from 1 to 1440 (one day). Start and end both move. Delaying the same block by 90 twice makes it 180 minutes late. |

Try it:

```bash
# macOS / Linux / Git Bash
curl -i -X POST http://localhost:8080/api/demo/trigger-delay \
  -H "Content-Type: application/json" \
  -d '{"tripId":1,"blockId":1,"minutes":90}'
```

```powershell
# Windows PowerShell. Invoke-RestMethod sends the JSON exactly as written (curl.exe would need extra escaping of the
# quotes in some PowerShell versions). ConvertTo-Json prints the answer as JSON again.
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/demo/trigger-delay -ContentType application/json -Body '{"tripId":1,"blockId":1,"minutes":90}' | ConvertTo-Json -Depth 5
```

The answer (`200 OK`) is what the ripple engine decided. Example: trip 1, flight delayed by 90 minutes, demo trips
seeded on 10 October (so the trip day is 11 October):

```json
{
  "tripId": 1,
  "version": 2,
  "changedBlockId": 1,
  "kind": "DELAY",
  "outcomes": [
    {
      "blockId": 1,
      "title": "Flight IJ 512 Mumbai to Bengaluru",
      "status": "OK",
      "newStart": "2026-10-11T09:30:00+05:30",
      "newEnd": "2026-10-11T11:15:00+05:30",
      "minutesLate": 90,
      "reason": "Now 09:30 to 11:15, 90 minutes later than planned."
    },
    {
      "blockId": 2,
      "title": "Collect bags at Bengaluru T1",
      "status": "AUTO_ADJUSTED",
      "newStart": "2026-10-11T11:15:00+05:30",
      "newEnd": "2026-10-11T11:35:00+05:30",
      "minutesLate": 90,
      "reason": "Moved from 09:45 to 11:15, free of charge, to fit the new plan."
    },
    {
      "blockId": 3,
      "title": "Cab pickup to the Whitefield office",
      "status": "AUTO_ADJUSTED",
      "newStart": "2026-10-11T11:35:00+05:30",
      "newEnd": "2026-10-11T11:35:00+05:30",
      "minutesLate": 90,
      "reason": "Moved from 10:05 to 11:35, free of charge, to fit the new plan."
    },
    {
      "blockId": 4,
      "title": "Client meeting in Whitefield",
      "status": "NEEDS_USER",
      "newStart": null,
      "newEnd": null,
      "minutesLate": 50,
      "reason": "The earliest you can make it is 12:50, 50 minutes late. This is the main reason for your trip, so you decide."
    },
    {
      "blockId": 5,
      "title": "Hotel check-in: Whitefield Residency",
      "status": "OK",
      "newStart": null,
      "newEnd": null,
      "minutesLate": 30,
      "reason": "You now arrive around 14:30. Check-in is open until 23:00, so nothing changes."
    },
    {
      "blockId": 6,
      "title": "Team dinner at Tandoor House",
      "status": "OK",
      "newStart": null,
      "newEnd": null,
      "minutesLate": 0,
      "reason": "Still on time: no change needed."
    }
  ],
  "brokenLinks": [
    {"fromBlockId": 3, "toBlockId": 4, "minutesShort": 50}
  ]
}
```

Shown pretty-printed; the real answer comes on one line.

Block by block, this is the domino effect the demo shows:

| Block | Before | After | Why |
|---|---|---|---|
| 1 Flight | 08:00–09:45 | 09:30–11:15, still `OK` | The delay itself. The changed block keeps its status. |
| 2 Bag claim | 09:45–10:05 | 11:15–11:35, `AUTO_ADJUSTED` | Bags come after landing. It may move by itself (`AUTO_SHIFT`), for free. |
| 3 Cab | 10:05 | 11:35, `AUTO_ADJUSTED` | Pickup changes are free until 30 minutes before pickup, and that is still hours away. |
| 4 Meeting | 12:00–13:00 | unchanged, `NEEDS_USER`, 50 min late | Cab at 11:35 + 75 min ride = 12:50. The meeting is the anchor and set to ask the user, so we never move it ourselves. |
| 5 Hotel | 14:00 | unchanged, `OK`, 30 min later | You'd get there around 14:30 (meeting until 13:50 if it ran late, + 40 min ride), but check-in is a window: any time up to 23:00 is fine. |
| 6 Dinner | 20:00 | unchanged, `OK` | Plenty of time after the hotel. |

Response fields:

| Field | Example | What it means |
|---|---|---|
| `tripId` | `1` | The trip. |
| `version` | `2` | The trip's new `version`, one higher than before. |
| `changedBlockId` | `1` | The block you delayed (or cancelled). |
| `kind` | `DELAY` | `DELAY` (this endpoint) or `CANCEL` (trigger-cancel). |
| `outcomes` | *(list)* | The changed block first, then every block that waits for it (directly or through other blocks), in trip order. Blocks that don't depend on the change are not listed: they keep their status and times. |
| `outcomes[].blockId`, `title` | `3`, `Cab pickup to ...` | Which block. |
| `outcomes[].status` | `AUTO_ADJUSTED` | The block's new status: `OK`, `AUTO_ADJUSTED` or `NEEDS_USER`. The changed block keeps the status it had (`CANCELLED` after trigger-cancel). |
| `outcomes[].newStart`, `newEnd` | `2026-10-11T11:35:00+05:30` | The new times: for the changed block, and for blocks the engine moved (`AUTO_ADJUSTED`). `null` = the times stay as they are. |
| `outcomes[].minutesLate` | `50` | How many minutes after its planned start the block can begin now (`0` = on time). For the changed block: how many minutes later it now starts. A block can be late and still `OK`, like the hotel above. |
| `outcomes[].reason` | `The earliest you can make it is 12:50...` | Why, in plain English, ready to show to the traveller. |
| `brokenLinks` | *(list)* | Links into a `NEEDS_USER` block whose timing no longer works, in trip order. |
| `brokenLinks[].fromBlockId`, `toBlockId` | `3`, `4` | The link, from block to block. |
| `brokenLinks[].minutesShort` | `50` | How many minutes are missing. |

How the engine decides, for each block down the chain ([CLAUDE.md](../CLAUDE.md) §4.1):

1. **Earliest possible start** = the latest of (from-block's end + `minGapMinutes` + `travelMinutes`) over all links
   that lead into the block. A from-block that runs late counts with its later end: its new end if it moved, or the
   end it *would* have (end + `minutesLate`) if it `NEEDS_USER`, so the knock-on effect still shows further down.
2. The block waits for a **cancelled** block, directly or through other blocks → `NEEDS_USER` ("Depends on a
   cancelled booking earlier in the trip, so you decide.").
3. The block still starts at or after its earliest possible start → `OK`, nothing changes.
4. **Hotel:** check-in is a window. Arriving by 23:00 on the check-in day → `OK`. Between 23:00 and midnight →
   `AUTO_ADJUSTED` with a late-arrival note (check-in moved to the arrival time). From midnight → `NEEDS_USER`
   (the hotel may treat it as a no-show).
5. `AUTO_SHIFT`, not the anchor, and still inside its free-change window (now ≤ start + `freeChangeUntilMinutes`)
   → `AUTO_ADJUSTED`: the start moves to the earliest possible start, and the end moves by the same amount.
6. Otherwise (`FIXED`, `ASK_USER`, the anchor, no free window at all, or the window has passed) → `NEEDS_USER`
   with `minutesLate`. The times stay as they are. The traveller decides; fix options come in Phase 5.

Blocks earlier in the chain, blocks not linked to the changed block, and blocks that are already `DONE` or
`CANCELLED` keep their status. `minutesLate` and `minutesShort` are whole minutes, rounded up (30 seconds late
counts as 1 minute). The engine is plain Java with unit tests
(`backend/src/main/java/com/safarsaathi/backend/ripple/RippleEngine.java`), and the same input always gives the
same answer.

### POST /api/demo/trigger-cancel

Cancels one block of a trip (normally the flight): its status becomes `CANCELLED`. Then it runs the ripple engine,
which marks every block that waits for the cancelled one, directly or through other blocks, as `NEEDS_USER`
("Depends on a cancelled booking earlier in the trip, so you decide."). Their times stay as they are.

Request body: `{"tripId": 1, "blockId": 1}`. Same fields as trigger-delay, without `minutes`.

```bash
# macOS / Linux / Git Bash
curl -i -X POST http://localhost:8080/api/demo/trigger-cancel \
  -H "Content-Type: application/json" \
  -d '{"tripId":1,"blockId":1}'
```

```powershell
# Windows PowerShell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/demo/trigger-cancel -ContentType application/json -Body '{"tripId":1,"blockId":1}' | ConvertTo-Json -Depth 5
```

The answer (`200 OK`) has the same shape as trigger-delay's, with `"kind": "CANCEL"`. For trip 1's flight, the
outcomes are the flight (`CANCELLED`, reason "Cancelled.") and then blocks 2–6, all `NEEDS_USER` with `minutesLate` 0:
without the flight, the whole rest of the day needs the traveller. `brokenLinks` stays empty, because "minutes short"
means nothing when a booking is gone. Phase 5 adds the fix options (next flight, another airline, next day + hotel).

### POST /api/demo/reset

Puts the demo back to the start. No request body. It:

- deletes all trips (their blocks and links go with them),
- loads the demo trips again from [mock-data/demo-trips.json](../mock-data/README.md), dated afresh (trip day = today
  + `safarsaathi.seed.day-offset`, so tomorrow by default), with `version` 1, every block `OK` and a new `createdAt`,
- clears the mock flight status, so the delays and cancellations triggered so far are forgotten.

```bash
# macOS / Linux / Git Bash
curl -i -X POST http://localhost:8080/api/demo/reset
```

```powershell
# Windows PowerShell
curl.exe -i -X POST http://localhost:8080/api/demo/reset
```

Answer (`200 OK`):

```json
{"trips": 2, "message": "Demo data reloaded"}
```

`trips` is how many trips were loaded. The app notices the new `createdAt`, so it replaces its saved copy even though
`version` is back to 1 (see [What the app can rely on](#what-the-app-can-rely-on)).

### Errors from the demo controls

Problem Details again (`application/problem+json`, a plain-English `detail`, never a stack trace):

| When | Status | `detail`, for example |
|---|---|---|
| The trip doesn't exist | `404 Not Found` | `Trip 99 not found` |
| The block doesn't exist, or belongs to another trip | `404 Not Found` | `Block 7 not found in trip 1` |
| The block is already cancelled or done | `409 Conflict` | `Block 1 is already cancelled, so it cannot be delayed or cancelled` |
| A field is missing | `400 Bad Request` | `tripId, blockId and minutes are all required, e.g. {"tripId": 1, "blockId": 1, "minutes": 90}` |
| `minutes` is 0 or less, or more than 1440 | `400 Bad Request` | `minutes must be more than 0, but it is 0` |
| The body isn't valid JSON | `400 Bad Request` | (Spring's standard text) |

The `409` is the one you're most likely to meet in the demo: once trigger-cancel has cancelled the flight, it can't be
delayed or cancelled again. **Reset demo** fixes that.

For example, `POST /api/demo/trigger-delay` with `{"tripId": 1, "blockId": 7, "minutes": 90}` (block 7 belongs to
trip 2):

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Block 7 not found in trip 1",
  "instance": "/api/demo/trigger-delay"
}
```

In PowerShell, `Invoke-RestMethod` reports a 4xx answer as an error message. That is expected.

### GET /demo

A web page for the laptop (HTML, not JSON) that drives the demo with one click per step, so nobody types curl
commands in front of the judges. Open <http://localhost:8080/demo> (`/demo/` works too).

- **Left: the trip as a live timeline.** It asks `GET /api/trips/{id}` every 2 seconds, the same data the app syncs,
  with the app's status words and colours ("On track", "Auto-adjusted", "Needs you"). A moved block shows its old
  time ("was 08:00"), the anchor has a star, and the `version` badge flashes when it goes up.
- **Right: the controls.**

| Control | What it does |
|---|---|
| **Trip** | Trip 1 (business, the default) or trip 2 (Leh). |
| **Booking to disrupt** | Which block the buttons act on. Starts on the trip's first flight (block 1 for trip 1). |
| **Delay +30 min**, **Delay +90 min**, **Delay +180 min** | `POST /api/demo/trigger-delay` with that many `minutes`. |
| **Cancel it** | `POST /api/demo/trigger-cancel`. |
| **Reset demo** | `POST /api/demo/reset`. No "are you sure?" question. |

- **What just happened** explains the last answer in plain words: every outcome with its status and `reason`, the
  broken links ("Broken link: Cab pickup to the Whitefield office → Client meeting in Whitefield is 50 minutes
  short."), and how many bookings need the traveller. An error shows its status and `detail`; after a `409` it adds a
  tip to press **Reset demo**.

The page is plain HTML, CSS and JavaScript with no outside files (it works without internet), in
`backend/src/main/resources/static/demo/index.html`.

---

## Live in G1 (Gate Navigation): airport map

### GET /api/airports/{code}/{terminal}/map

One terminal's walking map: header, floors, places (nodes), walkways (edges), QR "You are here" anchors, plus
`"areas"`, the hall and pier outlines (GeoJSON). The pilot has **BOM T2 only**. Case does not matter (`bom/t2` works).

```bash
curl -i http://localhost:8080/api/airports/BOM/T2/map
```

The map is a **demo schematic drawn by the team**, so the answer says so, and the app shows the `note` on screen:

```json
{
  "airport": "BOM", "terminal": "T2", "version": 2,
  "source": "Simplified schematic drawn from scratch by the OnBoard team ...",
  "verified": false,
  "note": "Demo schematic, not an official airport map",
  "units": "metres in a local frame: x grows to the east, y grows to the north",
  "floors": [{"id": "DEP", "order": 2, "names": {"en": "Departures", "hi": "प्रस्थान", "mr": "प्रस्थान"}}, ...],
  "nodes": [{"id": "gate_32", "type": "GATE", "floor": "DEP", "x": 30, "y": 180, "zone": "AIRSIDE", "ref": "32",
             "names": {"en": "Gate 32", "hi": "गेट 32", "mr": "गेट 32"}}, ...],
  "edges": [{"from": "security", "to": "duty_free", "distanceM": 25.0, "kind": "WALK", "stepFree": true,
             "oneWay": true, "zone": "AIRSIDE", "passesCheckpoint": true}, ...],
  "anchors": [{"id": "BOM-T2-ENTRY-4", "node": "entry_4"}, ...],
  "areas": {"type": "FeatureCollection", "features": [...]}
}
```

- **Place types:** ENTRY, CHECKIN_ROW, BAG_DROP, SECURITY, IMMIGRATION, PIER_JUNCTION, GATE, LIFT, ESCALATOR, STAIRS,
  TRAVELATOR, WASHROOM, HELP_DESK, BUGGY_STOP, LANDMARK, BAGGAGE_BELT, BAGGAGE_SERVICES, LOST_FOUND.
- **Walkway kinds:** WALK, TRAVELATOR, LIFT, ESCALATOR, STAIRS. `stepFree` is false for STAIRS and ESCALATOR.
  `oneWay` walkways go only from `from` to `to` (security, the travelators, the arrivals exit).
- **Coordinates are metres in a local frame**, not longitude/latitude (also in `areas`).
- **Caching:** `ETag: "BOM-T2-v2"` and `Cache-Control: max-age=300`. Send the ETag back in `If-None-Match` and the
  answer is `304 Not Modified` with no body.
- **Checked at startup:** the backend reads the map from `mock-data/airports/BOM-T2/` and checks it with routing-core's
  `AirportMapValidator` (unique ids, lengths match positions, no way to airside around security, every gate reachable).
  A map with mistakes stops the backend with a list of what is wrong.
- **Errors:** unknown airport or terminal → `404` Problem Details, e.g.
  `"detail": "No map for airport DEL terminal T1 (the pilot has BOM T2 only)"`.

---

## Conventions

- **Times** are ISO-8601 text with the India offset and whole seconds, e.g. `2026-10-11T08:00:00+05:30` (zone
  Asia/Kolkata).
- **Money** is whole rupees as integers, and the field names end in `Inr` (for example `extraCostInr`, `changeFeeInr`).
- **Empty values** are sent as `null`, never left out.
- **`version`**: from Phase 2, every change to a trip increases `trip.version`. The app overwrites its offline copy
  (Room) only when the server's version is newer, or when the trip was seeded again (`createdAt` changed).
- **Errors** from our endpoints are Problem Details (`application/problem+json`) with a plain-English `detail` and no
  stack trace. See [the trip errors](#errors) for examples.

---

## Planned endpoints (TODO stubs)

Each stub says what the endpoint is for. The phase that builds it adds the exact request and response.

### Trip editing: phase not decided yet
- `POST /api/trips`: create a trip. Body has `purpose` (BUSINESS, WEDDING, HOLIDAY, PILGRIMAGE, MEDICAL, EXAM),
  `persona` (FIRST_TIME, FAMILY, BUSINESS, ELDERLY) and `language`. TODO: request/response example.
- `POST /api/trips/{id}/blocks`, `PUT /api/blocks/{id}`, `DELETE /api/blocks/{id}`: edit blocks. TODO.
- `POST /api/trips/{id}/links`: add a link (`fromBlockId`, `toBlockId`, `minGapMinutes`, `travelMinutes`,
  `travelMode`). TODO.

### "Do this now" + push: Phase 3
- `GET /api/trips/{id}/now`: the next unfinished block, the current leg and one suggested action. TODO.
- `POST /api/devices`: registers the phone's FCM token so the backend can push "Plan updated". TODO.

### Plan B and fixes: Phase 5 (orchestrator from Phase 4)
- `GET /api/trips/{id}/planb`: the precomputed Plan B (next flight + nearest airport hotel), stored offline. TODO.
- `GET /api/trips/{id}/options`: 2–3 scored options after a disruption, with plain-language explanations. TODO.
- `POST /api/trips/{id}/options/{optId}/apply`: runs the booking orchestrator (scarcest first, rollback on failure).
  Returns the result, or `wa.me` / `mailto` links for changes that have no API. TODO.

### AI assistant: Phase 6
- `POST /api/assistant/chat`: chat in the user's language (Hinglish and other mixed input are OK). Gemini can call
  `getTrip`, `getFlightStatus`, `compareCosts`, `getOptions` and `applyOption`. **`applyOption` runs only after the
  user explicitly confirms.** TODO.

### Directions and cost: Phase 7
- `GET /api/trips/{id}/directions?leg=N`: directions for one of the 6 door-to-door legs (Google Routes API or a
  distance-based mock). TODO.
- `POST /api/cost/compare`: true cost per flight option (fare + bag fee + cab cost at that hour + seat fee − best card
  offer), with labels Cheapest / Fastest / Least stress. TODO.

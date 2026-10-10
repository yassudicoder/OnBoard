# mock-data: demo seed files

JSON files that fill the backend with realistic **fake** data, so the whole demo runs with zero API keys and the same
story plays out every time.

> **Phase 1:** `demo-trips.json` (the two demo trips) is here. The other files below are created in the phase shown.

**Everything here is invented.** Use made-up names, PNRs, phone numbers and booking references, and never put real
personal data in this folder. It is committed to git.

---

## Files

| File | What it holds | Created in | Used by |
|---|---|---|---|
| `demo-trips.json` | The two demo trips as blocks + links ([details below](#demo-tripsjson-the-two-demo-trips)) | **Phase 1 (here now)** | The backend's seed loader (`DemoTripSeeder`): at startup on an empty database, and on **Reset demo** (`POST /api/demo/reset`) |
| `flights.json` | Mock flight inventory: 6–8 Indian routes, 3 airlines, a few flights a day each, seats left (some nearly full), fare, bag allowance and bag/seat fees | Phase 4 | MockAir; later Plan B (Phase 5) and cost compare (Phase 7) |
| `hotels.json` | Hotels near the demo destinations (Bengaluru near the airport and Whitefield, Delhi airport, Leh): rooms, price per night, check-in time | Phase 4 | MockStay |
| `cabs.json` | Cab types (e.g. mini, sedan, SUV) with base fare and per-km price; night surge 1.8× from 00:00 to 05:00 | Phase 4 | MockCab, cost compare |
| `restaurants.json` | A few restaurants (Bengaluru, Leh) with table slots and party sizes | Phase 4 | MockTable |
| `offers.json` | 6–8 card offers: bank, card type, % off, max discount ₹, valid airlines, valid from/to, minimum spend ₹ | Phase 7 | True-cost compare (F7) |
| `terminal-map.json` | Points for ONE terminal (Mumbai T2 or Delhi T1, picked in Phase 7): gates, security, check-in rows, washrooms, food, exits. Each has x/y coordinates on our own vector map, plus walking minutes between neighbouring points | Phase 7 | Airport map screen, entry → gate directions |

The partner rules (free-change windows, fees) come from CLAUDE.md §5. Phase 4 decides whether they live in these
files or in the mock partner code:

- **MockAir:** change fee ₹2,500, free when the change is caused by a disruption; rebooking only onto flights with free
  seats.
- **MockStay:** free cancellation until 24 h before check-in; a late-arrival note is always allowed; "no-show" after
  23:59 without a note.
- **MockCab:** pickup change free until 30 min before (₹200 after); cancellation free until 60 min before.
- **MockTable:** free change or cancellation until 18:00 on the day.

---

## demo-trips.json: the two demo trips

When the backend starts and its database has **no trips yet**, the seed loader reads this file and saves both trips.
On every later start it sees the trips already there and leaves them alone. **Reset demo** on
<http://localhost:8080/demo> (`POST /api/demo/reset`) deletes all trips and loads this file again, with fresh dates.

The loader is `DemoTripSeeder` in the backend's `demo` package. It doesn't open this folder directly: `backend/pom.xml`
copies the JSON files from here into the backend when it is built (under `mock-data/`), and the loader reads
`classpath:mock-data/demo-trips.json`. That works the same with `./mvnw spring-boot:run`, in the tests and in a
deployed jar, where the repo folder doesn't exist.

- **Trip 1, business (English):** "Client visit: Mumbai to Bengaluru". Flight 08:00–09:45 (MockAir) · bag claim
  20 min · cab pickup 10:05 (MockCab, shifts automatically) · client meeting 12:00 in Whitefield (the **anchor**, must
  not miss; asks the user; Google Calendar or mock) · hotel check-in 14:00 (MockStay) · dinner 20:00 (MockTable).
  Travel times: airport → office 75 min, office → hotel 40 min, airport → hotel 60 min.
- **Trip 2, first-time flyer (Marathi):** "Leh holiday via Delhi". Pune → Delhi → Leh, a 58-year-old traveller,
  elderly persona, purpose HOLIDAY. The Delhi connection has a 75-minute minimum connection time. Leh hotel + airport
  cab.

### What the file looks like

Shortened to one block and one link (the real file has all of them):

```json
{
  "_comment": ["..."],
  "trips": [
    {
      "key": "demo-business",
      "title": "Client visit: Mumbai to Bengaluru",
      "purpose": "BUSINESS",
      "persona": "BUSINESS",
      "language": "en",
      "travellers": 1,
      "status": "PLANNED",
      "blocks": [
        {
          "key": "flight",
          "type": "FLIGHT",
          "title": "Flight IJ 512 Mumbai to Bengaluru",
          "day": 0, "start": "08:00", "end": "09:45",
          "locationName": "Mumbai Airport T2 (BOM)",
          "lat": 19.0896, "lng": 72.8656,
          "provider": "MOCK_AIR", "providerRef": "K7Q2ZM",
          "flex": "FIXED", "anchor": false,
          "freeChangeUntilMinutes": null, "changeFeeInr": 2500,
          "notes": "Arrives at Bengaluru T1 (BLR). ..."
        }
      ],
      "links": [
        { "from": "flight", "to": "bags", "minGapMinutes": 0, "travelMinutes": 0, "travelMode": "NONE" }
      ]
    }
  ]
}
```

| Where | Field | Meaning |
|---|---|---|
| top | `_comment` | Notes for people reading the file (JSON has no real comments). Allowed only at the top: the strict loader rejects a `_comment` inside a trip, block or link. |
| trip | `key` | A name for the trip inside this file. The database gives it a number (`id`). |
| trip | `title`, `purpose`, `persona`, `language`, `travellers`, `status` | Copied into the trip as they are. `language` is a short code (`en`, `mr`, ...). |
| block | `key` | A name for the block inside its trip. Links use it to point at blocks. |
| block | `type`, `title`, `locationName`, `lat`, `lng`, `provider`, `providerRef`, `flex`, `anchor`, `changeFeeInr`, `notes` | Copied into the block as they are. |
| block | `day`, `start`, `end`, `endDay` | The block's times, relative to the trip day (next section). |
| block | `freeChangeUntilMinutes` | Until when a change is free, in minutes from the block's start: `-30` = until 30 minutes before it starts, `0` = until it starts, `null` = never free (`changeFeeInr` always applies). |
| block | `providerRef` | The booking reference at the provider. `null` = nothing booked (bag claim). |
| link | `from`, `to` | The `key`s of two blocks of the same trip. |
| link | `minGapMinutes`, `travelMinutes`, `travelMode` | "`to` may start at the earliest `minGapMinutes` + `travelMinutes` after `from` ends". |

`freeChangeUntilMinutes`, `providerRef`, `locationName`, `lat`, `lng` and `notes` may be `null`. Blocks have no
`status` here: every block starts as `OK`, and every trip starts at `version` 1.

### Times are relative, so the demo never goes stale

The file has no dates. The seed loader works them out when it runs:

- **Trip day** = today in India (Asia/Kolkata, from the backend's `Clock`) + `safarsaathi.seed.day-offset` days.
  The default offset is **1, so the trip is always tomorrow**, counted from the day the trips were loaded.
- `day` = days after the trip day (`0` = the trip day itself, `1` = the day after, ...).
- `start` and `end` = the clock time, `HH:mm` on a 24-hour clock, in Indian time.
- `endDay` = the day the block ends, only needed when that's a later day (an overnight train, a hotel stay). Without
  it, the block ends on its `day`.

Example: loaded on 10 October 2026 with the default offset, the trip day is 11 October, so
`"day": 0, "start": "08:00"` becomes `2026-10-11T08:00:00+05:30`.

The dates are fixed at the moment of loading, so after a day the trips lie in the past (and free-change windows, such
as the cab's, may have closed). Press **Reset demo** on <http://localhost:8080/demo> to load them again with fresh
dates.

### Ids follow the file order

On an **empty** database, the database numbers everything in file order, trip 1 first:

| Trip id | Title | Block ids (key) | Link ids (from → to) |
|---|---|---|---|
| 1 | Client visit: Mumbai to Bengaluru | 1 `flight`, 2 `bags`, 3 `cab`, 4 `meeting`, 5 `hotel`, 6 `dinner` | 1 flight → bags, 2 bags → cab, 3 cab → meeting, 4 meeting → hotel, 5 hotel → dinner |
| 2 | Leh holiday via Delhi | 7 `flight-pnq-del`, 8 `flight-del-ixl`, 9 `bags-ixl`, 10 `cab-ixl`, 11 `hotel-leh` | 6 flight-pnq-del → flight-del-ixl, 7 flight-del-ixl → bags-ixl, 8 bags-ixl → cab-ixl, 9 cab-ixl → hotel-leh |

The app opens trip 1 (`AppConfig.DEMO_TRIP_ID`), and the demo triggers from Phase 2 use these numbers too: for example
`{"tripId": 1, "blockId": 1, "minutes": 90}` delays the trip-1 flight. That's why new trips, blocks and links go at
the **end** of their list: then the existing ids don't move. **Reset demo** gives the same ids again: after deleting
the trips, the backend restarts the id counters at 1 before it loads this file.

The backend log shows each trip it loads, for example
`Demo trip 1 saved: "Client visit: Mumbai to Bengaluru" (6 blocks, 5 links, trip day 2026-10-11)`. On a later start
it logs `Demo trips not loaded again: the database already has 2 trip(s)`.

### Allowed values

They must match [CLAUDE.md](../CLAUDE.md) §3 exactly (upper case, underscores):

| Field | Allowed values |
|---|---|
| trip `purpose` | `BUSINESS`, `WEDDING`, `HOLIDAY`, `PILGRIMAGE`, `MEDICAL`, `EXAM` |
| trip `persona` | `FIRST_TIME`, `FAMILY`, `BUSINESS`, `ELDERLY` |
| trip `status` | `PLANNED`, `ACTIVE`, `DONE` |
| block `type` | `FLIGHT`, `BAG_CLAIM`, `CAB`, `HOTEL`, `MEETING`, `EVENT`, `RESTAURANT`, `TRAIN`, `OTHER` |
| block `provider` | `MOCK_AIR`, `MOCK_STAY`, `MOCK_CAB`, `MOCK_TABLE`, `GOOGLE_CALENDAR`, `NONE` |
| block `flex` | `FIXED`, `AUTO_SHIFT`, `ASK_USER` |
| link `travelMode` | `WALK`, `CAB`, `METRO`, `NONE` |

Text limits (from the database tables): `title` and `locationName` up to 200 characters, `providerRef` up to 64,
`notes` up to 1000, `language` up to 8.

### Changing the file

1. Use only the allowed values above, and point every link at block `key`s of its own trip. The loader is strict on
   purpose: a misspelt field name, an unknown value, a link to a missing `key`, or a block that ends before it starts
   stops it with an error that names the problem, instead of loading half a trip.
2. Add new trips, blocks and links at the **end**, so the existing ids stay the same.
3. Check that every link fits the times: the `to` block should start at or after the `from` block's end +
   `minGapMinutes` + `travelMinutes`. Otherwise the trip starts out already broken.
4. The loader only runs on an **empty** database or on a reset. To load your change: restart the backend (so the new
   file is copied in, see [Decided in Phase 1](#decided-in-phase-1)), then press **Reset demo** on
   <http://localhost:8080/demo> (or `POST /api/demo/reset`). It deletes all trips and loads the file again. The
   backend doesn't start any more? Then `docker compose down -v` (**deletes all local data**) and
   `docker compose up -d` in the repo root give you an empty database, and the next backend start loads the file.

---

## Conventions

- UTF-8 JSON with 2-space indent, one file per kind of data.
- Field names follow the domain model in CLAUDE.md §3 where possible (e.g. `freeChangeUntilMinutes`, `changeFeeInr`).
- **Times:** `demo-trips.json` uses relative times (above) so the demo never goes stale. A file that needs real dates
  uses ISO-8601 with the India offset, e.g. `2026-10-10T08:00:00+05:30`.
- **Money:** whole rupees as integers, with field names ending in `Inr`.
- Records refer to each other by a stable `key` inside a file. The database hands out the numeric `id`s.

## Decided in Phase 1

- **Demo dates:** relative to the day of loading, as described above. Not fixed dates.
- **How the backend reads these files:** from the classpath (`classpath:mock-data/<file>.json`). `backend/pom.xml`
  copies every `*.json` file in this folder into the backend at build time, so after editing a file, restart the
  backend (`./mvnw spring-boot:run` copies it again).

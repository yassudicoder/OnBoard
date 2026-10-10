# recovery-core

Plain Java shared by the **backend** and (from phase C3) the **Android app** for Disruption Recovery: the airport
rules model and the **connection checker** (C1), later the ranking of alternative flights (C2). One copy of the code,
so the server and the phone (offline) always reach the same verdict with the same reason.

- **No libraries, no Android, no Spring.** Public fields and no-argument constructors, so Jackson (backend) and Gson
  (app) both read `mock-data/recovery/airport-rules.json` straight into these classes.
- **Java 8 library calls only** (no `List.of`, `String.isBlank`, ...): the app runs on Android 8 (API 26).
  `java.time` is fine (Android 8 has it).
- Same pattern as [`routing-core`](../routing-core/README.md) (Gate Navigation).

## What's inside

| Package | Classes | What |
|---|---|---|
| `rules` | `AirportRules`, `AirportPack`, `ConnectionRule`, `SourcedMinutes`, `TransferEstimates`, `NearbyAirport`, `ValueKind`, `ConnectionType` | The shape of `airport-rules.json`, and `AirportRules.problems()`, which checks the file |
| `connection` | `ConnectionChecker`, `ConnectionCheck`, `ConnectionStatus`, `Certainty`, `FlightLeg`, `FlightPhase`, `ConnectionFacts`, `TicketType` | Will the traveller make it from flight 1 to flight 2, and why |

The reasons are sentences in `src/main/resources/com/safarsaathi/recovery/connection/connection_text_en.properties`
(Hindi and Marathi files follow in C3/C4; a missing language falls back to English).

## The connection rule, in short

`available` = flight 2's departure - flight 1's landing (latest estimates, else the timetable).
`MCT` = the airport's minimum connection time from the rules file (official, or a labelled DEMO VALUE; none = UNKNOWN).
`practical` = taxiing + getting off + transfer security (team estimates) + gate closing before departure.

| Status | When | Certainty |
|---|---|---|
| RESOLVED, REBOOKED_BY_AIRLINE | confirmed facts the caller keeps | confirmed |
| CANCELLED | flight 1 or 2 cancelled | confirmed |
| CONFIRMED_MISSED | flight 2 left while flight 1 had not landed, or confirmed earlier | confirmed |
| LIKELY_MISSED | flight 2 leaves before flight 1 lands, or `available < MCT` | predicted |
| UNKNOWN | no MCT for this airport and connection type | unknown |
| AT_RISK | `available < max(MCT, practical) + 15 min` | predicted |
| OK | otherwise | predicted |

Only the official (or demo) MCT can make a connection "likely missed"; our own estimates can only add a warning.

## How both builds use it (no separate build of its own)

| Who | How | File |
|---|---|---|
| Backend (Maven) | compiles `src/main/java` and `src/main/resources` with its own code, and runs `src/test/java` with its tests (JUnit 5, AssertJ; the rules-file test borrows the backend's Jackson) | `backend/pom.xml`, `build-helper-maven-plugin` |
| Android app (Gradle) | from phase C3: compiles `src/main/java` with the app's code | `android/app/build.gradle.kts`, `sourceSets` |

So `./mvnw test` in `backend/` runs the recovery-core tests too.

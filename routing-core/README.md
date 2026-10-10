# routing-core

Plain Java shared by the **backend** and the **Android app**: the airport map model (G1) and, from phase G2, the
route finder (A*) and the step-by-step instruction generator. One copy of the code, so the app (offline) and the
backend (`POST /api/nav/route`) always find the same route.

- **No libraries, no Android, no Spring.** Public fields and no-argument constructors, so Jackson (backend) and Gson
  (app) both read `map.json` straight into these classes.
- **Java 8 library calls only** (no `List.of`, `String.isBlank`, ...): the app runs on Android 8 (API 26), where newer
  calls are missing.

## How both builds use it (no separate build of its own)

| Who | How | File |
|---|---|---|
| Backend (Maven) | compiles `src/main/java` with its own code, and runs `src/test/java` with its tests (JUnit 5) | `backend/pom.xml`, `build-helper-maven-plugin` |
| Android app (Gradle) | compiles `src/main/java` with the app's code, and packs `src/main/resources` | `android/app/build.gradle.kts`, `sourceSets` |

So `./mvnw test` in `backend/` runs the routing-core tests too.

## Layout

```
src/main/java/com/safarsaathi/routing/map/     AirportMap, MapNode, MapEdge, MapFloor, MapAnchor, enums, AirportMapValidator
src/main/java/com/safarsaathi/routing/route/   Router (A*), WalkProfile, Route (+ progress maths), RouteLeg, RouteStep,
                                               Maneuver, StepBuilder (turns + landmarks), InstructionText (the words)
src/main/resources/com/safarsaathi/routing/route/   instructions_en / _hi / _mr.properties ({0} = place, {1} = floor)
src/test/java/com/safarsaathi/routing/map/     AirportMapValidatorTest (runs on mock-data/airports/BOM-T2/map.json)
src/test/java/com/safarsaathi/routing/route/   RouterTest, InstructionTextTest (3 sample routes in English, Hindi, Marathi)
```

## Routing in one paragraph

`new Router(map).route("entry_4", "gate_32", WalkProfile.normal())` returns the quickest route in walking seconds:
1.2 m/s (slow 0.8 m/s), travelators +0.6 m/s, lifts +60 s wait, escalators 0.5 m/s, stairs at half pace. All of these
are team estimates. Step-free never uses stairs or escalators. One-way walkways (security, travelators, the arrivals exit)
mean you can't skip security or walk back through it. `route.steps` are the instructions: "Head towards …", a turn
wherever the direction changes by 25° or more (named after the place where it happens), security, travelators, lifts /
escalators / stairs (up or down, to which floor), and the arrival. `InstructionText.forLanguage("mr").text(step, map)`
gives the sentence.

The map data itself lives in `mock-data/airports/BOM-T2/` (see the header of `map.json`: a demo schematic, not an
official airport map).

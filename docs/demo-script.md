# Safar Saathi: demo script

Two scripts in one file:

1. **[Tonight (22:00): the 3-minute demo of Phases 1–2](#tonight-the-3-minute-demo-of-phases-12)**: the domino idea,
   the trip timeline, a 90-minute flight delay, the phone updating by itself, offline, reset. Everything in it is
   built for tonight: do one full dry run before 22:00.
2. **[Later: the full demo](#later-the-full-demo-finalised-in-phase-8)**: the 8-phase story (Plan B, front desk,
   boarding pass, Marathi assistant). **Coming later**, finalised in Phase 8.

---

## Tonight: the 3-minute demo of Phases 1–2

**The story in one line:** a business trip is a chain of bookings → the flight is 90 minutes late → the app shows
which bookings moved by themselves for free and which one needs the traveller → it still works with no signal.

### Screen setup

One laptop on the projector: the **emulator** (app on the **Trip** tab) on the left half of the screen, and a browser
with **<http://localhost:8080/demo>** on the right half. The judges see the button press and the phone at the same time.

### Roles

| Who | Does |
|---|---|
| **Narrator** | Tells the story and keeps time (phone stopwatch). |
| **Driver** | Presses the buttons on `/demo` and works the emulator (scroll, airplane mode). |
| **Backup** | Watches the backend log, has the screenshots ready, takes the judges' questions. |
| 4th person | Second backup: keeps an eye on the clock and steps in if the driver gets stuck. |

### Script

| Time | Screen | Do | Say (roughly) |
|---|---|---|---|
| 0:00–0:25 | Emulator: Trip tab, all green | Nothing yet. | "A trip isn't one booking, it's a chain: flight, bags, cab, meeting, hotel, dinner. When the flight is late, everything after it falls like dominoes, and today the traveller fixes each one by hand. Safar Saathi treats a trip as that chain." |
| 0:25–0:55 | Emulator: Trip tab | Scroll slowly through the timeline. Point at the star on the meeting. | "Business trip, Mumbai to Bengaluru, tomorrow. Each card is a booking. The links between them know the travel times: the cab to Whitefield takes 75 minutes. The star is the anchor, the reason for the trip: the client meeting. Everything is green." |
| 0:55–1:45 | `/demo`, then the emulator | Press **Delay +90 min** ("Booking to disrupt" is already on the flight). Point at "What just happened" on `/demo`. Then pull the timeline down on the phone (or wait up to 10 s, it refreshes by itself). Point at the flight (now 09:30), bags + cab (amber, "Auto-adjusted"), the meeting (red, "Needs you") and the red banner "1 thing needs your decision: Client meeting in Whitefield". Tap the banner: the meeting's details open. | "The airline just announced a 90-minute delay. Our ripple engine walks the chain. Bags and cab moved by themselves, for free, because the cab can be changed free until 30 minutes before pickup. The meeting is now 50 minutes short. It's the anchor, so we never move it without asking: it's red, and the banner asks the traveller to decide. Hotel and dinner have enough slack, so they stay green." |
| 1:45–2:20 | Emulator | Swipe down from the top and turn on **airplane mode**. Back on the Trip tab, the line under the title says "Offline · showing your saved plan from HH:MM" (within 10 s, or pull down), and the changed plan is still there. | "Now the traveller is at the gate with no signal. The whole plan, including what just changed, is saved on the phone. The screen never reads from the internet, only from the phone's own database." |
| 2:20–2:45 | Emulator, then `/demo` | Turn airplane mode **off**. Press **Reset demo**, then pull the timeline down. The phone is back to all green ("On track"). | "Back online, the app catches up by itself. And one click puts the demo back to the start." |
| 2:45–3:00 | Both | Nothing. | "No API keys, no magic: plain Java rules with unit tests, the same answer every time. Next: two or three fix options with one-tap apply, partner bookings changed with automatic rollback, and a voice assistant in Marathi." |

Numbers to remember (trip 1, delay +90): flight 08:00 → **09:30**, cab pickup 10:05 → **11:35**, meeting at 12:00 is
**50 minutes short** (11:35 + 75-minute ride = 12:50), hotel check-in is open until 23:00.

### Before the judges arrive (by 21:45)

- [ ] Docker Desktop running, `docker compose up -d` in the repo root, and `docker compose ps` shows `(healthy)`.
- [ ] Backend running (`./mvnw spring-boot:run` in `backend/`), and the log shows `Safar Saathi backend is ready`.
- [ ] Press **Reset demo** once: fresh dates (trip day = tomorrow) and everything green.
- [ ] Emulator started, app opened on the **Trip** tab **while online**, showing all green. This also saves the plan on
      the phone for the offline step. (First start on Android 17: tap **Allow** on the "nearby devices" question,
      or the app can't reach the backend.)
- [ ] Airplane mode is **off**, and the driver knows where the switch is (swipe down from the top of the emulator).
- [ ] `/demo` open in the browser, zoomed in so the buttons are readable on the projector.
- [ ] One full dry run (delay → airplane mode → back online → reset), then leave it reset.
- [ ] Backup: screenshots of the green timeline and of the amber/red timeline after the delay, open in a folder.
- [ ] Laptop on the charger, notifications off, other heavy apps closed (the emulator needs the memory).

### If something goes wrong

- **The phone doesn't change after "Delay +90 min":** keep talking and walk through "What just happened" on `/demo`
  (every booking with its status and reason, plus the broken link). Check that the phone isn't in airplane mode,
  then pull the timeline down to refresh. The line under the trip title should say "Updated HH:MM".
- **`/demo` shows an error after a press:** press **Reset demo**, then **Delay +90 min** again. (After **Cancel it**,
  the flight can't be delayed any more: that's the "Error 409".)
- **The backend crashed:** that's the offline moment. Show that the phone still has the plan, then restart the backend
  (`./mvnw spring-boot:run`, about 30 s). The trips are kept in the database.
- **Anything else:** "Here are the screenshots from our run." Don't debug in front of the judges.

### Questions the judges may ask

- **Is the flight data real?** Not tonight: the flight status is a mock that we control, so the demo is repeatable. A
  real provider (AeroDataBox) plugs in behind the same Java interface once an API key is set.
- **Why is the hotel still green?** Hotel check-in is a window, not a fixed time: arriving any time before 23:00 is
  fine. Between 23:00 and midnight we would add a late-arrival note, and after midnight the traveller decides.
- **What happens to the red meeting now?** That's Phase 5: the app offers 2–3 fixes (for example, move the meeting or
  switch to a video call), scored for a business trip, explained in plain words and applied with one tap.
- **How does the phone know about the change?** Tonight it asks the backend for the trip every 10 seconds while the
  trip screen is open (or when you pull it down). Push notifications (Firebase) come in Phase 3. The phone only
  replaces its saved copy when the server's version number is higher, or when the demo data was reloaded.
- **Why did the cab move but the meeting didn't?** Every booking has rules. The cab can be changed for free until 30
  minutes before pickup. The meeting is the anchor and set to "ask the user", so only the traveller decides.

---

## Later: the full demo (finalised in Phase 8)

> **Coming later. Draft outline, finalised in Phase 8** ("Polish + demo", [CLAUDE.md](../CLAUDE.md) §9).
> The features shown here are built in Phases 1–7, so exact taps, wording and timings will change. Rehearse with a
> stopwatch once Phase 7 is done, then update this section.

**The story in one line:** trip 1's flight is delayed → the app fixes the whole day → the front desk sees the bookings
change live → airplane mode, and the boarding pass still opens → a Marathi-speaking first-time flyer asks the
assistant on trip 2.

### Roles (4 people)

| Who | Does |
|---|---|
| **Narrator** | Tells the story and keeps time. |
| **Phone driver** | Drives the app on the emulator or phone (shown on the projector). |
| **Laptop driver** | Fires the demo triggers and shows the `/desk` dashboard. |
| **Backup** | Watches for problems, switches to the backup video if needed, takes the judges' questions. |

### Script

| Time | Screen | What we do | What we say (roughly) | Shows |
|---|---|---|---|---|
| 0:00–0:20 | App home | Nothing yet | "One late flight can wreck a whole day: the cab leaves, the meeting is missed, the hotel gives the room away. Safar Saathi treats a trip as a row of dominoes." | The idea |
| 0:20–0:45 | App: **Trip 1** timeline + "Do this now" | Scroll the timeline: flight 08:00 → bags → cab 10:05 → client meeting 12:00 (star = must not miss) → hotel 14:00 → dinner 20:00 | "Business trip, Mumbai to Bengaluru. Everything is green." | F1, F3 |
| 0:45–1:15 | Laptop, then phone | Trigger a 90-minute delay → push arrives → timeline turns amber and red | "The flight is 90 minutes late. The cab shifted by itself (amber). The meeting needs a decision (red)." | F4 |
| 1:15–1:45 | App: **Fix-it** screen | Show the 2–3 option cards (extra ₹, time lost, *Recommended*) → tap **Apply** | "Three fixes, scored for a business trip and explained in plain words. One tap." | F5, F6 |
| 1:45–2:10 | Laptop: **`/desk`** | Bookings change live: cab pickup moved, hotel late-arrival note, dinner moved, change log fills up | "This is the partners' side. Every booking changed, scarcest first, and if one fails it all rolls back." | F6 |
| 2:10–2:30 | Phone, **airplane mode ON** | Offline banner "last updated HH:MM" → Wallet → boarding pass opens → Plan B and terminal map | "No signal at the gate? The boarding pass, the plan and Plan B are all on the phone." | F8 |
| 2:30–2:55 | Phone, **airplane mode OFF**, **Trip 2** | Switch to trip 2 (Marathi, large text). Tap the mic and ask "माझी फ्लाइट वेळेवर आहे का?" ("Is my flight on time?") → the answer comes in Marathi and is read aloud | "A 58-year-old first-time flyer, Pune to Delhi to Leh. Asks in Marathi, gets the answer in Marathi, out loud." | F9, F10 |
| 2:55–3:00 | App home | Nothing | "Runs with zero API keys. Real services plug in behind the same interfaces." | Close |

Feature numbers (F1–F10) are the judges' must-have list in CLAUDE.md §1. Not shown live, but ready for questions: F2
door-to-door directions, F7 true-cost compare with card offers.

### Checklist (full demo)

- [ ] Laptop: database up (`docker compose up -d`), backend running with `APP_MODE=demo`.
- [ ] Fresh demo data: **Reset demo** on `/demo` (or `POST /api/demo/reset`).
- [ ] `/desk` open in a browser on the second screen.
- [ ] Phone/emulator: app installed, notifications allowed, trip 1 opened once **while online** (so Room and Plan B are
      saved for airplane mode), boarding pass already scanned into the Wallet.
- [ ] Trip 2 set up with Marathi and the elderly persona. Volume up for read-aloud.
- [ ] Backup screen recording of a full run, open and paused.

### If something goes wrong (full demo)

- **Push is slow:** open the Fix-it screen from the red banner on the timeline instead.
- **Venue Wi-Fi is bad:** the backend runs on the laptop and every partner is mocked, so only the AI assistant needs
  the internet. Without it, the app falls back to template replies (still in Marathi).
- **Anything else:** say "here's the recorded run" and play the backup video. Don't debug in front of the judges.

### To decide in Phase 8

- Real phone or emulator on the projector?
- Whether to show the rollback path (`/mock/*/fail-next`) if there is time left.
- Demo trip dates: relative to the moment the demo trips are loaded (by default the trip is the next day, set by
  `safarsaathi.seed.day-offset`; see [mock-data/README.md](../mock-data/README.md)). **Reset demo** re-dates them.
  Still open: load them with offset 0 on the demo morning, so the trip runs "today" and the "Do this now" card matches
  the clock?

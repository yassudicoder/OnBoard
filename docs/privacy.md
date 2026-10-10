# OnBoard: Privacy and the Document Vault

What the Document Vault keeps, where it keeps it, how it is protected, how to delete it, and where its limits are.
Written for the team and for anyone reviewing the app (judges, mentors). Plain English first; technical detail
after each point.

> **Status:** V1 (secure vault core) and V2 (adding documents) are built. Lines marked *(V3)* to *(V6)* describe
> later phases and are **not built yet**. This file is updated in every vault phase.

---

## 1. The short version

- Your travel documents stay **on your phone**, inside one private folder, **encrypted**. The vault never sends
  them anywhere. It needs no internet to open, show or check them.
- Opening the vault needs your **fingerprint, face or phone PIN**. It locks itself after 1, 5 or 15 minutes without
  use (your choice; 5 by default).
- Vault screens are **blocked from screenshots and screen recording**, and they show blank in the recent-apps view
  (you can switch this off).
- **"Delete the whole vault"** removes every document, page and key from the phone for good. Uninstalling the app
  does the same.
- Copies in the app **do not replace your original documents**. The app cannot tell whether a passport or visa is
  genuine. When it compares details, it says "Checked", never "Verified".

---

## 2. What the vault keeps

| Data | Why | Where | Extra protection |
|---|---|---|---|
| Traveller: name, nationality, date of birth | To match documents to a person; name checks *(V4)* | Vault database | Whole database encrypted |
| Trip folders: title, dates, legs (e.g. "Kigali transit"), countries and their role | To group documents and run the pre-departure checks *(V4)* | Vault database | Whole database encrypted |
| Document details: type, name on document, issuing country, dates, flight number, booking reference; for hotels the hotel's name and address | To show key details above the image, run checks, and (hotel address) plan a ride there | Vault database | Whole database encrypted |
| **Document number** (passport, visa, policy, ...) | To show it to an official | Vault database | Encrypted **a second time** with the Keystore key, and shown masked (`••••••01`) |
| Pages: scans, photos, PDFs | To show the document offline | `files/` folder | Each file has its **own** key; SHA-256 fingerprint stored |
| Settings: lock on/off, auto-lock minutes, screenshot blocking | Your choices | App settings | None needed (no personal data) |
| Medical notes on the emergency card *(V5)* | Only if you choose to add them | Vault database | Opt-in, phone only, never in backups unless you turn backup on |

Not kept: no Aadhaar number, no card numbers, no location history, no contacts list, no analytics about your
documents. (The separate Wallet screen keeps Aadhaar **last 4 digits only** and a PAN shown masked; see section 8.)

The **sample trip** ("Paramvir", Delhi → Kigali → Kinshasa) is made up. Every sample document carries a SAMPLE label
and "SAMPLE - NOT A REAL DOCUMENT" across its page image. Its flights use the airline code "ZZ" only as an obviously
fake code.

---

## 3. Where it lives

Everything the vault keeps is in one private folder of the app, `no_backup/vault/`:

```
no_backup/vault/
  vault.db (+ -wal, -shm)   the SQLCipher-encrypted database
  db.key                    the database key, wrapped (encrypted) by the Keystore master key
  files/<id>.bin            every page and PDF, each encrypted with its own key
```

- Android never copies `no_backup` into Google's cloud backup or onto a new phone during a device transfer.
- Other apps cannot read it (Android keeps each app's private folder to itself).
- The master key is not in this folder: it lives in the **Android Keystore** and cannot be exported or copied.
- The vault makes **no network calls**. Opening, showing and checking documents works in airplane mode.
- Reading a passport's MRZ and boarding pass barcodes uses Google ML Kit models **inside the app**: offline, on the
  phone, nothing is sent anywhere.
- The one download: when the vault opens, it asks Google Play services to fetch Google's document scanner (screen
  and models) in advance, so scanning also works offline later. That is a Play services download; no document data
  is involved.
- *(V5)* Country guidance packs are downloaded from our server (public information only; nothing about you is sent).
- *(V6)* Optional encrypted backup: **off by default**. If you turn it on, everything is encrypted **on the phone**
  before upload, with a key made from a recovery phrase that only you hold. The server stores data it cannot read.
  Restoring needs the recovery phrase; without it, nobody (including us) can restore the backup.

---

## 3b. Adding a document (V2): where the pages pass through

| Way in | What happens to the page before it is encrypted |
|---|---|
| **Scan** with Google's document scanner | The scanner (a Google Play services screen) saves each page as a JPEG in this app's **private cache folder**. The vault reads it into memory and **deletes the file at once**. |
| **Scan** with the vault's own camera (when Google's scanner cannot run) | The photo is taken **straight into memory** (never a file, never the gallery), cropped on screen, encrypted. |
| **Import** a PDF or photo | Read from the file the traveller picked, straight into memory. The original file stays where it was; the vault does not delete the traveller's own files. |
| **Type it in** | No page; only the typed details. |

- Pages wait in memory only (CaptureViewModel) until they are saved; cancelling or leaving the vault wipes them
  (the bytes are overwritten with zeros).
- Fields read from the MRZ or a barcode are **never saved unseen**: the confirm screen shows every field, says what
  was read and which check digits failed, and saving needs the tick "I compared these details with my document".
- The number field tells the keyboard not to learn or suggest what is typed (`textNoSuggestions`).
- **Limit:** Google's scanner screen belongs to Play services, so the vault's screenshot blocking does not cover
  that one screen. Every vault screen of our own (camera, crop, confirm) is covered.

---

## 3c. What other parts of OnBoard may read (decided 2026-10-11)

Two other features may read a **summary** of the vault, through one read-only window (`VaultSummaryReader`), and
nothing else:

| Who | May read | Where it goes |
|---|---|---|
| Ask OnBoard | per document: kind, holder's **first** name, expiry date, covered dates and countries, a status word, SAMPLE flag; hotel name and address | Answers made **on the phone** only; never sent to a cloud model or our server |
| Ride hand-off | the hotel's name and address | Used on the phone to open the ride app with that address |

- Never readable by them: document numbers, the machine-readable lines, page images, file names or paths, full
  names, dates of birth. The summary does not even have fields for them (checked by a unit test).
- **While the vault is locked**, only the hotel's name and address can be read (so a ride to the hotel can be planned
  without unlocking). Everything else needs the vault unlocked, with the same fingerprint / face / PIN prompt.
- Nothing from the vault is sent to our backend.

---

## 4. How it is protected

```
Android Keystore (secure hardware on most phones)
└── Vault master key: AES-256-GCM, cannot be exported
    ├── wraps → database key (32 random bytes) → SQLCipher encrypts the whole vault.db (AES-256)
    ├── wraps → one key per file (AES-256-GCM)  → each page / PDF in files/
    └── encrypts → document numbers (a second layer inside the encrypted database)
```

**Encryption.** Every file is encrypted with AES-256-GCM using its own random key ("envelope encryption"). The file
keys and the database key are themselves encrypted ("wrapped") with the master key in the Android Keystore. GCM
includes a tamper seal: a file changed by even one bit, or opened with the wrong key, fails to open instead of
showing garbage. Each sealed file also carries a label (its own id), so two files swapped on disk both fail to open.
Code: `android/app/src/main/java/com/safarsaathi/app/data/vault/crypto/`. We use `javax.crypto` directly, not the
deprecated Jetpack security-crypto library.

**Database.** The vault's database is a separate file from the app's main database, encrypted page by page with
SQLCipher for Android 4.19.1. Without its key, the file is random-looking bytes (no "SQLite format 3" header, no
readable text). The instrumented test `VaultOnPhoneTest` checks this.

**Fingerprints.** For every file the vault stores a SHA-256 fingerprint of the original. Each time a page is shown,
and when you tap "Check all files" in the vault settings, the file is decrypted in memory and its fingerprint
compared. A file that was changed, damaged or swapped is reported, never shown as fine. *(V6: "Make offline-ready"
runs the same check on every file of a trip.)*

**Lock.** androidx.biometric's BiometricPrompt: a strong fingerprint or face, or the phone's PIN, pattern or password
(on Android 9 and 10: any fingerprint or face, or the PIN). The prompt appears when the vault opens and after it
locks itself. Switching the lock **off** needs the fingerprint/face/PIN first. "Lock now" is in the toolbar and in
the settings.

**Screens.** While a vault screen is showing, the app sets Android's `FLAG_SECURE`: screenshots and screen recordings
come out black and the recent-apps preview is blank. Vault dialogs get the flag too (dialogs are separate windows).
Decrypted pages are only ever held in memory while shown. Nothing decrypted is written to the phone's storage.

---

## 5. Honest limits

- **The lock guards the screens, not the key.** In V1 the Keystore master key is *not* tied to your fingerprint
  (Android's `setUserAuthenticationRequired`). Reasons: Android deletes such keys when you remove your screen lock
  (which would wipe the vault), phones without a screen lock could not use the vault at all, and background
  reminders *(V4)* could not read expiry dates. So the lock protects against **someone picking up your unlocked
  phone**. It does not protect against malware that already controls your phone. Moving the document numbers to a
  fingerprint-bound key is planned for the viewer phase *(V3)*.
- **A phone without a screen lock cannot lock the vault.** The vault says so plainly and offers a shortcut to the
  phone's security settings.
- **If Android's Keystore is reset** (rare: some phone repairs or factory-reset-like events), the master key is gone
  and the vault cannot be decrypted, by design. The vault then says it cannot be opened and offers to delete it and
  start again. The optional backup *(V6)* is the way to recover from this.
- **Screenshot blocking can be switched off** by you in the vault settings, for example to share a screenshot with
  family on purpose.
- **Copies are not originals**, and the app cannot judge whether a document is genuine. Officials may still ask for
  the original.
- **Country rules are only as good as their sources.** Any rule the team has not checked against an official source
  shows UNKNOWN *(V4/V5)*. Confirm with the airline or embassy before you fly.

---

## 6. India's Digital Personal Data Protection Act, 2023: how the vault follows the basics

This is the team's engineering reading of the Act's basic ideas. **It is not legal advice**, and the team has not
had it reviewed by a lawyer.

| Basic idea | What the vault does |
|---|---|
| Collect only what is needed | Only the fields needed to show a document and run the checks. No Aadhaar, no card numbers, no tracking. Medical notes are optional *(V5)*. |
| Clear purpose and consent | You open the vault and add documents yourself, for one purpose: showing your own documents and checking them on the phone. Nothing is shared. Backup *(V6)* is off until you switch it on, with a plain explanation first. |
| Keep it accurate | Fields read by the camera *(V2)* are always shown to you to confirm; the document shows whether you confirmed them. |
| Keep it safe | Encryption, lock, auto-lock and screenshot blocking (section 4). |
| Erase on request | Delete one document, one trip folder, or **the whole vault** (settings). The full delete removes the folder and the Keystore key and resets the settings; nothing is left to recover. |
| Do not keep it longer than needed | The vault keeps documents until you delete them. Because nothing is on our servers, uninstalling the app also erases everything. |

Sources (last checked 2026-10-11): Digital Personal Data Protection Act, 2023 (Act No. 22 of 2023) and the Digital
Personal Data Protection Rules, 2025 (notified November 2025, coming into force in stages), Ministry of Electronics
and IT, <https://www.meity.gov.in/>. Before relying on any detail, check the text published in the Gazette of India.

---

## 7. See it for yourself (debug build, emulator or phone with USB debugging)

```bash
# The vault's private folder: an encrypted database, a wrapped key and encrypted files
adb shell run-as com.safarsaathi.app ls -R no_backup/vault

# The first bytes of the database: random bytes, not "SQLite format 3"
adb shell run-as com.safarsaathi.app head -c 32 no_backup/vault/vault.db | xxd

# An encrypted page starts with "OBV1" (our format marker), not with a JPEG header
adb shell run-as com.safarsaathi.app sh -c 'head -c 16 no_backup/vault/files/*.bin | xxd'
```

`run-as` only works for debug builds; release builds do not allow it.

---

## 8. The rest of OnBoard, for completeness

| Part | What it keeps | Where | Note |
|---|---|---|---|
| Wallet (Profile > More) | Boarding pass barcodes, Aadhaar last 4 digits, PAN (shown masked), visa details, card bank + last 4 | App database `safar_saathi.db` | Excluded from cloud backup, but **not encrypted**. Moving it into the vault is an open question for the team. |
| Gate Navigation | Saved terminal map, last known gate | App database | No personal data. |
| Baggage Shield *(B1)* | Bag photos and their SHA-256 | Uploaded to our server as evidence | Planned: kept 30 days after arrival unless a claim is open; you can delete your copies. |

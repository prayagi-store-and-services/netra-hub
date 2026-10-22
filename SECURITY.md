# Security notes - netra-hub

## Driving log now records the trip (v1.4.27)

While driving mode is on, the driving log now also records speed changes (10 km/h or more), hard braking, and bumps seen by the accelerometer (speed breaker or pothole, always marked "likely"), each with time, speed and, when GPS has it, the coordinates. The accelerometer is read only while driving mode is on. The log stays in the app's private storage (newest 500 entries), is never sent anywhere, and is removed by uninstalling or clearing the app's data. No new permission, library or server. Not a vulnerability fix.

## Fuller crash reports (v1.4.26)

Automatic crash reports used to be cut off before the root cause when an exception was wrapped in others (a crash in KBC showed only the wrappers). The report now keeps a few frames of each wrapper and more frames of the root cause, within a larger size limit (6000 characters). It still holds only class names and code locations, never exception messages. No new permission, library or server. Not a vulnerability fix.

## Last security scan survives an update (v1.4.25)

The security score, last scan time and scan duration are now saved in the app's private storage and shown again right after an update or restart, until the fresh scan finishes (a few seconds). Nothing is sent anywhere. No new permission, library or server. Not a vulnerability fix.

## Driving detection fixes and a visible driving log (v1.4.24)

Driving mode now starts at 15 km/h (was 30) and ends after 90 seconds below that (was 30), uses high-accuracy GPS every 5 seconds, and works out the speed from two fixes when the phone sends a position without a speed. Home shows a Driving detection card (mode, GPS speed and its age, a switch for a spoken announcement, a TEST announcement button, and a driving log of time, speed and reason). The log is kept only in the app's private storage (newest 200 entries, never sent anywhere, no backup) and uninstalling or clearing data removes it. No new permission, library or server. High-accuracy GPS uses more battery than before. Not tested on a real ride yet.

## Verified web link (v1.4.23)

The app now declares a verified web link for its own page, https://prayagi-store-and-services.github.io/netra-eco/app/hub. Android opens the link in the app only when the website's assetlinks.json confirms this app's package and signing certificate; otherwise the link stays in the browser. The link is only an entry point: no screen reads data from it, nothing is stored or sent. No new permission, library or server.

## Clearly labelled TEST buttons (v1.4.22)

Both alert paths now have a user-facing TEST control, per the ecosystem rule of 19 Oct 2026. "Test announcement" speaks a fixed line ("This is a test announcement from Netra Hub...") through the exact machinery of a real official alert (alarm stream, TTS, transient audio focus with watchdog, ringtone fallback); it runs even when the announce switch is off because the user explicitly asked, and it still respects the honest hard limits (Do Not Disturb total silence, zero alarm volume). "Test notification" posts a notification titled "NETRA TEST: emergency alert channel" through the same high-priority channel real alerts use, under its own notification id. Neither test uses emergency wording, sends anything off the phone, or changes any setting. No new permission, library or server.


## Official weather alerts: precise matching, once-per-alert voice, audio focus (v1.4.21)

User-reported fixes, not a vulnerability patch. (1) False-area alerts: a Severe/Extreme warning issued by a state body for one district used to alert every phone in that state, because the sender name alone matched. On 19 Oct a phone in Prayagraj was alerted for an area ~70-80 km away this way. The sender name no longer decides. Matching now uses the alert's own polygon/circle against the phone's coordinates when both exist, otherwise only exact whole-word district/locality/state names in the alert's area text. An alert that cannot be verified for the phone's area is not shown. (2) Repeat announcements: the same warning re-issued by SACHET under a new feed id used to speak and notify again. Alerts are now remembered by event and area with their severity; they speak and notify once, again only when the severity changes (up or down) or the warning is over. (3) Spoken announcements now hold transient audio focus so other playing media pauses and then resumes, instead of talking over music. Focus is always released, with a watchdog, so media is never left paused. No new permission, library, feed or server; nothing new is stored or sent.

## Official alert XML parser hardening (v1.4.13)

Reject DTD/entity declarations before parsing RSS or CAP XML, turn off entity expansion, disable external-entity/DTD features where the Android XML provider supports them, and block external resolution. Normal escaped text still parses. Regression tests use a harmless temporary local marker and reject external/internal entity declarations. A desktop JVM probe of the previous default parser read that marker; exploitability on a shipped Android device has not been verified. This is parser hardening, not a confirmed exploited vulnerability. No new permission, library, feed, server or location disclosure. Alert cadence and matching are unchanged.


## Network library version pinned (v1.4.11, corrected in v1.4.12)

Not a security patch. v1.4.11 was first described as a security patch. That was wrong. It set the network library (OkHttp) to 4.12.0 in the version catalog (it was 4.10.0 there, which on its own would pull Okio 3.0.0, in the range affected by CVE-2023-3635, GHSA-w33c-445m-f8w7). But the Hub 1.4.10 build already contained OkHttp 4.12.0, because another library raised it, so the shipped app did not change. The pin only makes the version explicit. We found no vulnerable library in any released Hub build. No new permission, library or server. v1.4.12 only corrects the release notes.

## Sections with no data are hidden (v1.4.9)

Not a security patch. A tab, card or row that has no real data is now hidden instead of showing "Unavailable" or a zero. The Location card and Official Safety Alert Status use only the real location fix and hide when there is none. Walking, standing, timeline, digital wellness, power and report tabs are gone. No new permission, library or server.

## Only real values are shown (v1.4.8)

Not a security patch. Placeholder numbers and test controls were removed so the app never shows a value it did not measure. Steps now come from the phone's own step counter and are the same everywhere in the app. Distance, walking, standing, idle, posture, timeline, trends and digital wellness now say Unavailable until a real, verified method exists. The simulate buttons (screen-time apps, frozen data, "Simulate Enabled") and the manual travel mode override are gone, and the setup wizard no longer marks a feature as on without checking it. No new permission, library or server.

## Driving flag now limited to apps signed with Hub's key (v1.4.7)

Security patch. Before this, any app on the phone could read the "driving yes/no" flag, which tells another app when you are on the move. The flag is now protected by a signature-level permission, so only apps signed with the same key as Hub can read it. The flag itself is unchanged: yes/no and a time, no location, nothing written, never sent over the network. Hub's own screens are not affected. Limit: if a future Netra app is signed with a different key it cannot read the flag until that is changed on purpose. No other app in this repo reads it today. No new library or server.

## Region guard (v1.4.3)

The app refuses to open when the phone's SIM or network country is on a built-in block list (PK, BD, AF, CN, KP). The check runs only on the phone, uses no permission, no network call and no IP lookup, and nothing is stored or sent. With no signal, or an Indian SIM, it never blocks. It is a deterrent, not foolproof: removing the SIM or changing the language region bypasses it.

## Parking spot and siren stop (v1.4.0)

New optional parking spot, OFF by default. When on, it saves one location (latitude, longitude, time) in the app's private storage when driving ends, replacing the previous one. No history, never sent anywhere, Clear deletes it, switching it off deletes it. Uses the location permission the app already asks for. Also: the siren now has a STOP SIREN / CANCEL button on the SOS screen. The Driving flag for other Netra apps (v1.3.0) now also counts the speed-based driving mode. No new permission, library or server.

## Driving flag for other Netra apps (v1.3.0)

Hub now publishes one yes/no "driving" flag and the time it was set, through a read-only content provider on this phone. Any app on the phone can read it. It contains no location, no name and nothing else, and it cannot be written to. It is never sent over the network. If Hub stops updating it, it reads as "not driving" after 2 minutes. No new permission, library or server.

## Developer PIN fix and stronger PIN storage (v1.1.23)

Fixed: after a user changed the Developer Mode PIN from the default, the default PIN 000000 could still be accepted. The app treated "PIN not read from the phone yet" the same as "no custom PIN saved". Now nothing is accepted until the saved PIN has been read, the default works only while no own PIN has been saved, and once an own PIN is saved only that PIN works. The PIN screen now shows the default PIN (000000, 6 digits) and says you will be asked to set your own right after entering it.

Stronger storage: new PINs are stored with 310,000 rounds instead of 12,000. PINs saved earlier keep working and are re-saved with the stronger setting the next time they are entered correctly. A reset with the recovery key still brings back the default PIN on purpose. No new permission, library or server.

## Log privacy (v1.1.22)

Log lines no longer include personal content: the Hub no longer writes spoken text to the device log, and KBC no longer writes the player's name. Nothing else changes. No new permission, library or server.

## Report delivery fix (v1.1.21)

Crash and feedback reports from the app now send the website address as the origin header, so the report service accepts them. Reports that could not be delivered before (they stay saved on the phone and retry at the next start) will now go through. Report contents, the stored file and the opt-in text are unchanged. No new permission, library or server.

## Installer file cleanup (v1.1.19)

When the app comes back to the front it also deletes installer files older than one hour from its private cache folder. Nothing outside the app's own folder is touched. No new permission, library or network call.

## Update download progress (v1.1.18)

The in-app update now shows real progress: percent downloaded counting up, megabytes done of total, a bar, and a time left that never goes up. After the download the installer file can be installed again from the card if the installer was closed, and it is deleted after install or at the next start. Only the app's private cache folder is used. No new permission, library or network call.

## Update alert (v1.1.17)

A background check every 6 hours reads the same public latest.json the in-app updater already uses and, when a newer version exists, shows one notification. Tapping it runs the existing verified download (size and SHA-256 checked) and opens the Android installer. Adds the AndroidX WorkManager library (2.10.0) where it was not already present. No new permission, server or stored personal data.

## Plain failure messages (v1.1.16)
- When an update download fails, the app now shows one plain sentence (for example "No internet, or the server did not answer") instead of the raw system text such as "Unable to resolve host". The app's own messages (checksum, allow installs) are unchanged.
- No new permission, library or network call.

## UI standard check (v1.1.15)
- Two internal words on screen are replaced by plain text: Security tab "Security Engine Health" now says "OK - running", and the Service screen line now says "Background self-check is running".
- A new automatic check (scripts/ui-standard-check.sh, run by the ui-standard workflow on every pull request) fails the build if an internal word (for example TRUTH ENGINE, IBRS2, IDHMSE) or a filter chip or tab label that can wrap shows up on screen. CONTRIBUTING.md holds the Netra UI standard checklist.
- No new permission, library or network call. Nothing about the user is read or sent.

## UI cleanup, step 3 (v1.1.14)
- Home: the Microphone row is removed from the privacy scanner (Hub does not use the microphone, so the row only ever said "Unavailable"). Scanner rows keep the label on one line and let the value wrap on the right. The dark "Absolute Truth" footer and the "TRUTH ENGINE VALIDATED" badge are removed: they repeated the sensor count and claimed a validation the app does not perform. The score card now reads "Safety score" with a plain sentence.
- Sensors, Service and Logs: the "Name > Tab" line above the tabs is removed (the selected tab already shows), tab labels stay on one line, the Service tabs scroll sideways instead of being cut off.
- Settings: the "Settings > Global Search" line is removed, the search field is one slim line, and internal codes (ISPPE, IBRS2, IDHMSE) are replaced by plain words.
- Home action row (SOS setup, Share location, Travel Checking): labels cannot wrap.
- No new permission, library or network call.

## Standard header and scrolling (v1.1.1)
The header is the Netra standard: 56 dp, only the app name, the installed version and the device date/time. The SOS setup, Share location and Travel Checking buttons moved to the top of the scrolling Dashboard; the bottom bar stays fixed. No new permission, network call or library.

## Header cleanup, first UI step (v1.1.0)
- The header now shows the installed version (read from Android's package info; "Unavailable" if Android does not return it). The always-green dot and the account icon are removed: nothing was behind them (the dot never changed with any real state, and Hub has no accounts). The header button "SOS" is renamed "SOS setup" because it opens the SOS settings screen and does not send an SOS. The SOS tab icon is now a warning sign instead of a camera. No permission, network call or library change. Further UI steps follow in later releases.
- Rule for all Netra apps: every datum shown must be backed by real evidence; when none is available the app shows "Unavailable" and nothing is made up.

## In-app update (new)

What it does: on app open, at most once a day, the app asks `https://github.com/prayagi-store-and-services/netra-hub/releases/latest/download/latest.json` whether a newer version exists. If yes, it shows the version and what changed, and the user taps Update. The app downloads `app-release.apk` from the same release, checks its size and SHA-256 against latest.json, and only then opens the Android package installer. The user confirms with one system tap.

What is protected:
- Only https://github.com/prayagi-store-and-services/netra-hub/ release URLs are used; the download URL is built from the release tag, never taken from the metadata.
- The file is deleted and not installed if its size or SHA-256 does not match.
- Android installs the update only if it is signed with the same key as the installed app (the Netra release key), so a different signer is rejected by the system.
- Nothing about the user or device is sent: the check is a plain download of a small public file. No account, no ID, no location.
- Permission added: REQUEST_INSTALL_PACKAGES (needed to open the installer; the user must also allow installs from this app once in Android settings). A separate FileProvider (`<applicationId>.updates`) exposes only the app cache folder `updates/`.

Release process: the Signed Release workflow publishes `app-release.apk`, a named copy, and `latest.json` together. The tag must equal `v` plus the versionName in app/build.gradle.kts.

Limits: Android does not allow silent installs, so the user always taps once. Versions installed before this feature existed cannot update themselves and must be installed manually once.

## Background readiness fix buttons (new in 1.0.2)

What it does: in Settings, the "Battery optimization and readiness" card now shows a button for each item that is not yet OK. Each button only opens a real Android screen or pop-up where the user decides; the app never turns these on by itself.
- Allow unrestricted battery: the standard Android pop-up (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
- Allow exact alarms: Android's "Alarms and reminders" screen for this app (ACTION_REQUEST_SCHEDULE_EXACT_ALARM).
- Allow notifications: the Android notification pop-up, with a link to the app's notification settings.
- Autostart (realme, Oppo, OnePlus, Xiaomi, Vivo, Huawei): opens the maker's own autostart screen when this phone has one, else the app's settings page. Android gives apps no way to read the Autostart switch, so the app shows an instruction and does not claim it is on or off.

Permissions added: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and SCHEDULE_EXACT_ALARM. Neither sends any data. The status lines are re-read every time the user returns from those screens.

## Speed gate for background sensors (new in 1.0.2)

The always-on safety sensors now listen only while the phone moves at 30 km/h or faster (Settings: "Safety sensors only above 30 km/h", on by default). They stop 30 seconds after the speed falls below 30. Speed comes from Android's location service (balanced power, one reading every 10 seconds); a location older than 30 seconds, or no speed from the phone, counts as unavailable and keeps the gate closed. Nothing is sent anywhere: speed is used on the device only. Limit: balanced-power location can report speed late or not at all indoors or in weak GPS, so the sensors may start a few seconds after a vehicle gets up to speed.

## Anonymous usage count (new in 1.0.3)

Once per UTC day (and once per month) the app adds 1 to a public counter in Firestore (`netra_active/netra-hub_<yyyyMMdd>` and `_<yyyyMM>`), so the Netra Eco website can show approximate active users. The request contains only the counter document name and "increment by 1". No device ID, install ID, account, location or app data is sent, and the app keeps no ID for this. A local flag stops repeats on the same day; a failed send is retried at the next open. It is on by default and can be turned off with the "Share anonymous usage count" switch. Firestore rules allow only creating a counter with value 1 or raising it by exactly 1; counters are public to read. Anyone could in theory script extra +1s, so the number is approximate, and reinstalling or clearing data can count one person twice.

## Automatic crash reports
- If the app crashes, it saves a short report on the device. The next time the app opens, it sends that report by itself (no button, no question) and then deletes it. If the send fails, it is kept and retried at the next start.
- Version 1.1.2: Settings has a manual "Send crash report" button. It shows the exact text first (app, app version, phone model, Android version, the last crash trace) and sends only if the user taps Send; "Share instead" lets the user pick any app. If no crash is saved it says Unavailable. The automatic send now counts as sent only when the forwarding service confirms it; before, an HTTP 200 reply was enough, so a report could be deleted without any email being sent.
- The report contains only: the app name, phone model, Android version, app version, and the crash stack trace (exception class names and code locations; exception messages are dropped on purpose).
- It contains no name, email, location, files, contacts, device IDs or usage history.
- It is sent through the same form pipeline as the website forms (FormSubmit) to the developer's email.

## Installer file cleanup
After an in-app update installs, the app restarts and, on start, deletes every downloaded installer file from its cache folder (`cache/updates/`). Nothing from the update is left in storage. A new download also removes older files first. If the user cancels the install, the file is removed the next time the app starts.

## Manual update check
- Settings has a "Check for update" button. It reads the same latest.json as the automatic check, shows "You are on the latest version", "Update available: vX" or "Unavailable: could not check", and never installs anything without the user tapping Update and confirming in the Android installer. The file is still checked for size and SHA-256 first.

## Travel Checking: hidden camera check and Wi-Fi device scan (added in 1.0.7)
- New "Travel Checking" mode, opened by the button in the app header (always visible, never starts by itself). It is a 5-step guide with:  a magnetic field meter, a torch "lens finder" guide, an infrared check guide (opens the phone's own camera app), a Wi-Fi device scan and a step-by-step room checklist.
- Battery: nothing runs in the background. The magnetic meter reads the sensor only while step 1 is on screen, the torch is on only while step 2 is on screen (it is switched off when you leave), and the Wi-Fi scan runs only when you tap Scan.
- It gives hints only. It cannot prove that a room has no hidden camera, and the screen says so. A camera on another network, on mobile data or recording to a card does not appear in the scan.
- Wi-Fi device scan: starts only when you tap "Scan this Wi-Fi network". It looks only at the phone's own private network (10.x, 172.16-31.x, 192.168.x), never more than 254 addresses, and refuses public or unusual addresses. For each address it makes short connection attempts to nine common ports (80, 443, 554, 8080, 8554, 8000, 37777, 34567, 22) and a ping, then reads the device name the network gives. It does not log in to anything, does not send data, and keeps no results: the list is gone when you leave the screen.
- Android does not let apps read other devices' MAC addresses, so brand and MAC are shown as Unavailable. "Possible camera" appears only when a camera-style port answers or the device name contains a word like cam, ipc, dvr or nvr. It is a hint, not proof.
- Torch: uses the phone's torch only while you press the button, no camera permission. Magnetic meter: reads the phone's magnetic sensor on screen only, nothing is recorded.
- Permissions: none added. The scan uses INTERNET and ACCESS_NETWORK_STATE, which this app already had. The torch needs no permission. No new library.

## Share location (added in 1.0.8)
- A "Share location" button in the app header. It runs only when you tap it: it takes one location fix and opens the Android share sheet with a Google Maps link, the accuracy and the time of the fix. You pick who gets it.
- It is a one-time snapshot, not a live track. The app has no server and nothing is uploaded by the app. If there is no fix it shows "Location Unavailable" and shares nothing.
- Permissions: none added. It uses the location permission this app already had.
- No new library.

## SOS shake (added in 1.0.9)
- OFF by default. The user turns it on in the SOS screen (header button "SOS"). Switching it on asks for SMS, location and notification permission. If SMS or location is refused it stays OFF.
- While ON, a foreground service (type location) keeps a visible notification and reads the phone's motion sensor. This uses some battery. Switching it OFF, or the notification's "Turn off" button, stops it fully.
- Three hard shakes within 1.5 seconds start a 10 second countdown with a Cancel button. If not cancelled, one SMS per saved contact is sent with a Google Maps link of the current location, or the words "Location Unavailable" when there is no fix.
- Emergency contacts (up to 5) are stored only on the phone. Nothing is uploaded.
- Limits: SMS needs mobile network and the app cannot confirm delivery. It does not call anyone. After one SOS, shakes are ignored for 60 seconds.
- New permission: SEND_SMS (sensitive; requested only when SOS is switched on). The telephony hardware feature is declared optional so tablets are still supported. Foreground service type location uses the location permission this app already had. No new library.

## Emergency profile in the SOS message (added in 1.0.10)

- Optional name, date of birth and blood group, typed in the SOS screen and kept only on this phone (the same private app storage as the emergency contacts). They are never uploaded or shared by the app.
- They leave the phone in one way only: written into the SOS SMS that you triggered yourself with the shake and did not cancel. Empty fields are left out. Age is worked out from the date of birth at that moment. The SMS also adds the phone battery percent when Android gives it.
- Honest limits: nothing here is checked against any record, it is exactly what you typed. SMS is plain text on the mobile network, so anyone who can read your contacts' SMS can read it. No new permission and no new library.

## 112 India shortcut (added in 1.0.11)

- A button on the SOS screen opens the official 112 India app (package in.cdac.ners.psa.mobile.android.national), or its Google Play page when it is not installed. It runs only when you tap it.
- The manifest gains a package-visibility entry for that one package so the hub can tell if it is installed. No new permission, no new library, no data is sent to the app or to anyone.
- Honest limits: the hub is not registered with 112 and does not alert 112 itself. Alerts to 112 are sent by the 112 India app, which has its own terms and state coverage.

## Location and backup honesty fixes (also in 1.0.11)

- Found in an audit: when the phone had no location at all, the location code made up a fix in New York (40.7128, -74.0060, accuracy 50 m, stamped "now"). SOS and Share location could therefore have sent a wrong place. It also accepted old cached fixes of any age.
- Fixed: the made-up fix is removed. A location older than 15 minutes (or stamped in the future) is never used. With no recent real fix the app says "Location unavailable", and the SOS SMS says "Location Unavailable" instead of a map link. The sensor engine's built-in New York default is now 0,0 (treated as no fix).
- Fixed: backups no longer fall back to plain Base64 when the Android Keystore fails. Base64 is encoding, not encryption. Creating a backup now fails and says so; restoring a file that cannot be decrypted fails and says so. Older backup files written by the old fallback can no longer be restored.
- Honest limits: nothing in the app claims an encrypted database. Only backup files are encrypted (AES-GCM with a Keystore key). Not tested on a device. No new permission or library.

## Compliance audit fixes (1.0.12)

Everything below was found by auditing the code against what the app says it does. Nothing here is tested on a device.

- Warnings: the app used to ask a third-party weather API (placeholder key "public") about the phone's coordinates and call the answer "verified". That call is removed. No coordinates leave the phone for warnings. The warning card now says that no official alert feed is connected. The app does not claim "no active warnings".
- Encryption claims: the "AES-256 log encryption" switch changed nothing and defaulted to on. It is removed and replaced by a plain statement that logs are not encrypted by the app. "Export encrypted backup" wrote plain JSON; it is renamed "Export Logs (plain JSON)" and the file names no longer say "encrypted". The old backup engine wrote only a few metadata fields and a restore that restored nothing; both now report "not available".
- Integrity: the system-integrity check always said "secure". It now says "not verified". Backup format validation now needs valid Base64 of at least an IV plus data instead of any text containing "{".
- Security score: items the app cannot read from Android (Find My Device, Play Protect, theft lock, offline lock, trusted devices, remote lock, network lock, power-menu lock, factory-reset protection, SIM lock, emergency SOS setting) used to default to "enabled". They now show "unknown" until Android reports otherwise. Device encryption shows "enabled" only when Android reports it.
- Permissions removed because nothing uses them: RECORD_AUDIO (the app never records audio), BODY_SENSORS, ACCESS_BACKGROUND_LOCATION, HIGH_SAMPLING_RATE_SENSORS, USE_BIOMETRIC, USE_FINGERPRINT, ACTIVITY_RECOGNITION, BLUETOOTH_ADMIN. ACTIVITY_RECOGNITION returns if driving detection ships. The app no longer asks for the microphone.
- SOS result wording: the notification now says the SMS was "handed to the phone" and that delivery is not confirmed. Android gives no delivery proof without extra callbacks.
- Left as is, with reason: the Security Hub's manufacturer picker (it only changes which phone-maker tips are shown, and is labelled); the old test-simulation framework (no screen opens it); the usage stats permission (used for app screen time on the health page).

## Self-audit and recovery honesty (1.0.13)

- The background "self-audit" and "recovery" jobs and the service-recovery routine used to log "Self-audit complete", "Recovery Completed" and mark a failed service as running again, while doing nothing. They now log that no checks and no recovery actions exist in this version, and a service that fails its check is shown as stopped, not recovered. Nothing here changes what the safety features (SOS, location share) do.
- Left for the owner to decide: crash reports are sent automatically with no switch (the report holds only app name, phone model, Android and app version, and exception class names with code locations; no name, email, location or device ID). It goes through the formsubmit.co forwarding service to the owner's inbox. A consent switch can be added if wanted.

## Driving detection is not available yet (1.0.14)
The app has no working "driving detected" detection in this version. The dashboard card and the assistant used to show "Idle / Stationary" or "No Drive Detected", which was misleading. They now say drive detection is not available. GPS speed is still shown as measured. A real, battery-aware driving detection is planned as a separate, clearly labelled heuristic.

## Steps today card (version 1.1.3)

- New Home card "Steps today" reads the phone's own step counter sensor, only while the Home screen is visible (no background work, no service). Steps taken while the app was closed are included, because the phone keeps counting; the app only stores a start point for the day.
- Permission added: ACTIVITY_RECOGNITION (Physical activity). Android requires it to read the step counter. It is requested only when you tap "Allow step counting".
- Stored on the device only: the day, the counter start value and the time of the first reading. Nothing about steps is sent anywhere.
- Honest limit: steps before the first reading of the day are not known, so the card says "Counted since <time>" and the real total can be higher. If there is no step sensor or no permission it shows Unavailable.
- Not built yet: age-based daily target from date of birth and the achievement message (planned, next part).

## Daily step target from date of birth (version 1.1.4)

- The Steps today card now asks for a date of birth (optional, typed as DD-MM-YYYY) to pick a daily step target. It is stored on this phone only (local preferences, not backed up because app backup is off) and is never sent anywhere. Without it the target shows Unavailable.
- Targets come only from published reviews: ages 6 to 19 = 12,000 steps/day (Colley et al. 2012, steps equal to 60 minutes of active time; Tudor-Locke et al. 2011 child and adolescent review), ages 20 to 64 = 10,000 steps/day (Tudor-Locke et al. 2011 adult review, "reasonable" for healthy adults). Under 6 and 65 and over: no single evidence-based target in those reviews, so the app shows Unavailable instead of inventing one. A general guide, not medical advice.
- When today's real steps reach the target the card shows a "Target reached" message. No new permission, no network call, no new library.

## Home screen widget (version 1.1.5)

- The existing Hub widget now shows, in one card: battery temperature and charging state (from Android's battery broadcast, read when the widget is drawn), steps today and the age based daily target (the values this app saved the last time it read the step counter). A value that is not known shows "Unavailable". The old "STATUS: SAFE" text is replaced by the plain fact "Below 40C" or "HIGH TEMP (40C or more)".
- The widget never reads the step sensor and has no timer or background work of its own. The Steps card asks the widget to redraw at most once every 30 seconds while the app is open. The card says "Steps update when Hub is open".
- No new permission, network call or library. Tapping the widget opens the app.

## Earthquakes near you (version 1.1.9)
The Safety screen has an "Earthquakes near you" card. Only when the user taps "Check now", Hub reads the phone location once (existing location permission) and asks the USGS public feed (https://earthquake.usgs.gov/fdsnws/event/1/query, no key) for earthquakes of magnitude 2.5 or more within 200 km in the last 7 days. The request carries the exact latitude and longitude of the phone and nothing else (the feed provider sees the phone IP address as with any web request); no ID is sent and nothing is stored. It is not an official warning and the card says so: USGS may miss smaller quakes in India. If location or the feed cannot be read it shows Unavailable. Nothing runs in the background, so there is no battery cost. No new permission and no new library.

## Automatic earthquake alerts (version 1.1.10)
Hub now checks for earthquakes by itself, without opening the card. Android WorkManager runs one small job about every 30 minutes when the phone has a network (Android may delay it in Doze or battery saver). The job reads the last position the phone knows with the existing location permission (nothing is invented; with no recent position it records "Unavailable" and sends nothing), asks the USGS public feed (https://earthquake.usgs.gov/fdsnws/event/1/query, no key) for earthquakes of magnitude 2.5 or more within 200 km, and shows one notification for new quakes from the last 24 hours. The request carries the latitude and longitude of the phone and nothing else. Notified quake ids and the last check result are stored only on the phone (SharedPreferences "quake_watch"); no account or ID is sent. This is not an official warning: USGS may miss smaller quakes in India and publishes after a delay. If notifications are off the job records "Unavailable: notifications are off". No new permission and no new library. This replaces the 1.1.9 "no background work" statement above for this feature only.

## Automatic official alerts, NDMA SACHET (version 1.1.11)
About every 30 minutes when the phone has a network (Android may delay it), Hub downloads the public NDMA SACHET alert list (https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml, no key) and the alert files it has not read yet (CAP XML from the same site). Nothing about the user is sent: these are plain downloads, the site sees the phone IP address as with any web request. The phone's own state, district and locality (from the existing location permission and the phone's geocoder) are compared on the phone with the area text of each alert. The alert list gives area names, not map coordinates (the map-shape files are blocked for apps), so the match is by name only: an alert is shown only if it is not expired, is Severe or Extreme, and its area text names the phone's district or locality, or the issuing state body names the phone's state. Alerts written with abbreviations may be missed. The notification says "NDMA SACHET, official", shows the alert's own area text and says it was matched by area name. Read alert ids and the last check result are stored only on the phone (SharedPreferences "sachet_watch"). If there is no recent location, no network, the feed fails or notifications are off, the Safety card says Unavailable. No new permission and no new library.

## Cleaner screens, first step (version 1.1.12)
- The big fixed block under the header (location card with a second clock, session timer and a security message) no longer sits on every tab. Only the 56 dp header and the bottom bar stay fixed. An official warning that applies to your place is still pinned on every tab.
- The Home tab shows the location card and the one-line security message at the top of its scrolling list. The date and time are in the header only. The session timer was removed.
- Bottom bar names are shorter (Home, Sensors, Graph, Logs, Service, Settings) and stay on one line.
- No new permission, library or network call.

## Cleaner screens, second step (version 1.1.13)
- Live Graph uses the same light colours as the rest of the app. The second title block ("NETRA AI, HUMAN SAFETY SYSTEM, CORE_ENGINE") is replaced by one line, "Live sensor graph", and the empty-state text reads "Waiting for sensor data".
- Service Manager rows no longer show the internal "Score weight" number.
- History and Logs no longer fills with "WATCHDOG_CHECK ... Event/Passive" entries (the check still runs, it just does not write a log line for modules that need no heartbeat).
- Settings no longer shows a developer note about a removed switch; it says only that event logs are kept in the app's private storage and are not encrypted by the app.
- No new permission, library or network call.


2026-10-15 family attribution: 1.4.14 adds " | Netra Ecosystem" immediately after the visible version. Production header extracted for 360dp rendering; no permissions, telemetry, signing, network or data-path changes. Not a security vulnerability patch. Phone testing remains pending.


2026-10-15 earthquake filters, 1.4.15/code58: device-local on/off, radius100/200/250/500/1000km and minimum magnitude0..5.5 (0.5 steps). Existing200km/M2.5 defaults preserved. Shared filters apply to foreground USGS results and automatic worker. Off cancels periodic work and in-flight checks re-read off before notification. No new permissions, server credentials or backend writes. Existing fallback requests coordinates from USGS, not the unbuilt coarse-topic server-push design. Android scheduling and feed completeness remain limited; not early warning or guaranteed delivery. Unit and actual control/dialog PNG checks required; physical phone verification pending.

2026-10-16 build-classpath security (1.4.16): the Gradle build classpath (CI and release signing) now forces org.bouncycastle:bcprov-jdk18on 1.85 and org.apache.httpcomponents:httpclient 4.5.14 instead of the 1.79 / 4.5.6 pinned by the Android build plugin. These libraries run only during the build; they are never packaged into the app. App code, permissions, network behavior and stored data are unchanged. Addresses Dependabot-flagged build-classpath CVEs (critical GHSA-9pwp-9qqc-pr26 and GHSA-574f-3g2m-x479; high GHSA-qp49-qgx5-5m26 and GHSA-p93r-85wp-75v3; medium GHSA-c3fc-8qff-9hwx and GHSA-7r82-7xv7-xcpj). Not an app behavior change; phone testing state unchanged.

2026-10-17 crash fix (1.4.17): thermal listener registration is now serialized. Crash report from a real phone (RMX3471, Android 14): java.util.ConcurrentModificationException inside PowerManager.addThermalStatusListener when two collectors registered thermal listeners at the same time from coroutine threads. The platform listener map is a plain ArrayMap with no internal locking. The app now registers and unregisters the thermal listener only on the main thread, behind one app-level lock, and a failed registration shows an honest "registration failed" status instead of crashing. No permission, network, data-path or behavior change beyond not crashing. CI verified; real-device confirmation pending Deepak's phone test.

2026-10-18 crash-report robustness (1.4.20): the version stamped into a saved crash report now comes from the compile-time version constant instead of a PackageManager lookup. During an actual crash the system package service can be unavailable, which was the one remaining path that could save a report with version "unknown". Model and Android version were already read from process-local fields. Crash reports still contain only app name, version, crash time, phone model, Android version and the sanitized trace; no new data, permission or network change. Reports saved by older versions keep the honest "unknown (crash was saved by an older app version)" label.

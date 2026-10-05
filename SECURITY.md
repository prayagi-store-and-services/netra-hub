# Security notes - netra-hub

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

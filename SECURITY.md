# Security notes - netra-hub

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

# Security notes - netra-hub

## In-app update (new)

What it does: on app open, at most once a day, the app asks `https://github.com/prayagideepak-collab/netra-hub/releases/latest/download/latest.json` whether a newer version exists. If yes, it shows the version and what changed, and the user taps Update. The app downloads `app-release.apk` from the same release, checks its size and SHA-256 against latest.json, and only then opens the Android package installer. The user confirms with one system tap.

What is protected:
- Only https://github.com/prayagideepak-collab/netra-hub/ release URLs are used; the download URL is built from the release tag, never taken from the metadata.
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

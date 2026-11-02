## 1.5.7 - Tablet and smart-class layout

- On wide screens the app no longer stretches edge to edge: every tab stays centred in a comfortable reading column, and the app header is larger for distance viewing. Phone layouts are unchanged.

## 1.5.6 - GPS power saving (opt-in)

- New opt-in "GPS POWER SAVING" switch in Settings. While you are moving, location stays on; when the phone is still, location turns off and wakes for a few seconds every 5 minutes to refresh. Movement is read from the accelerometer; nothing is sent anywhere.
- Works while Hub safety monitoring is active. Android lets no app switch location by itself: this needs the one-time ADB grant of WRITE_SECURE_SETTINGS (the same step automation apps like MacroDroid use). Without the grant the switch explains the setup and nothing changes. Turning the feature off restores your previous location setting.

## 1.5.5 (Beta / testing) - IMD weather reports and warning repair

- Official IMD subdivision report selected on this phone using public area polygons. Current-date Green reports update silently; Yellow, Orange and Red warnings use notification and official-alert TTS on a colour change. No coordinates are sent to IMD.
- CAP reports of all standard severities can match the existing exact-area/validity checks. CAP severity is not guessed to be an IMD colour. Weather announcements use IMD colours once; separate non-weather Severe/Extreme official CAP safety alerts retain per-alert delivery.
- Last-check text no longer claims a complete IMD all-clear or successful delivery from a partial/already-seen feed batch. Reports name their source and scope.
- Checks remain about 30 minutes, subject to Android delays, not instant server push. Subdivision warnings are not every district nowcast or hourly forecast. No physical-phone acceptance claim; keep official IMD alerts enabled.

### Community links

- Settings has the five official Netra social profiles, Netra Hub website and a separate private complaint/feedback form.
- Links open only after a tap in an available external browser. No automatic follow, rating, post, new permission or background network call.
- Private feedback is sent through FormSubmit by the web form. No live form delivery or physical-phone test is claimed.

- Manual crash report wording now names third-party FormSubmit, HTTPS transport and lack of end-to-end encryption. Sending still requires preview and Send. The existing daily/monthly Firebase usage counter remains on by default and can be turned off in Settings.

## 1.5.4 (Beta / testing)
Safety and physical-device verification are incomplete; this is not a replacement for emergency services.
- Automatic newer-version popup and notification, with at most one metadata request per day. Android battery/network limits can delay checks; this is not instant release detection.
- Update opens this app's page in compatible Netra Ecosystem or the Eco website. Hub downloads or installs nothing.
- Removed manual update checking and the unused Install apps permission-panel entry. Install permission remains absent.

## 1.5.3
Newer-version notice and exact Hub update page in compatible Netra Ecosystem or the Eco website. No APK download or install in Hub. Existing legacy-updater retirement is unchanged.

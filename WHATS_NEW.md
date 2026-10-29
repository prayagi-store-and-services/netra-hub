## Unreleased - IMD weather reports and warning repair

- Official IMD subdivision report selected on this phone using public area polygons. Current-date Green reports update silently; Yellow, Orange and Red warnings use notification and official-alert TTS on a colour change. No coordinates are sent to IMD.
- CAP reports of all standard severities can match the existing exact-area/validity checks. CAP severity is not guessed to be an IMD colour. Weather announcements use IMD colours once; separate non-weather Severe/Extreme official CAP safety alerts retain per-alert delivery.
- Last-check text no longer claims a complete IMD all-clear or successful delivery from a partial/already-seen feed batch. Reports name their source and scope.
- Checks remain about 30 minutes, subject to Android delays, not instant server push. Subdivision warnings are not every district nowcast or hourly forecast. No physical-phone acceptance claim; keep official IMD alerts enabled.

## Unreleased - community links

- Settings has the five official Netra social profiles, Netra Hub website and a separate private complaint/feedback form.
- Links open only after a tap in an available external browser. No automatic follow, rating, post, new permission or background network call.
- Private feedback is sent through FormSubmit by the web form. No live form delivery or physical-phone test is claimed.

## 1.5.4 (Beta / testing)
Safety and physical-device verification are incomplete; this is not a replacement for emergency services.
- Automatic newer-version popup and notification, with at most one metadata request per day. Android battery/network limits can delay checks; this is not instant release detection.
- Update opens this app's page in compatible Netra Ecosystem or the Eco website. Hub downloads or installs nothing.
- Removed manual update checking and the unused Install apps permission-panel entry. Install permission remains absent.

## 1.5.3
Newer-version notice and exact Hub update page in compatible Netra Ecosystem or the Eco website. No APK download or install in Hub. Existing legacy-updater retirement is unchanged.

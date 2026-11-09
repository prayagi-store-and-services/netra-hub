# ATIF v3.0 - applicable parts for Netra Hub

ATIF v3.0 (Absolute Truth & Intelligence Framework) is the owner's permanent master rule, issued 9 Nov 2026. Canonical full text: private governance copy (`netra-intruder`, `docs/ATIF-v3.0.md`). This file lists only the parts that apply to THIS app.

## Applicable parts and why

- Part 01, 02: safety and weather information must be accurate before reassuring.
- Part 04, 05: IMD colours and CAP severities are reported as received; severity is never guessed or upgraded.
- Part 06: no invented alerts; a partial or already-seen feed batch never becomes an "all clear".
- Part 09: weather data is time-sensitive; checks run about every 30 minutes and are labelled as such, not instant push.
- Part 14: subdivision warnings are not every-district nowcasts or hourly forecasts; this limit stays visible.
- Part 15 (High-Stakes Protection, public safety): official IMD/CAP sources named; users are told to keep official alerts enabled.
- Part 16: no coordinates are sent to IMD; GPS power saving is opt-in and local.
- Part 17, 20: reports name their source and scope.

## Data this app shows, and its status

- IMD subdivision reports: official feed, matched by public area polygons - VERIFIED source, delayed by design.
- CAP safety alerts: delivered per alert for Severe/Extreme official alerts.
- Location features: on-device; nothing sent anywhere.

## Standing rule

Any change to alert logic keeps the named-source, named-scope wording. No claim of complete coverage, instant delivery or physical-phone acceptance without the matching evidence.

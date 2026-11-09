# Netra Hub

On-device human-safety app: driving detection, official weather alerts (NDMA SACHET + IMD), USGS earthquake watch, SOS (shake + optional crash alert), device safety and system health.

## What it does and does not do

- All detection runs on the phone. Location is used for local alert matching; the global USGS day feed is fetched without sending coordinates (the manual "Check now" button does send them to USGS, and says so).
- Weather alerts come only from NDMA SACHET and IMD. When a feed cannot be read, the app says "unavailable", keeps the last verified report until it expires, and never shows a false all-clear.
- Earthquake info is USGS data reported after events. It is not earthquake prediction and not an official early-warning system.
- Driving detection uses GPS speed only. The phone cannot tell whether you are driving or are a passenger.
- SOS sends SMS only after a visible countdown that can be cancelled, and only to contacts you saved. Nothing is sent from a single shake or a single impact reading.
- Battery temperature is the battery's own broadcast value, not the exact internal temperature of the phone. No temperature is guaranteed.

## Permissions (why each exists)

- Location (fine/background): driving detection and area matching for alerts.
- SMS: SOS messages only, after the countdown.
- Notifications: alerts and the SOS countdown.
- Activity recognition: step counting. Camera: camera self-check screen.
- Exact alarm, battery-optimization exemption, boot receiver: scheduled safety checks.
- Usage stats: optional digital-wellness screen. Bluetooth: headset status display.
- WRITE_SECURE_SETTINGS is optional and never requested at runtime; it only enables the opt-in GPS power-saving toggle via a one-time ADB grant.

## Build and test

Standard Android Gradle project (`gradle testDebugUnitTest` for unit tests). CI runs the same unit tests on every pull request. Instrumentation tests require a device or emulator and are not part of CI.

## Audit log

See docs/ATIF-AUDIT.md for the dated verified/not-verified audit entries.

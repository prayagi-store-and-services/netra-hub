# ATIF audit log - netra-hub

Newest first. Failures and NOT VERIFIED items first, per the owner's 9 Nov 2026 rule.

## 9 Nov 2026 (baseline audit)

- NOT VERIFIED: physical-phone acceptance; IMD feed timeliness under Android delays.
- VERIFIED: IMD/CAP source+scope labelling; no all-clear from partial feeds; ~30-min check cadence disclosed; GPS opt-in local-only.
- Method: README/WHATS_NEW/strings read on current main; grep sweep for stats, superlatives and guarantee wording; GitHub Releases API for version facts. No physical-device testing was performed for any item above.

## 9 Nov 2026 (safety & reliability audit, branch fix/safety-reliability-audit-2026-11)

- FIXED + TESTED (unit): cached official alerts were deleted on SACHET/IMD feed failure, on unverified location and on unexpected errors (SachetWatchWorker). Cached reports now survive outages and expire on their own validity.
- FIXED: a 15-minute OfficialWarningWorker ran a manager with no alert feed connected and always wrote "no official alert feed", contradicting the live SACHET/IMD path. Its scheduling is retired; pending work is cancelled on upgrade.
- FIXED: FLAG_KEEP_SCREEN_ON was set unconditionally ("24/7"). It is now a user setting, default off.
- CHANGED: location sampling adapts to driving mode (5 s driving / 15 s stationary). The stationary interval is capped at the speed gate's evidence gap so driving detection cannot silently stop working.
- VERIFIED (code): driving rules match their stated values (15 km/h threshold, 10 s sustained-open with >=3 samples, 90 s close, 5 min blind-spell close); SOS shake needs 3 shakes in 1.5 s plus a 10 s cancel countdown; crash alert needs driving mode + 4 g + 15 s cancel countdown; quake worker already separates feed-unavailable from no-quake.
- NOT VERIFIED: battery improvement from adaptive sampling (no device/emulator measurement); crash-detection false positive/negative rates (no real-world data); instrumentation tests (not run in this audit).

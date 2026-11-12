# Hub UI redesign groundwork (owner series 9 Nov 2026 16:49-16:52)

Reference: /downloads/image-fc4ed414.jpg (10 screens). Acceptance: 11-point check, draft PR, no merge, real data only, screenshots before any release.

## Current state (verified)
- Compose single-activity app. Theme: ui/theme/Color.kt is a LIGHT "Bento" palette (bg 0xFFF7FBF4) with Cyber*/NetraDark* aliases mapped onto the same light values. Reference is DARK NAVY - this is a full palette + component restyle, not a tweak.
- Navigation: MainScreen.kt holds selectedTab (NavigationTab) + overlay booleans; no Navigation component. Bottom nav must become Home/Alerts/Reports/Settings per reference.
- RedesignGate.kt: date gate for the Oct redesign; not a blocker.
- Existing tests that may break on visual change: screenshot/layout tests (EcosystemHeaderScreenshotTest, QuakeFilterScreenshotTest, LiveGraphScreenTest, StatusRowLayoutTest, AdaptiveLayoutTest, AlertTestButtonsTest, BrandTest). Update with evidence, do not delete to pass.

## Work plan (order)
1. Design-system module: dark navy palette (bg ~#0A0E1A, cards ~#141A2E, cyan/blue accents, green/amber/red semantics), typography, shapes, spacing - new values, keep alias names stable where possible.
2. Splash, Home dashboard (6 real-status cards + bottom nav), then per-screen restyle: Driving, Weather, Earthquake, SOS, Device Safety, Alerts, Reports, Settings.
3. States per screen: loading/empty/offline/error/unavailable; SOURCE_UNAVAILABLE != NO_ACTIVE_ALERT.
4. Screenshots on emulator/device if available - otherwise NOT VERIFIED for pixel match.
5. Draft PR; 11-point report each marked met / not met / not verified.

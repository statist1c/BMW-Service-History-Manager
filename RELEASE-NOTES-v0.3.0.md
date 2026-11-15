# v0.3.0

## Fixed app updates
- GitHub releases marked pre-release are now included by default. Previously all published previews were skipped, preventing in-app update discovery.
- Added **Include preview releases** in Settings. Turn it off to receive stable releases only.
- Continues to find the newest eligible version with exactly one APK, regardless of its filename. Drafts, same versions and downgrades remain excluded.
- Reuses an already downloaded and verified APK when continuing installation, including after granting Android's install permission.
- Preview status is shown when reviewing an update. SHA-256, package identity, signing certificate and version checks remain enabled.

## Validation
- All 48 Android checks passed, including live GitHub release discovery and handing a verified newer preview APK to Android's update installer. The fixture installer was cancelled without changing the test installation.
- Signed release build and Android lint passed.

## Installation
Install v0.3.0 manually once if your existing version cannot discover preview releases. Future newer releases can then be found through Settings → Check for updates with Include preview releases enabled. Use the plain release tag **v0.3.0**; this build has no beta suffix.

Vehicle features are unchanged. MGU / iDrive 7 and 8 operations remain experimental and require real-vehicle validation.

# v0.2.1

## New app icon
- New BMW-inspired service icon with M-inspired background stripes, a polished chrome wrench and wider tyres.
- Removed the black outer corners for a clean Android launcher appearance.
- Added adaptive and round launcher icons, plus a monochrome variant for themed icons.

## App updates
- Uses the same filename-independent APK discovery as the MGU Root Companion app.
- Finds the newest newer stable version with exactly one APK attached, including versioned or renamed filenames.
- Keeps SHA-256, package, signing certificate and installed-version verification.

## Validation
- Signed release build and Android lint passed.
- All 28 Android checks passed, including updater filename and release selection checks.
- Successfully installed over the previous signed version on an Android emulator; launcher appearance checked.

## MGU / iDrive 7 and 8
MGU service-history operations remain experimental and have not been validated on a real vehicle. This release changes the icon; the additional identification improvements under investigation are not included. Local head-unit history editing does not register records in BMW's central service history. Native history read-back and backup remain unavailable.

## Release assets
Use `BMW-Service-Manager-v0.2.1.apk` as the versioned APK. Upload exactly one APK; no legacy-name duplicate is needed with this version. Users on v0.2.0 or earlier can install v0.2.1 manually to obtain the filename-independent updater.

Upstream credits and the PolyForm Noncommercial license are preserved.

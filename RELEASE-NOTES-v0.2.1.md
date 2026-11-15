# v0.2.1

## New app icon
- New BMW-inspired service icon with M-inspired background stripes, a polished chrome wrench and wider tyres.
- Removed the black outer corners for a clean Android launcher appearance.
- Added adaptive and round launcher icons, plus a monochrome variant for themed icons.

## Validation
- Signed release build and Android lint passed.
- Successfully installed over the previous signed version on an Android emulator; launcher appearance checked.

## MGU / iDrive 7 and 8
MGU service-history operations remain experimental and have not been validated on a real vehicle. This release changes the icon; the additional identification improvements under investigation are not included. Local head-unit history editing does not register records in BMW's central service history. Native history read-back and backup remain unavailable.

## Release assets
Use `BMW-Service-Manager-v0.2.1.apk` as the versioned APK. Also upload an identical copy named `BMW-Service-Manager.apk` so existing in-app updaters can find it. Both must be from the supplied signed build.

Upstream credits and the PolyForm Noncommercial license are preserved.

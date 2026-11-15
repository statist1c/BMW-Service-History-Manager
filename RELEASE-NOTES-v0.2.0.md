# BMW Service History Manager v0.2.0

## New features

* **Write selected record:** preview and add one entry without erasing existing vehicle history. Duplicate entries remain possible if that service is already stored in the head unit.
* **Independent head-unit controls:** restart the head unit or erase its service history without writing replacement records. Both require vehicle checks and two confirmations; erase also requires backup acknowledgement.
* **Head-unit identification:** read seven standard ECU identity fields and display supported values and raw responses.
* **Native date picker:** choose a service date from the calendar while retaining manual ISO date entry.
* **EDIABAS argument editor:** add, edit and copy validated service-record arguments locally. Arbitrary diagnostic jobs cannot be executed from this editor.
* **Experimental MGU profile:** service-history add/erase/restart implemented using inspected diagnostic definitions, with explicit MGU acknowledgement and a head-unit identity read tied to the current vehicle/network session.

## Fixes and safeguards

* Corrected routine result interpretation: MGU/NBT success is `0`; result `1` is a memory failure. CIC uses its separate success value of `1`.
* Responses missing a routine status stop the operation as unverified.
* Unknown-family changes stay disabled. CBS resets on MGU-equipped vehicles remain disabled pending separate cluster validation.
* Retained aligned bottom navigation, existing themes, JSON/TST interchange, VIN checks, backup acknowledgement and GitHub update checks.

## Compatibility and validation

Android 8.0 or newer. Signed with the same identity as earlier releases; installs over v0.1.x. VersionName `0.2.0`, versionCode `2000`.

Builds/lint and 100 combined core/feature/Android checks passed. No real BMW was available: MGU support is **experimental, not vehicle-validated**. Firmware permissions or sessions may differ; refusals are reported without bypassing security or automatically retrying writes. No history read-back, rollback or BMW backend registration is provided. Photograph current iDrive history and export records before changes. See `docs/MGU-SUPPORT.md`.

## License

PolyForm Noncommercial License 1.0.0; upstream credits and required notices retained. No proprietary PRG files, Windows EDIABAS runtime or HU-ServiceManager code/assets are bundled. English UI only.

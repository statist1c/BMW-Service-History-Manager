# BMW Service History Manager for Android

Native Android adaptation of [devttys1/bmw-service-manager](https://github.com/devttys1/bmw-service-manager), with the rounded cards, line icons, bottom navigation, and light/dark/system themes used by the user's MGU companion apps.

**Version:** 0.2.1 · Android 8.0+ · universal APK, no bundled native libraries. Independent project; no BMW affiliation. Vehicle operations, including MGU history support, are experimental and have **not** been validated on a BMW from Android.

## Install

Install the signed `BMW-Service-Manager-v0.2.1.apk` supplied with this project. Android may ask you to allow installation from the app used to open the file. No root is required on the phone.

## Features

| Feature | Status in this Android version |
| --- | --- |
| Create/edit/delete genuine service records | Implemented locally |
| 1–13 items per record, statuses, unsigned distance/time, correction counter, dealer number | Implemented; matches upstream wire layout |
| Mileage sorting and move up/down ordering | Implemented |
| Upstream/Stampbook JSON and Toolset32 TST import/export | Implemented; import replaces the local workspace only after confirmation |
| Local autosave and Android file picker exports | Implemented; private storage, backup/transfer disabled |
| Exact UDS request preview | Implemented offline |
| ENET discovery and gateway identity/VIN | Experimental native UDP transport |
| CBS read from cluster slots 0x4101–0x417F | Experimental native HSFZ transport |
| Service-history write / optional erase and restart | Experimental; explicit supported-family selection, identity check, backup acknowledgement, preview and two confirmations required |
| CBS reset | Experimental; completed-service confirmation, identity and supported-family selection required |
| Write selected record | Adds one previewed record, never erases existing history; duplicate entries remain possible |
| Standalone head-unit restart / erase history | Implemented with identity checks and two confirmations; erase requires backup acknowledgement |
| Calendar date picker | Implemented alongside manual ISO date entry |
| EDIABAS argument editor | Add/edit/copy validated arguments locally; does not execute arbitrary jobs |
| Head-unit identification | Reads seven standard identification DIDs from address 0x63; displays available text/raw data |
| MGU service-history add / erase / restart | Experimental profile based on inspected diagnostic definitions; explicit MGU acknowledgement and a scoped head-unit identification read required |
| CBS reset on MGU-equipped vehicles | Disabled pending separate cluster validation; read-only CBS remains experimental |
| Read back existing head-unit service history | Not supported by the known upstream protocol |
| Cloud/digital BMW service-book updates | Not supported |
| QR/Stampbook transfer, 3D drive replay, DID probe tools | Not ported; use upstream desktop app |

Upstream lists **CIC, NBT, NBT Evo, ENTRY and CHAMP2**, with development/testing on an **F48 X1 with NBT Evo**. This Android adaptation adds an experimental **MGU** profile, with evidence and limits in [docs/MGU-SUPPORT.md](docs/MGU-SUPPORT.md). The head-unit family is a user declaration, not automatic hardware detection. Read-only diagnostics can also be refused by unsupported cars. Unknown-family mutations stay disabled.

## Use offline

1. Open **History** and optionally enter your VIN.
2. Add real completed services, or import JSON/TST. Each item can have its own status, remaining distance and remaining months. Unknown numeric item codes are preserved.
3. Sort or reorder as needed. Export JSON before important changes.
4. In **Vehicle**, choose whether a write plan would erase history or restart the head unit, then preview the exact requests. Preview sends nothing.

Dates are ISO `YYYY-MM-DD` with a native calendar picker; mileage is kilometres. Dealer numbers are up to seven printable ASCII characters; leading zeroes are retained. Maximum 2,000 records / 4 MiB input file. The app is English-only. The TST importer's erase flag is intentionally **not** enabled automatically. No sample vehicle data is inserted. Per-record controls provide a selected-record preview/write and a validated EDIABAS argument editor. Raw arguments change only local records until explicitly previewed and confirmed for writing.

## Connect to a vehicle

Use an ENET cable and a compatible USB-C Ethernet adapter, or an ENET Wi-Fi adapter. Select the matching phone network. The app binds its diagnostic sockets to that network; it does not require Internet on the car network. Android/adapter routing and link-local address assignment vary and require testing. This app does not configure static phone IP addresses or implement DHCP.

Enter the actual gateway IPv4 address and HSFZ port (normally 6801), or use discovery. The suggested `169.254.199.99` is only a starting value, not an assumed vehicle address. The MGU SSH IP from other companion apps is not necessarily the diagnostic gateway. Check the vehicle identity and VIN. Discovery selects the first response; verify it before proceeding.

For mutations: select the actual head-unit family, enable experimental changes for that session, and match the workspace VIN if one is set. For MGU, acknowledge experimental support, check vehicle identity, then read head-unit identification on that same connection; at least one identifier must be returned. This is a responsiveness check, not automatic proof of the model or write permissions. History writes additionally require the backup acknowledgement and an exact preview of the current records/options. The app rechecks the gateway identity immediately before sending changes. Standalone restart and erase send no add-record requests; standalone erase requires the backup acknowledgement. MGU CBS resets stay disabled.

Only work while parked, ignition on, with stable vehicle battery power. Head-unit history cannot be read back or backed up by this app: photograph it in iDrive first. Erase is permanent. Writes stop on the first transport/ECU/routine failure and are never automatically retried or rolled back. A failure can leave partial changes. The app checks response service/echo and routine result, but only inspection in iDrive/cluster confirms the actual vehicle outcome.

Keep the app open for vehicle operations. Rotation is handled without restarting the operation. Switching apps, locking the phone, Android killing the process, or losing power/network can interrupt a session. No background vehicle service is claimed. Read CBS again after a reset and verify the instrument cluster.

## GitHub updates

Configured for [statist1c/BMW-Service-History-Manager](https://github.com/statist1c/BMW-Service-History-Manager). **Settings** provides an automatic-check switch, manual check, release review and install.

Checks run on launch when due and approximately daily via Android JobScheduler when the device permits; timing is not guaranteed. Background findings appear in Settings on the next app launch. No account, token, silent installation or automatic APK download is used.

Only newer stable `vX.Y.Z` releases are eligible. The release must contain exactly one `.apk` asset; its filename can include the version or any other name, its size must be 1 byte–16 MiB, its URL must belong to this repository, and GitHub must expose a `sha256:` asset digest. Before install the app verifies downloaded size, SHA-256, package ID, identical signing certificate, higher Android versionCode and a versionName matching the release tag. It refuses mismatched packages/signers and downgrades. SHA-256 comes from the same HTTPS GitHub source as the download metadata; the installed APK's signing certificate provides the additional publisher continuity check. Android always requests installation approval.

See [PUBLISHING.md](PUBLISHING.md) for signing secrets and the optional manual APK-build workflow. APK uploads and release creation remain manual.

## Build

Open this directory in Android Studio, use JDK 17, install Android SDK Platform 35 and accept its SDK licenses. The Gradle 8.9 wrapper and Android Gradle Plugin 8.7.3 are included/configured. SDK/build dependencies download on first build.

```text
bash gradlew assembleDebug lintDebug
bash gradlew assembleDebugAndroidTest
```

On Windows, use `gradlew.bat` instead of `bash gradlew`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. A debug build cannot update the supplied release-signed installation. See publishing instructions to build with the supplied private signing identity or your own identity before the first public release.

For standalone core checks, use JDK 17:

```text
javac -d work/test-classes app/src/main/java/dev/mgu/service/ServiceRecord.java app/src/main/java/dev/mgu/service/Hsfz.java app/src/main/java/dev/mgu/service/Cbs.java app/src/main/java/dev/mgu/service/HeadUnitInfo.java app/src/main/java/dev/mgu/service/HeadUnitProtocol.java tests/CoreTest.java tests/FeatureTest.java
java -cp work/test-classes dev.mgu.service.CoreTest
java -cp work/test-classes dev.mgu.service.FeatureTest
```

The platform-only instrumentation runner is `dev.mgu.service.AppSmokeTest`; run on a dedicated test emulator. It creates and clears this app's local test records. The optional updater-validation test fixtures are documented in `docs/VALIDATION.md`.

## License

**PolyForm Noncommercial License 1.0.0**. This derived Android app is for personal and other permitted noncommercial purposes. The upstream license does not permit use in a paid service business, selling the app, or other anticipated commercial application. Obtain separate permission from the relevant rights holder for commercial use.

Required Notice: Copyright 2026 devttys1 (https://github.com/devttys1/bmw-service-manager)

Distribute [LICENSE.md](LICENSE.md) and [NOTICE.md](NOTICE.md) with the source and retain the in-app license screen. Build-tool notices are in `licenses/`. No upstream 3D models, web fonts or web/runtime libraries are included.

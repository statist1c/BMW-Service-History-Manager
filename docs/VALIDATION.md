# Validation record — updated 16 November 2026

## v0.5.0 and real-car read-only validation

On 16 November 2026, user-provided v0.4.2-beta diagnostics and screen recording confirmed ENET discovery/vehicle identity, complete nine-DID head-unit identification (MGU variant 0F25B0; F101 software fingerprint, serial and VIN returned), and two complete 127-slot CBS scans with seven populated service items. No busy responses or retries occurred in those two runs; automatic busy recovery is covered by simulated tests, not that car recording. APK fingerprints matched the delivered build. Raw logs and recording are kept private and are not bundled in source.

v0.5.0 carries the same read-only protocol implementation with release version/diagnostic heading changes. On the signed v0.5.0 emulator installation, 402 core/protocol checks, 26 diagnostic/export checks, 36 Android storage/failure checks and 49 Android UI checks passed. Debug/release lint passed. Same-key installation upgraded the beta without uninstalling.

Tested and working scope: 2019 BMW 530e G30, iDrive 7, vehicle/head-unit identification and current CBS requirements. Completed iDrive history reading is unsupported. History writing, erase/restart and CBS resets remain unvalidated on this vehicle; MGU CBS resets remain disabled. Earlier statements below are historical validation records.

## v0.4.0

Passed: **40 core + 50 feature + 35 v0.4.0 core checks**, **36 Android storage/failure checks**, and **51 full Android/UI checks** (including the live GitHub and dark-theme screenshot options). The signed-release migration check additionally passed four assertions after a one-check v0.3.0 fixture seed. Debug/release assembly, test APK assembly, `lintDebug` and `lintRelease` passed. Release lint has zero errors; remaining warnings concern localization, legacy UI APIs and drawing allocations.

New checks exercise signed distance extrema/negative multiples and legacy unsigned conversion, future/duplicate/custom-code write guards, exact F150 family matching, F101 software changes over a local TCP double, per-VIN isolation, migration preserving the original file, atomic storage and JSON identity checks, durable intent before sends, timeouts, crash-pending sends, malformed routine replies, partial completion, duplicate guards after edits/reloads and disk failure preventing any send. CIC status 0/1 handling was corrected using the locally inspected diagnostic description; earlier historical descriptions below predate that correction.

On an Android 15 emulator, a release-signed v0.3.0 installation was seeded with a real legacy history file containing a VIN, record and unsigned distance value. The same-key v0.4.0 APK installed over it without uninstalling; the VIN/record migrated, distance retained its four bytes as signed -1, and SHA-256 confirmed the original file remained unchanged. Certificate SHA-256 remains `9f9cc9906c59b39791b443f7b5727fe48ea24e50da7bed8aa54b3bb48b3fa173`. The migrated release UI was visually inspected.

Storage checks use isolated emulator directories. Run the standard instrumentation with `-e focus storage` for the additional journal/workspace cases. The normal runner creates and clears app test records; use a dedicated emulator. `-e migration seed` is only for a release-signed test runner targeting the old v0.3.0 fixture; `-e migration check` verifies the upgraded fixture. Proprietary PRGs, disassembly, keys and APK fixtures are excluded from source delivery. No BMW vehicle was contacted. This is not real-vehicle acceptance or history read-back verification.

## 0.2.0 feature and MGU update

Debug/release builds, Android test APK, `lintDebug` and `lintRelease` passed. **40 core checks + 37 feature checks + 23 Android instrumentation checks passed.** New checks cover strict EDIABAS argument validation, selected-record preview containing exactly one add and no erase request, native argument editing, invalid-argument retention, a native calendar preserving a leap-day date, Unknown-family mutation guards, MGU acknowledgement/identity binding, and the MGU CBS reset block. The read-only identification implementation is exercised against a local HSFZ gateway double: only service 0x22 requests are sent, text/binary values are displayed appropriately, and unavailable DIDs are reported individually. MGU status zero is accepted, memory-error status one is rejected, CIC's distinct success code is handled, missing status is rejected, and unvalidated MGU distance overflow is blocked.

MGU protocol evidence comes from offline inspection of locally installed diagnostic descriptions, not hardware testing. No proprietary diagnostic files or disassembly are included. See `MGU-SUPPORT.md` for exact evidence and limitations. Existing in-app mutation confirmations and first-failure stop behavior remain in use. English only, as requested. The older HU-ServiceManager was compared offline; none of its program code/assets was reused. The release APK's signing identity was verified, and v0.2.0 installed and launched successfully over the release-signed v0.1.1 APK on the emulator.

## 0.1.1 alignment fix

Bottom navigation labels now explicitly fill each tab's width and center their text beneath its icon. Release assembly and `lintRelease` passed. The release-signed v0.1.1 / versionCode 1001 installed successfully over v0.1.0 without uninstalling. The corrected light-theme layout was visually inspected; the four label bounds have equal widths and aligned top/bottom edges. Screenshots were refreshed. Existing protocol and updater checks below describe the original 0.1.0 validation; no vehicle/protocol behavior changed in 0.1.1.

## Initial 0.1.0 checks completed locally

* JDK 17 / Gradle 8.9 / Android Gradle Plugin 8.7.3 / Android SDK 35.
* Debug APK, platform-only Android test APK and signed release APK assembled successfully.
* `lintDebug` and `lintRelease` passed with **0 errors**. Remaining warnings concern English UI strings/localization, UI drawing allocation and legacy platform UI APIs; this preview does not claim translated UI.
* **40 core checks passed**, using the upstream `tests/test_records.py` binary fixture, zero-padded dealer fields, unsigned boundary values, EDIABAS/TST round trips, malformed input rejection, CBS decode/reset ownership and payloads, and a local TCP gateway double exercising ACK, alive-check response, response-pending and rejected response echoes. No vehicle was contacted for these checks.
* **18 Android instrumentation checks passed** on an Android 15 / API 35 emulator, including real native record-dialog interaction, saving, JSON object/array interchange, integer-overflow rejection, exact preview, Unknown/MGU write guard and local persistence. An additional screenshot run passed **19 checks**, including dark-theme recreation.
* Updater metadata fixtures verify newer stable release selection, rejection of prereleases, same/older releases, other-repository APK URLs and missing GitHub SHA-256 digest.
* Local APK fixtures verify acceptance of a newer APK with the same signing identity; rejection of wrong size, wrong SHA-256, another signer, another package, same-version APK and mismatched release tag/versionName. The provider refuses arbitrary file paths.
* Release APK signature verified with Android `apksigner`, APK Signature Scheme v2, RSA 3072-bit. The signed APK was installed and launched on the emulator separately from the debug test build.
* Light history, dark history, vehicle and dark settings screens were captured from the actual app. History/vehicle screens were visually inspected; screenshots are in `docs/screenshots/`.
* The upstream license is preserved in the project and APK assets. Source packaging excludes build outputs, local SDK paths, test APK fixtures and private signing material.

Release signing certificate SHA-256:

```text
9f9cc9906c59b39791b443f7b5727fe48ea24e50da7bed8aa54b3bb48b3fa173
```

## Not established by these checks

* No BMW hardware or actual vehicle was available. ENET adapter behavior, phone routing, ECU responses, supported vehicle coverage, real history writes/erases/restarts and CBS resets remain unvalidated on Android.
* Upstream's F48 NBT Evo testing is upstream evidence, not validation of this Android application. Real-vehicle MGU compatibility is not established. Version 0.2.0 implements experimental MGU history add/erase/restart from inspected protocol definitions; Unknown-family changes and MGU CBS resets remain disabled.
* There is no service-history read-back verification or rollback. An accepted ECU response is not proof that every entry is displayed correctly in iDrive.
* The configured GitHub repository existed but had no published newer APK during implementation. Update selection and APK authentication were tested with local fixtures; an end-to-end Internet update requires a newer public release. The included GitHub workflow has not been run on GitHub by this task and needs the private signing secrets set by the owner.
* Daily background timing is controlled by Android. No silent downloads/installation, push notification service, Google Play integration or authenticated private-repository updater is included.

## Re-run Android tests

Build/install debug and debugAndroidTest APKs on a dedicated emulator, then run:

```text
adb shell am instrument -w dev.mgu.service.test/dev.mgu.service.AppSmokeTest
```

The tests create and clear the app's local records. For optional APK authentication cases, create `files/update-tests/` in the debug app's private storage using `adb shell run-as dev.mgu.service`. Place:

* `valid.apk`: same package, same debug signing key, versionName 0.1.1 and versionCode 1001.
* `other-signer.apk`: same package/version as valid, but a different signing key.
* `other-package.apk`: any properly signed different-package APK.
* `old.apk`: the installed 0.1.0 / versionCode 1000 debug APK.

Then run with `-e fixtures true`; `-e screenshots true` also records theme screenshots in the test app's `files/screenshots/`. Fixture APKs are test-only and are not part of the source/release downloads. These fixtures target an installed test build of **0.1.0 / 1000**: explicitly set Gradle properties `appVersionName=0.1.0` and `appVersionCode=1000` when building that target/runner, because the current project defaults to 0.1.1. Use a dedicated emulator.

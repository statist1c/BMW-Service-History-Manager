# Notices and provenance

Required Notice: Copyright 2026 devttys1 (https://github.com/devttys1/bmw-service-manager)

This is an independent native Android adaptation of **BMW Service Manager** by devttys1, based on upstream commit `fb2280b3fdaa61ac25b2aa064c5de5b8580d0621`, inspected on 15 November 2026.

The Android application and adaptation are distributed under the **PolyForm Noncommercial License 1.0.0**, reproduced without modification in `LICENSE.md` and inside the APK. Preserve that license and the Required Notice whenever distributing copies or derivatives. This is source-available software with a noncommercial restriction; do not describe it as permissively licensed open source.

Ported sources and behavior:

* `records.py` → `ServiceRecord.java` / `HistoryJson.java`: binary layout, field limits, item names and codes, statuses, EDIABAS arguments and Toolset32 text interchange.
* `headunit.py` → native write preview and sequential history operations, request order, delays, routine status handling and target 0x63.
* `hsfz.py` → `Hsfz.java`: TCP framing, tester 0xF4, discovery/identity, acknowledgement, alive-check and pending-response handling.
* `cbs.py` → `Cbs.java`: cluster record decoding, owner mapping and default reset payload.

Android changes include a new platform-native UI matching the user's MGU companion apps, phone storage and file pickers, stricter validation and response matching, absolute transport deadlines, explicit phone-network binding, VIN checks, gated mutation controls, and a GitHub APK updater. The updater/version/provider scaffolding was adapted from the user's earlier MGU FSC Android project, generated in the same workspace; no additional external runtime library is bundled.

Upstream documents the history layout as observed in SHM.exe, and the Toolset32 interchange as compatible with theDeniZ/ServiceManager. These tools and their binaries are not included.

The Gradle wrapper JAR and scripts are build tooling under Apache License 2.0. The Gradle distribution's license and notice are included in `licenses/`; the downloaded build tools retain their respective licenses. The APK has no third-party application-library dependencies. Android's platform UI, fonts and JSON implementation are provided by the device, not copied into this project. Upstream Flask/Python dependencies, web fonts, web libraries, map imagery and car models are not bundled.

BMW, iDrive and ISTA are trademarks of BMW AG. This project is not affiliated with, endorsed by or sponsored by BMW AG. The app icon is an original generic service clipboard, not a BMW badge.

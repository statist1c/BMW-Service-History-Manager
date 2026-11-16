# v0.4.1-beta — private car diagnostic build

Unpublished test build. No GitHub release, tag, or source push is part of this delivery.

Changes:
- Fixed loss of partial head-unit identification results when an individual read encounters a transport error. Stop the scan without retrying the connection; incomplete scans cannot authorize changes.
- Visible version, reader revision BMW-HU-ID/2, all nine planned identification DIDs, individual requests/responses, completion status and installed APK SHA-256.
- Export car diagnostic log buttons on Vehicle and Monitor. Includes the most recent identification scan saved privately across restarts, its build/time, current session and raw CBS traffic. Logs contain VIN and ECU identifiers.
- Beta installed version works with updater comparisons; remote release tag filtering stays unchanged.

The seven-field report visible in the supplied photos could not be reproduced from the audited v0.4.0 source or delivered/public APK. Its cause remains unresolved. This beta supplies evidence for the next test; it does not establish MGU write compatibility or add an iDrive completed-history reader.

## Car test
1. Export a backup of your local saved history, then install this APK over the existing app. Do not uninstall or clear app data. Confirm 0.4.1-beta is visible in the app header.
2. Park the car, switch ignition on, and connect the same adapter/network used previously. Keep the app open during each scan.
3. On Vehicle, press Check vehicle identity and wait for completion.
4. Press Read head-unit identification and wait. The report should say BMW-HU-ID/2 and list nine planned DIDs starting F150 and F101. Close the report after reading it. An unavailable response is useful evidence too.
5. Press Read CBS from vehicle and wait for completion or an error. This reads current service requirements.
6. Press Export car diagnostic log on Vehicle or Monitor, choose Downloads and save the text file. Send that file back with any error you see. If a scan stops, export the log anyway. Export after CBS in the same app session so it includes CBS traffic.
7. For this test, do not press any reset, erase, write or restart controls.

Validation: 130 core/protocol checks, 36 Android storage/failure checks, 49 Android UI checks, and 24 Android diagnostic/export checks passed. Signed APK upgrade installed successfully on the emulator. Debug/release Android lint passed. No real-car validation performed.

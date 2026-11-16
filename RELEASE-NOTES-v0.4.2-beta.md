# v0.4.2-beta — CBS scan recovery

Private, unpublished build. No GitHub release, tag or source upload.

## Findings from the November 16 car test
- Installed v0.4.1-beta fingerprint matches the delivered APK.
- All nine head-unit identification reads completed; F150 returned 0F25B0 (mapped to MGU), F101 returned a software fingerprint, and serial/VIN reads succeeded. Five optional identifiers returned NRC 31.
- First CBS scan received seven populated records but stopped at slot 0x7E with response 7F 22 21. The previous implementation discarded the collected results and cleared checked vehicle identity through its generic error handler.
- The user's second CBS scan completed with seven records. The recording shows both the first error and successful second scan, followed by log export. No mutation requests appear in the supplied log.
- Current service requirements were returned; completed iDrive service history and write permission were not tested.

NRC 0x21 is BusyRepeatRequest; NRC 0x31 is RequestOutOfRange. Reference: https://udsoncan.readthedocs.io/en/latest/_modules/udsoncan/ResponseCode.html

## Changes
- Pace cluster reads with a 75 ms gap. On an explicit NRC 21 response, repeat only that same read after 300 ms, then 600 ms, with at most two retries. This is a conservative application policy, not a BMW-verified timing specification.
- The bounded retry is local to CBS service 22 reads. Writes, resets, erase and restart behavior is unchanged.
- Preserve collected CBS records on transport errors, malformed replies and exhausted busy responses. Show completion state, attempted slots and error reason in the UI and exported session log.
- Incomplete snapshots cannot authorize CBS resets. A handled partial scan does not discard the independently checked vehicle identity.
- Retain v0.4.1-beta build fingerprint and diagnostic export functionality.

## Validation
402 core/protocol checks passed, including bounded busy recovery, busy exhaustion, disconnect, malformed payload and prior record/protocol checks. 26 signed-release Android diagnostic/export checks and 49 native Android UI checks passed. Debug/release lint passed. Installed as an upgrade over the earlier signed beta on the emulator. This new build has not been tested on the real car.

## Test steps
1. Install v0.4.2-beta over the existing app; do not uninstall or clear data. Confirm the version in the header.
2. With the car parked, ignition on and adapter connected, press Check vehicle identity.
3. Press Read head-unit identification and close the report after it finishes.
4. Press Read CBS from vehicle once and wait. Any busy retries should be logged automatically. Check whether seven items appear and whether the scan says complete or incomplete.
5. Export car diagnostic log to Downloads and send the text file back. Export even if the scan is incomplete. Keep the app open until export so the session traffic is included.
6. Do not press reset, write, erase or restart controls for this read-only test.

Logs include VIN and ECU identifiers. Supplied recordings and raw vehicle logs are not included in the source archive or uploaded publicly.

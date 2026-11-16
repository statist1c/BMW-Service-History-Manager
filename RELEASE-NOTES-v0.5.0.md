# v0.5.0

Tested and working on a 2019 BMW 530e G30 with iDrive 7: ENET discovery, vehicle identity, MGU head-unit identification and reading current CBS service requirements.

The read-only implementation was tested on the car in v0.4.2-beta, with two complete CBS scans returning seven service items. v0.5.0 carries that implementation forward. This is validation on one vehicle, not a claim of compatibility with every BMW.

- Paced CBS reads, up to two delayed retries for an explicit busy response, and retained partial results with clear incomplete status.
- Export car diagnostic log from Vehicle or Monitor, including installed APK fingerprint, head-unit report and raw current-session CBS traffic.
- Per-VIN workspaces, record editing, JSON/TST transfer and persistent write journals from v0.4.0.

Service-history writing, erase/restart and CBS resets remain unverified on this car. MGU CBS resets remain disabled. Reading completed iDrive history and updating BMW's cloud service book are not supported.

Install the same-key APK over your existing version; do not uninstall or clear data. Export a backup before upgrading. Diagnostic exports contain vehicle identifiers.

Source is published to main. The APK release and assets are uploaded manually by the project owner.

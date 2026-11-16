# v0.4.0

## Vehicle workspaces
- Separate saved histories for each VIN, plus an offline workspace.
- Switch vehicles or explicitly copy current records to a VIN workspace.
- Existing history is migrated while keeping the original file. TST save-back destinations are separate for each workspace.

## Identification and write evidence
- Reads BMW F150 diagnostic variant and F101 software fingerprint in addition to the existing identity fields.
- History changes require a matching recognized family and VIN-bound workspace; variant/software and gateway identity are rechecked immediately before sending.
- Persistent write journals retain planned requests, local snapshots, raw replies and accepted/rejected/uncertain outcomes.
- Interrupted or previously accepted appends are blocked using VIN, draft identity, date and record fingerprint. No automatic replay or rollback.
- View and export journals from Monitor. Journals are local evidence, not a vehicle-history backup.

## Record validation
- Checks future dates, duplicate dates and unsupported custom item codes before vehicle writes.
- Supports signed remaining distance, including negative overdue values. Mileage stays unsigned.
- Legacy unsigned distance imports preserve their wire bytes when converted to signed values.
- Corrected CIC routine status handling: an out-of-memory result is rejected rather than treated as success.

## Validation
- Core/protocol, Android UI and storage-failure checks passed, including interruption and duplicate-write protection.
- Signed release build and Android lint passed.
- Upgrade migration from v0.3.0 verified with saved records; the original history file remained unchanged.

## Compatibility
MGU / iDrive 7 and 8 support remains experimental and requires real-vehicle validation. No vehicle-history read-back, BMW backend update, DoIP history writer or security unlock is added. Preview-release updates and the M-inspired icon are retained.

The new features are independently implemented; no bmw-self-service application code or assets are included. Existing license terms and required notices are preserved.

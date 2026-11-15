# v0.2.2

## Easier service-history editing
- Change an existing service item's type directly, including custom item codes, while retaining its status and remaining distance/time.
- Move individual service items up or down within a record.
- Insert new records at the top or bottom of the history.
- Insert new items at the top or bottom of a record.
- Save back to an imported TST file after an overwrite confirmation. Export a new copy if the file provider does not allow overwriting.
- Cancelling a record discards its nested item edits. Importing JSON clears the previous TST save destination.

## Validation
- Signed release build and Android lint passed.
- All 37 Android checks passed, including native editor changes, insertion/reordering, cancellation, and TST save-back/truncation.
- Core protocol and feature checks passed.

## Compatibility
MGU / iDrive 7 and 8 service-history operations remain experimental and await real-vehicle validation. No additional vehicle diagnostic or write routines are introduced in this release. Local history editing does not register records in BMW's central service history.

The filename-independent updater and M-inspired icon from v0.2.1 are retained. Publish exactly one APK per stable release.

Editor conveniences were independently implemented after comparing theDeniZ/ServiceManager; no code or assets from that project are bundled. Existing upstream credits and the PolyForm Noncommercial license are preserved.

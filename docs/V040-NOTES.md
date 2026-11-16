# v0.4.0 evidence and limits

The workspace, journal and record-check implementations are original Android code. The comparison with [bmw-self-service](https://github.com/lennoncarvalho/bmw-self-service/tree/8add6e957a8755b31d727263b8ddbd8673d35210) informed requirements, not copied implementation. No source application code or assets from that repository are bundled. Its published history evidence explicitly says `physical_validation: false`.

## Identification

Read-only UDS requests `22 F1 50` and `22 F1 01` at head-unit address `0x63` retain the three-byte diagnostic variant and raw software fingerprint. Generic identity reads remain available as additional information. Known variant identifiers are protocol facts cross-referenced with the project's published source evidence:

| Variant | Family label |
| --- | --- |
| 0F12D0 | CIC |
| 0F1A30 | NBT |
| 0F2230 | NBT Evo |
| 0F1820 | ENTRY |
| 0F1580 | CHAMP2 |
| 0F25B0 | HU_MGU |
| 0F2E60 | MGU_02_A |
| 0F2E50 | MGU_02_L |

These identifiers do not establish an iDrive marketing version, a universal firmware allowlist, authentication rights or verified vehicle support. A recognized variant must match the selected family; a nonempty F101 fingerprint and the checked gateway/VIN/network are also required. Immediately before history add/erase/restart, a new connection rechecks F150/F101 and the gateway identity. Unknown variants stay read only. Existing MGU acknowledgement and mutation confirmations remain required. CBS reset still has its separate restrictions; this is not a new CBS reset implementation.

## Signed distance

The local CIC description was also checked during this release. Its SERVICEHISTORY status table defines 0 as success and 1 as out of memory. v0.4.0 corrects the earlier CIC-specific acceptance of 1; that result now stops the operation. The inspected `cic.prg` SHA-256 is `d77e5bf8dd036d58354fa560dff1403610a1e194a21d2b650066b4baaa853650`. The file and its disassembly are not distributed.

Offline inspection of the locally installed MGU job definitions found the first item's remaining-distance argument loaded as a 32-bit long (`parl`, argument 12) and packed by arithmetic right shifts (`asr`, 24/16/8 bits), then masked bytes. Subsequent item groups repeat that packing. The proprietary files and disassembly remain outside the deliverable. Input hashes are in MGU-SUPPORT.md. An independent Java encoder is checked at signed boundaries and negative multiples. Mileage remains unsigned 32-bit; remaining time remains unsigned 16-bit. Legacy imported unsigned distance values above 2^31-1 become the corresponding signed two's-complement value, preserving their four wire bytes. This is protocol representation evidence, not confirmation of how a particular car displays overdue values.

## Local data and write evidence

Legacy history.json is retained. Per-VIN workspaces use private, atomic files and preserve draft IDs across editing and JSON round trips. Switching vehicles does not merge their records and invalidates connection checks. TST save-back destinations are per workspace. Imports naming a different VIN explicitly identify which workspace will be replaced.

Before a history mutation is sent, its journal records a frozen request and `sending` state, syncs it to storage and verifies the committed bytes. Storage failure stops sending. The record, VIN, exact variant/software and per-step raw replies are retained. A pending `sending` state after a crash is uncertain; it is never automatically retried. Earlier accepted steps followed by a failure remain partial. Append guards compare VIN and draft ID, date or encoded-record hash, including across application restarts. An explicit replacement can still erase and rewrite history after the existing destructive-operation confirmations. It is never an automatic fallback for a refused append. Journal files are retained, reports are bounded, and the phone's private data must not be cleared if that local evidence is needed.

Journals cannot detect records written by another tool, prove the iDrive display was updated, restore omitted vehicle entries or update BMW's backend. No vehicle-history reader, DoIP history writer, security unlock, certificate alteration or hardware-validation claim is introduced.

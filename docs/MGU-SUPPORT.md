# MGU support in v0.2.0

**Implemented experimental service-history support, not real-vehicle validation.** Unknown-family changes remain disabled. This is local head-unit history editing, not BMW backend service-book registration.

## Evidence inspected

On 15 November 2026, the user's installed `hu_mgu.prg`, `mgu_02_l.prg`, `mgu_02_a.prg` and `nbtevo.prg` diagnostic descriptions were inspected offline. No diagnostic jobs were executed against a vehicle. No proprietary PRG file, disassembly, database or runtime was copied into the project or APK.

The MGU descriptions define service-history add/erase jobs, the same 9 record header arguments and up to 13 four-field service-item groups. Their embedded command prefixes are `31 01 A0 81` (add) and `31 01 A0 80` (erase), targeting address `0x63`. Their declared result codes distinguish success `0x00` from memory failure `0x01`, inconsistency `0x02`, missing write permission `0x04`, and other error `0x05`. These are protocol facts used for an independent implementation; the proprietary descriptions are not a dependency.

Local input SHA-256 hashes, for reproducibility:

| File | SHA-256 |
| --- | --- |
| hu_mgu.prg | `2f4e3a15d6452de6208be3a23a1157503074d4ac5e9bbc116920d7ecf04f0d1c` |
| mgu_02_l.prg | `26af34fabc3cc97a556dbdbe283f7652f7f53dd48e728ccf3013bade40a52b78` |
| mgu_02_a.prg | `2710398e368869c0a5ed530df0796b4d4e216e2e0e1d85d069250435f2332fe7` |

Independent public references:

* [BimmerGuide's own Tool32 job comparison](https://www.bimmerguide.de/service-historie-eintrag-mit-tool32/) lists HU_MGU, describes a common argument layout and documents distinct CIC versus NBT/MGU result meanings.
* [BimmerManager's developer FAQ](https://www.bimmermanager.com/support) describes its own MGU history support as experimental rather than fully validated. It is corroborating context, not source code or proof that this Android port works on a particular car.
* [UDSonCAN's standard DID definitions](https://github.com/pylessard/python-udsoncan/blob/master/udsoncan/common/dids.py) identify the seven read-only identity fields used here. No UDsonCAN code/library is bundled.

## Implemented behavior

Select **MGU · experimental**, enable experimental vehicle changes and acknowledge the MGU notice. Check gateway identity/VIN, then read head-unit identification. At least one head-unit identifier must return; that check is scoped to the checked gateway/VIN and phone network for this session. The app does not infer the head-unit model from an arbitrary returned string.

The existing native HSFZ transport and common record layout carry the add/erase requests. MGU routine status must be zero; status one stops the operation as a memory error. A response missing its status byte is unverified and stops the operation. CIC uses its separate success value of one. A failed step stops subsequent records/restart; no automatic retry or rollback is attempted.

The app retains VIN checks, exact previews, backup acknowledgement, two confirmations, raw request/response logging and an identity recheck immediately before a mutation. Selected-record writes never send erase. Independent erase writes no replacement records. MGU remaining-distance values above signed 32-bit range are refused; use zero for ordinarily completed items unless other values have been validated for the particular vehicle.

## Remaining limits

No BMW vehicle was available. Gateway routing, firmware/session requirements, actual ECU permissions, display behavior and write acceptance require testing. Model selection is manual; one responding identity DID establishes responsiveness only. The software does not unlock security access, alter certificates, bypass ECU write permission checks, flash firmware or add records to BMW's backend.

MGU generations/software levels can differ. Native history read-back/backup is still unavailable. Photo-document and export records before any write. A failed operation can leave partial changes. Missing permissions or rejected routines are reported and not worked around automatically.

CBS reset on an MGU-equipped vehicle is a separate cluster/ECU question and remains disabled in the MGU profile pending validation. Read-only CBS scanning and head-unit identity reads remain available experimentally.

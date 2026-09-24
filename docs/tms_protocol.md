# TMS/THN31 NFC Stack — Technical Reference

Redmi Note 14 Pro 5G (codename `malachite`/`malachite_eea`, MT6878 Dimensity 7300
Ultra). NFC controller vendor: Tsingteng MicroSystem ("TMS"), controller family
THN31. No root; all data obtained via `adb` from a stock, unmodified system.

This document is a living reference. It reflects current understanding only;
it is not a change log.

## 1. Component stack

```
App (holding android.permission.WRITE_SECURE_SETTINGS)
   │  TmsNfcAdapter          — public client class, com.tms.nfc.jar
   │  ITmsNfcAdapter          — AIDL, binder obtained via
   │    INfcAdapter("nfc").getNfcAdapterVendorInterface("nfc_tms")
   ▼
com.android.nfc process  (APK: /system/app/NQNfcNci/NQNfcNci.apk)
   │  TmsNfcService.TmsNfcAdapterService   — Stub implementing ITmsNfcAdapter
   │  TmsNfcService.TmsHciAdapterService   — Stub implementing IHciAdapter
   │  TmsNativeNfcManager implements TmsDeviceHost   — JNI boundary
   ▼
libtmsnfc_nci_jni.so → libtmsnfc-nci.so   (TMS fork of the NFA/NCI stack;
   │                                        embedded source path:
   │                                        vendor/tms/sys/opensource/libtmsnfc-nci/src/nfa/dm/nfa_dm_act.cc)
   ▼
android.hardware.nfc-service-tms  (vendor AIDL HAL, vendor.tms.tmsnfc_aidl.ITmsNfc)
   ▼
/dev/tms_nfc → THN31 controller
```

Both `com.tms.nfc.jar` (thin framework client) and `NQNfcNci.apk`
(`com.android.nfc`, the real service) declare the same AIDL surface. The APK
holds the actual implementation and is the primary object of analysis.

## 2. Permission model

All sensitive `ITmsNfcAdapter`/`IHciAdapter` methods — `sendNciCommand`,
`getHciAdapterService`, `IHciAdapter.open/close/transceive`,
`setM1RawDataModeEnable`, `setForceSAK`, `setSkipTagSelect`,
`getNfccSerialNumber` — are gated by `NfcPermissions.enforceAdminPermissions()`:

```java
// com/android/nfc/NfcPermissions.java
private static final String ADMIN_PERM = "android.permission.WRITE_SECURE_SETTINGS";
public static void enforceAdminPermissions(Context context) {
    context.enforceCallingOrSelfPermission(ADMIN_PERM, ADMIN_PERM_ERROR);
}
```

Lower-tier calls (routing, RF masks) use `enforceUserPermissions()` =
`android.permission.NFC`.

On stock AOSP, `WRITE_SECURE_SETTINGS` can be granted to an installed app from
`adb shell` without root:

```
adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS
```

On this device (MIUI, region `ES`, `ro.miui.region=ES`), this fails
regardless of install method (`adb install`, `pm install` from shell, or
session-based `pm install-commit`) with:

```
java.lang.SecurityException: grantRuntimePermission: Neither user 2000 nor
current process has android.permission.GRANT_RUNTIME_PERMISSIONS.
```

Stock AOSP special-cases the shell UID (2000) for this call; MIUI removes
that special case unless a separate Developer Options toggle, **"USB
debugging (Security settings)"** (distinct from plain "USB debugging"), is
enabled. That toggle itself requires a Mi Account plus, on this build, a
physical SIM present in the tray (no active service needed — presence only).
Root is not otherwise required for any software-level phase of this project,
but on this specific device/region, no-root operation is contingent on
clearing this MIUI gate (SIM-in-tray) or on rooting instead.

A second, independent MIUI gate blocks package installation itself
(`INSTALL_FAILED_USER_RESTRICTED`) via **"Install via USB"**, also under
Developer Options — this one was cleared without a SIM (Mi Account + Wi-Fi
sufficed on this build) and no longer blocks `adb install`.

`changeRfParams(data, lastCmd)` is gated only by `enforceUserPermissions()`
(`android.permission.NFC`, install-time, already held) and internally routes
through the same private `sendNciCommand()` → `sendRawNciCommand()` → native
raw-NCI path as the admin-gated public method, subject to
`checkRfConfigCommandValidate()` restricting the frame to `CORE_SET_CONFIG`
shape (`data[0]==0x20, data[1]==0x02`). Confirmed working end-to-end without
`WRITE_SECURE_SETTINGS`: sending the M1RawDataMode-enable command
(`20 02 05 01 A2 45 01 01`) through it returns status `0` (NCI response
valid, `rsp[3]==0`), proving the native NCI round-trip is live and the
controller responds. This does not, however, yield a usable notification
path: `TmsNfcService.onM1RawDataAuthCallback()` gates delivery on its own
Java-side `mM1RawDataModeState` field, which is only set by the admin-gated
`setM1RawDataModeEnable()` — toggling the native flag through
`changeRfParams` alone leaves that field at `0`, so any resulting auth event
is logged and dropped (`"m1 raw data mode is off"`) rather than forwarded.
`changeRfParams` is therefore useful as a permission-light connectivity/health
probe for the raw NCI path, not as a way to reach PTM, `sendNciCommand`, or a
working M1RawDataMode callback — those still require `WRITE_SECURE_SETTINGS`.

Obtaining the `ITmsNfcAdapter` binder itself (`ServiceManager.getService("nfc")`
→ raw `transact()` on `INfcAdapter` transaction code 6,
`getNfcAdapterVendorInterface`) requires no permission at all — it is every
individual method call on it that is gated. `android.nfc.INfcAdapter` is
Android's own hidden-API blocklist tier (`hiddenapi: ... api=blocked`), not
MIUI-specific, and unlike the greylist tier is not bypassable by lowering
`targetSdkVersion` nor by the `VMRuntime.setHiddenApiExemptions` meta-reflection
trick (that method is itself blocklisted on this Android version). Reached
instead via a hand-built raw Binder transaction using only public
`IBinder`/`Parcel` APIs — transaction code and marshalling order read directly
out of the on-device `framework.jar`'s `INfcAdapter$Stub`/`$Stub$Proxy` smali
(`android.nfc.INfcAdapter`, code `6`, one `String` arg). Confirmed working:
`TmsAccess.getAdapter()` returns a live `ITmsNfcAdapter` with no permission
held. The per-method `WRITE_SECURE_SETTINGS` check is genuine server-side
`Context.enforceCallingOrSelfPermission` in the `com.android.nfc` process
against the real permission grant for the caller's UID — not a local
API-visibility check, so it has no equivalent reflection/Binder bypass.

## 3. Reader-side raw channels

### 3.1 `sendNciCommand()` — raw NCI passthrough

```java
// TmsNfcService.java
public byte[] sendNciCommand(byte[] cmd, boolean restartRfDiscovery) {
    return this.mTmsDeviceHost.sendRawNciCommand(cmd, restartRfDiscovery);
}
```

`TmsNativeNfcManager.sendRawNciCommand()` → native `doSendRawNciCommand()` →
native NFA/NCI stack, response returned synchronously. Accepts arbitrary NCI
frames, including proprietary GID. Used internally by the service itself to
drive `setM1RawDataModeEnable` (proprietary command `20 02 05 01 A2 45 01
<status>`) and `changeRfParams`. Suitable for controller configuration and
standard NCI RF Discovery / Frame-RF-Interface exchanges. Not expected to
carry MIFARE Classic's 7-bit short frames / AUTH sequence on its own.

### 3.2 `IHciAdapter` — Passthrough Mode (PTM)

Despite the interface name, this is not ETSI HCI. Internally it is called
"Passthrough Mode" (PTM) at every layer:

```java
// TmsNfcService$TmsHciAdapterService — real Stub implementation
public void open(IHciCallback cb) {
    enforceAdminPermissions(...);
    mTmsDeviceHost.setPassthroughMode(1);
    this.mCallback = cb;
}
public void transceive(byte[] data) {
    enforceAdminPermissions(...);
    mTmsDeviceHost.sendRawPtmCommand(data);
}
void onNciResponseReceive(int event, byte[] rsp) {
    mCallback.onHciDataReceive(rsp);   // async delivery
}
public void close() {
    mTmsDeviceHost.setPassthroughMode(0);
}
```

Confirmed at the JNI layer (`libtmsnfc_nci_jni.so`): `doSetPassthroughMode`,
`doSendRawPtmCommand`, `nfcManager_doSendRawPtmCommand`,
`notifyRawPtmCommandCallback`, `nfaPtmRawCommandCallback`.

Confirmed at the native NFA layer (`libtmsnfc-nci.so`, TMS's own fork):
`NFA_SetPassthroughMode`, `NFA_SendRawPtmCommand`, `NFC_SetPassthroughMode`,
`nci_proc_passthrough_mode`, `nfa_dm_act_send_raw_ptm`, debug string
`"NFC_SetPassthroughMode() Enter, mode = %d"`. This is a dedicated NFA DM
activity TMS added alongside stock `nfa_dm_act_send_raw_frame` /
`nfa_dm_act_send_raw_vs`.

This is the primary candidate channel for MIFARE Classic AUTH-level raw
exchange (nonce capture for hardnested). Exact wire format of `transceive()`
is unverified — pending empirical test (§5).

### 3.3 `M1RawDataMode`

Public wrapper name is `activateSeInterface()`/`deactivateSeInterface()`
(`TmsNfcAdapterCustom`, `VendorNfcAdapter`). Enabling it sends the proprietary
NCI command above via `sendNciCommand()`, and the only data returned to the
app is a single `int authStatus` (pass/fail) via
`onM1RawDataModeAuthResult`/`onM1RawDataAuthCallback`. On result it broadcasts
`com.miui.nfc.action.TRANSACTION` with a hardcoded MIFARE AID. This is
card-emulation-side transaction signaling (phone as a MIFARE-routed card via
eSE/UICC/HCE), not a reader-side nonce source, and carries no exploitable
crypto data.

### 3.4 Type-4 Tag APIs (not applicable to MIFARE Classic)

`sendT4tRawApdu`, `doReadT4tData`, `doWriteT4tData` operate on ISO-DEP/NDEF
(Type 4 Tag). MIFARE Classic is Type A / M1, not T4T — out of scope for this
project except as a reference for how the service structures raw exchanges.

### 3.5 Reader-mode tuning

`setForceSAK(enable, sak)`, `setSkipTagSelect(protocol)` — force a specific
SAK byte / skip automatic SELECT during discovery. Auxiliary controls, not
data channels.

## 4. Native library map (arm64-v8a, from NQNfcNci.apk)

| Library | Role |
|---|---|
| `libtmsnfc-nci.so` | TMS fork of the NFA/NCI core (`nfa_dm_act_*`, PTM activity) |
| `libtmsnfc_nci_jni.so` | JNI bridge between `TmsNativeNfcManager` and the core |
| `libsn100nfc-nci.so` / `libsn100nfc_nci_jni.so` | Stock NXP SN100x NCI stack (present but unused on this build — TMS path is what's wired via init.rc) |
| `vendor.tms.tmsnfc_aidl-V1-ndk.so` | AIDL stub for the vendor HAL (`ITmsNfc`: `doAction`, `nfccFwDownload`, `EseSoftReset`, `setTmsTransitConfig`, `getVendorParam`/`setVendorParam`) |

## 5. Verification status

| Item | Status |
|---|---|
| Component/permission chain (static) | Confirmed by decompilation |
| `sendNciCommand` raw passthrough | Confirmed by decompilation |
| PTM channel existence and wiring | Confirmed by decompilation (3 layers) |
| `WRITE_SECURE_SETTINGS` grantable without root | Not yet empirically tested |
| PTM `transceive()` wire format | Unknown — requires live RF test |
| MIFARE Classic AUTH nonce capture via PTM | Unproven |
| Kernel driver (`tms_device_modules.ko`) internals | Blocked — not root-readable |
| HAL binary (`android.hardware.nfc-service-tms`) internals | Blocked — not root-readable |

## 6. Environment

- Device: `24090RA29G` / `malachite_eea` / `malachite`, ADB over WSL2 (`usbipd-win`), no root, SELinux enforcing.
- Toolchain: jadx 1.5.6 (`~/tools/jadx-1.5.6`), Android SDK cmdline-tools + platform 34 + NDK 26.3.11579264 (`~/Android/Sdk`).
- Extracted artifacts live under `extracted/` (jars, apks, decompiled sources, native libs, vendor configs).
- MIUI's own install/grant restrictions (distinct from the TMS/permission
  model above): `adb install` requires Developer Options → "Install via
  USB" (cleared here without a SIM, Mi Account + Wi-Fi sufficed); `pm grant`
  requires the separate "USB debugging (Security settings)" toggle, which
  on this build additionally requires a physical SIM in the tray to
  complete a Xiaomi Activation-Lock-style server-verified check
  (`com.xiaomi.simactivate.service`) — not yet cleared, blocks
  `WRITE_SECURE_SETTINGS` regardless of the app-side workarounds below.

## 7. Android-side implementation status

The probe app (`android/probe/`) and native module (`android/mifare-native/`)
implement the three target commands end to end, ready to test against real
hardware once `WRITE_SECURE_SETTINGS` is available:

- **Binder-level access without `WRITE_SECURE_SETTINGS`**: `INfcAdapter.
  getNfcAdapterVendorInterface()` (transaction code `6`, descriptor
  `android.nfc.INfcAdapter`, read directly out of `framework.jar`'s
  `INfcAdapter$Stub`/`$Stub$Proxy` smali) is hidden-API **blocklist** tier —
  not bypassable by lowering `targetSdkVersion` (that only clears the
  weaker greylist tier) nor by the `VMRuntime.setHiddenApiExemptions`
  meta-reflection trick (itself blocklisted on this Android build). Reached
  instead via a hand-built raw Binder `transact()` using only public
  `IBinder`/`Parcel` APIs (`TmsAccess.java`). This gets a live
  `ITmsNfcAdapter` with zero permissions held; every individual method call
  on it is still permission-gated server-side (§2) and has no equivalent
  bypass — that part is genuine access control, not a local check.
- **`android/mifare-native/`**: upstream Iceman's `crapto1` +
  `hardnested_bf_core`/`hardnested_bitarray_core` (bitsliced NEON brute
  forcer) + a lightly patched `cmdhfmfhard.c` (only `acquire_nonces()`
  changed — see the "ANDROID/THN31 PATCH" comment in
  `third_party/client_src/cmdhfmfhard.c` — everything else in that 2785-line
  file, including all sum-property statistics and threading, is untouched
  upstream logic), cross-compiled for arm64-v8a with the NDK and linked
  into `libmfcbridge.so`. Verified: zero unresolved symbols at real
  shared-library link time (only bionic libc/libm left undefined). Build:
  `android/mifare-native/build.sh`.
- **`mfc_proto.c`**: clean-room MIFARE Classic protocol layer (auth,
  nested-auth nonce capture, read/write/value/transfer) — not copied from
  `armsrc/mifareutil.c` (bare-metal PM3 firmware code, not portable), but
  deliberately byte-for-byte faithful to it (command bytes, CRC_A, crypto1
  keystream/parity application), each function cross-referenced to the
  upstream line it mirrors.
- **`hardnested_tables/`** (upstream's precomputed bitflip probability
  tables, 351 files / 9MB, `.lz4`-compressed): bundled as Android assets,
  extracted to app-private storage on first run. `lz4frame.c`/`xxhash.c`
  (matching the vendored `lz4.c` v1.9.3) were fetched from the upstream lz4
  project to decompress them, since proxmark3 itself only vendors
  `lz4.c`/`lz4hc.c` and pulls `lz4frame` from elsewhere at its own build
  time.
- **The one thing that cannot be closed without a live card + PTM access**:
  `IHciAdapter.transceive(byte[])` takes a single plain byte array with no
  separate parity-bits parameter, yet MIFARE Classic's crypto1 protocol
  requires explicit per-byte parity control during authenticated exchange.
  `jni_bridge.c`'s `mfc_transceive()` currently assumes a self-describing
  frame (`[len][data...][packed parity bits...]`) it invents itself — an
  explicitly flagged guess, isolated to that one function, documented
  in-file. Nothing above it (`mfc_proto.c`, the patched `cmdhfmfhard.c`,
  the hardnested engine) needs to change if the real format turns out to
  be different.

## 8. UI: "no detecta la tarjeta" (2026-09-24)

Root cause found in `MainActivity`'s original single "Connect" button: it
opened PTM (`IHciAdapter.open()`) unconditionally before enabling NFC reader
mode. Opening PTM takes the THN31 controller out of its normal RF polling
loop, so `NfcAdapter.ReaderCallback.onTagDiscovered()` stops firing while PTM
is open — the app looked like it couldn't see any card at all, even a
perfectly ordinary one, regardless of `WRITE_SECURE_SETTINGS`.

Only `hardnested` actually needs PTM (raw encrypted-nonce + parity capture,
§4.1 of `research/iceman-hf-mf-command-mapping.md`). The dictionary sweep
(`autopwn`) and `value --inc` both work over the plain public
`android.nfc.tech.MifareClassic` API (`Tag.transceive()` under the hood, same
`[0x60/0x61][block][uid][key]` command TMS's own `TmsM1Tag.authenticate()`
uses), which needs neither PTM nor `WRITE_SECURE_SETTINGS` and does not
disturb reader-mode polling.

The app UI was reordered/relabelled around this split (§9) so the
PTM-required path is opt-in and clearly marked, and the default "Conectar
(modo lector)" button — the one that actually detects cards — never touches
PTM.

## 9. UI redesign + full-card autopwn (2026-09-24)

`MainActivity` was restructured into four numbered sections (Conexión /
Autopwn / Avanzado / Log) and the autopwn flow was extended from "one block,
Key A only" to a full-card sweep:

- Tries every dictionary key (plus keys already found on the card, tried
  first) as both Key A and Key B against every sector, not just one block.
- Logs and keeps a per-sector `SectorKeys[]` result (`lastResult`) in memory
  for the session, shown as a table and saved as a text report (CSV block +
  plain unique-key list) under `getExternalFilesDir(null)` (adb-pullable, no
  extra permission needed).
- "Fallback" for sectors the dictionary can't crack: when a sector has a
  known Key A but no Key B (or vice versa), the advanced fields (block,
  target block, key) are pre-filled and the log points at hardnested as the
  next step, since a real nested/darkside RF attack needs the same raw
  nonce+parity capture as hardnested and is blocked on the same PTM/parity
  unknowns as §7 — there is no public-API equivalent to implement as a true
  fallback today.
- This also lays the groundwork asked for as a later step (not implemented
  yet): once the wire format is confirmed, the saved per-sector Key A can be
  fed straight into hardnested for a chosen sector, and the recovered Key B
  straight into `value --inc` on a chosen block, without re-typing anything.

Not yet built: a true nested/darkside RF fallback (needs PTM + the
still-unresolved parity format, §7), and `hf mf value --inc`'s
block-copy-to-different-destination TRANSFER variant (current
`mfc_transfer()` always targets the same block).

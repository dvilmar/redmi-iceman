// MIFARE Classic protocol sequencing: authentication, nested-auth nonce
// capture, read/write/value/transfer. This is a clean-room implementation
// (not copied from armsrc/mifareutil.c -- that file is bare-metal PM3
// firmware code with heavy hardware dependencies, not portable as-is) but
// deliberately mirrors mifareutil.c's exact command bytes, CRC handling and
// crypto1 keystream/parity application byte-for-byte, so behaviour matches
// upstream Iceman. Each function below references the exact upstream
// function/line it was derived from.
//
// Card selection (REQA/anticollision/SELECT -> UID/ATQA/SAK) is NOT
// reimplemented here: it's assumed to already have happened through
// Android's own NFC stack (NfcAdapter.enableReaderMode() /
// android.nfc.tech.MifareClassic), whose result (the UID) is handed in via
// mfc_set_uid() before any of the calls below. See docs/tms_protocol.md,
// "open items" -- this is one of the two assumptions that needs live-tag
// confirmation.
#ifndef MFC_PROTO_H
#define MFC_PROTO_H

#include <stdint.h>
#include <stdbool.h>

typedef enum { MFC_KEY_A = 0, MFC_KEY_B = 1 } mfc_key_type_t;

// Call once per tag presentation, before any of the functions below.
void mfc_set_uid(uint32_t uid);

// Full authentication with a known key (mifareutil.c:205
// mifare_classic_authex / :208 mifare_classic_authex_cmd). Establishes the
// crypto1 session used by every call below until mfc_deauth().
int mfc_auth(uint8_t block, mfc_key_type_t key_type, uint64_t key);

// HALT (mifareutil.c:912 mifare_classic_halt) + release crypto1 state.
void mfc_deauth(void);

// mifareutil.c:346 mifare_classic_readblock_ex
int mfc_read_block(uint8_t block, uint8_t out16[16]);

// mifareutil.c:720 mifare_classic_writeblock_ex
int mfc_write_block(uint8_t block, const uint8_t data16[16]);

typedef enum { MFC_VALUE_INC = 0, MFC_VALUE_DEC = 1, MFC_VALUE_RESTORE = 2 } mfc_value_action_t;

// mifareutil.c:780 mifare_classic_value. `delta` is ignored for RESTORE.
int mfc_value_op(uint8_t block, mfc_value_action_t action, uint32_t delta);

// TRANSFER (opcode 0xB0), commits the last INCREMENT/DECREMENT/RESTORE to
// dest_block. Not a separate function in mifareutil.c (issued inline
// wherever needed); implemented here as mifare_sendcmd_short(..., 0xB0,
// dest_block, ...) expecting the same single-byte 0x0A ACK as write/value.
int mfc_transfer(uint8_t dest_block);

// --- hardnested nonce acquisition -------------------------------------
// Mirrors what armsrc/mifarecmd.c's MifareAcquireEncryptedNonces() does on
// real PM3 firmware, driven here directly over PTM instead. Used by the
// patched acquire_nonces() in third_party/client_src/cmdhfmfhard.c.

// Full auth with the KNOWN key on `block`/`key_type` (same as mfc_auth),
// establishing the session subsequent nested-auth captures ride on.
// *cuid_out receives the tag UID as a big-endian uint32 (matches
// upstream's `cuid` semantics).
int mfc_nested_start(uint8_t block, mfc_key_type_t key_type, const uint8_t key6[6], uint32_t *cuid_out);

// Sends ONE nested-auth-init command (60/61 <trg_block>, CRC_A, encrypted
// under the session from mfc_nested_start) targeting trg_block/trg_key_type
// and captures the raw 4-byte encrypted tag nonce + its 4-bit encrypted
// parity, WITHOUT completing the handshake (no nR/aR sent) -- exactly the
// data hardnested's statistical attack needs, nothing more. Safe to call
// repeatedly without re-selecting or re-authenticating in between (each
// call just restarts the tag's nested-auth state machine).
int mfc_nested_capture_one(uint8_t trg_block, mfc_key_type_t trg_key_type, uint32_t *nt_enc_out, uint8_t *par_enc_out);

void mfc_nested_stop(void);

// --- dictionary attack (autopwn's key-A recovery path) -----------------
// Tries each key in the built-in default-key dictionary (mfc_dict.h)
// against `block`/`key_type` via a full mfc_auth(). Returns PM3_SUCCESS and
// the working key in *found_key_out on the first hit, PM3_EFAILED if none
// of the dictionary keys work.
int mfc_dict_attack(uint8_t block, mfc_key_type_t key_type, uint64_t *found_key_out);

#endif

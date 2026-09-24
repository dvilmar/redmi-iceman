// See mfc_proto.h for design notes. Command bytes, CRC handling and
// crypto1 keystream/parity application below are deliberately kept
// byte-for-byte identical to armsrc/mifareutil.c (referenced per-function),
// just reimplemented against mfc_transceive() instead of the PM3 firmware's
// ReaderTransmit*/ReaderReceive + FPGA.

#include "mfc_proto.h"
#include "mfc_transport.h"
#include "crapto1.h"
#include "crc16.h"

#include <string.h>
#include <time.h>

// ISO14443A / MIFARE Classic command bytes (include/protocols.h in upstream)
#define MFC_CMD_REQA        0x26
#define MFC_CMD_READBLOCK    0x30
#define MFC_CMD_WRITEBLOCK   0xA0
#define MFC_CMD_HALT         0x50
#define MFC_AUTH_KEYA        0x60
#define MFC_AUTH_KEYB        0x61
#define MFC_CMD_DEC          0xC0
#define MFC_CMD_INC          0xC1
#define MFC_CMD_RESTORE      0xC2
#define MFC_CMD_TRANSFER     0xB0
#define MFC_ACK              0x0A

#define PM3_SUCCESS 0
#define PM3_EFAILED (-1)
#define PM3_ETIMEOUT (-2)

static struct Crypto1State g_pcs;
static bool g_authenticated = false;
static uint32_t g_uid = 0;

void mfc_set_uid(uint32_t uid) {
    g_uid = uid;
}

static inline uint8_t oddparity8(uint8_t b) {
    b ^= b >> 4;
    b ^= b >> 2;
    b ^= b >> 1;
    return (b ^ 1) & 1;
}

static void mfc_add_crc(uint8_t *buf, uint8_t len) {
    compute_crc(CRC_14443_A, buf, len, &buf[len], &buf[len + 1]);
}

// Simple LCG-ish reader nonce -- doesn't need to be cryptographically
// strong (mifareutil.c uses prng_successor(GetTickCount(),32) for the same
// purpose, i.e. also not a CSPRNG); nR only has to be unpredictable to the
// tag, not to us.
static uint32_t reader_nonce(void) {
    static uint32_t state = 0;
    if (state == 0) {
        state = (uint32_t)time(NULL) ^ 0x2545F491u;
    }
    state = state * 1664525u + 1013904223u;
    return state;
}

// mifareutil.c:150 mifare_sendcmd_short. `crypted`: 0 = send cleartext with
// standard odd parity, receive+decrypt nothing; 1 = XOR every transmitted
// byte with the session keystream (parity = filter(cipher) XOR parity of
// the CLEARTEXT byte, per mifareutil.c:164) and, for CRYPT_ALL responses,
// decrypt every received byte the same way.
typedef enum { MFC_CRYPT_NONE = 0, MFC_CRYPT_CMD = 1, MFC_CRYPT_ALL = 2 } mfc_crypt_t;

static int mfc_sendcmd_short(mfc_crypt_t crypted, uint8_t cmd, uint8_t data,
                              uint8_t *answer, uint16_t answer_cap, uint8_t *answer_par) {
    uint8_t dcmd[4] = {cmd, data, 0, 0};
    mfc_add_crc(dcmd, 2);

    int len;
    if (g_authenticated && crypted != MFC_CRYPT_NONE) {
        uint8_t ecmd[4];
        uint8_t par = 0;
        for (int pos = 0; pos < 4; pos++) {
            ecmd[pos] = crypto1_byte(&g_pcs, 0x00, 0) ^ dcmd[pos];
            par |= ((filter(g_pcs.odd) ^ oddparity8(dcmd[pos])) & 0x01) << (7 - pos);
        }
        uint8_t par_buf[1] = {par};
        len = mfc_transceive(ecmd, sizeof(ecmd), par_buf, answer, answer_cap, answer_par);
    } else {
        len = mfc_transceive_plain(dcmd, sizeof(dcmd), answer, answer_cap);
        if (answer_par) answer_par[0] = 0;
    }

    if (len <= 0) return len;

    if (g_authenticated && crypted == MFC_CRYPT_ALL) {
        if (len == 1) {
            uint8_t res = 0;
            for (int b = 0; b < 4; b++) {
                res |= (crypto1_bit(&g_pcs, 0, 0) ^ ((answer[0] >> b) & 1)) << b;
            }
            answer[0] = res;
        } else {
            for (int pos = 0; pos < len; pos++) {
                answer[pos] = crypto1_byte(&g_pcs, 0x00, 0) ^ answer[pos];
            }
        }
    }
    return len;
}

// Shared by mfc_auth() (full handshake) and mfc_nested_start()/
// mfc_nested_capture_one() (auth-init only, nonce capture). isNested
// mirrors mifareutil.c:230's AUTH_NESTED vs AUTH_FIRST distinction: when
// true, the auth command itself is sent encrypted under an *already*
// established session (nested auth to a different sector/key while still
// in the field); when false it's the first, cleartext auth.
static int mfc_auth_init(uint8_t block, mfc_key_type_t key_type, uint64_t key,
                          bool is_nested, uint32_t *nt_out, uint8_t *nt_par_out) {
    uint8_t answer[4];
    uint8_t answer_par[1] = {0};

    uint8_t cmd = (key_type == MFC_KEY_B) ? MFC_AUTH_KEYB : MFC_AUTH_KEYA;
    int len = mfc_sendcmd_short(is_nested ? MFC_CRYPT_CMD : MFC_CRYPT_NONE,
                                 cmd, block, answer, sizeof(answer), answer_par);
    if (len != 4) return PM3_EFAILED;

    uint32_t ntenc = ((uint32_t)answer[0] << 24) | ((uint32_t)answer[1] << 16) |
                      ((uint32_t)answer[2] << 8) | answer[3];

    crypto1_init(&g_pcs, key);

    uint32_t nt;
    if (is_nested) {
        // mifareutil.c:232 -- decrypt ntenc with the *new* key we're
        // switching to, keeping the previous session's cipher stream
        // untouched (we already sent the auth command through it above).
        nt = crypto1_word(&g_pcs, ntenc ^ g_uid, 1) ^ ntenc;
    } else {
        crypto1_word(&g_pcs, ntenc ^ g_uid, 0);
        nt = ntenc;
    }

    if (nt_out) *nt_out = nt;
    if (nt_par_out) *nt_par_out = answer_par[0] >> 4; // top nibble = this 4-byte answer's parity
    return PM3_SUCCESS;
}

int mfc_auth(uint8_t block, mfc_key_type_t key_type, uint64_t key) {
    g_authenticated = false;

    uint32_t nt;
    if (mfc_auth_init(block, key_type, key, false, &nt, NULL) != PM3_SUCCESS) {
        return PM3_EFAILED;
    }

    // mifareutil.c:285-314 -- generate encrypted nR + aR (successor(nt,64))
    // and complete the handshake.
    uint8_t nr[4];
    uint32_t nr32 = reader_nonce();
    nr[0] = (uint8_t)(nr32 >> 24);
    nr[1] = (uint8_t)(nr32 >> 16);
    nr[2] = (uint8_t)(nr32 >> 8);
    nr[3] = (uint8_t)nr32;

    uint8_t mf_nr_ar[8];
    uint8_t par = 0;
    for (int pos = 0; pos < 4; pos++) {
        mf_nr_ar[pos] = crypto1_byte(&g_pcs, nr[pos], 0) ^ nr[pos];
        par |= ((filter(g_pcs.odd) ^ oddparity8(nr[pos])) & 0x01) << (7 - pos);
    }

    nt = prng_successor(nt, 32);
    for (int pos = 4; pos < 8; pos++) {
        nt = prng_successor(nt, 8);
        mf_nr_ar[pos] = crypto1_byte(&g_pcs, 0x00, 0) ^ (nt & 0xff);
        par |= ((filter(g_pcs.odd) ^ oddparity8(nt & 0xff)) & 0x01) << (7 - pos);
    }

    uint8_t par_buf[1] = {par};
    uint8_t answer[4];
    int len = mfc_transceive(mf_nr_ar, sizeof(mf_nr_ar), par_buf, answer, sizeof(answer), NULL);
    if (len != 4) return PM3_EFAILED;

    uint32_t ntpp = prng_successor(nt, 32) ^ crypto1_word(&g_pcs, 0, 0);
    uint32_t tag_answer = ((uint32_t)answer[0] << 24) | ((uint32_t)answer[1] << 16) |
                           ((uint32_t)answer[2] << 8) | answer[3];
    if (ntpp != tag_answer) return PM3_EFAILED;

    g_authenticated = true;
    return PM3_SUCCESS;
}

void mfc_deauth(void) {
    if (g_authenticated) {
        uint8_t dummy[4];
        // HALT (mifareutil.c:912): cleartext 0x50 0x00 + CRC, no response
        // expected either way.
        uint8_t halt[4] = {MFC_CMD_HALT, 0x00, 0, 0};
        mfc_add_crc(halt, 2);
        mfc_transceive_plain(halt, sizeof(halt), dummy, sizeof(dummy));
    }
    g_authenticated = false;
}

int mfc_read_block(uint8_t block, uint8_t out16[16]) {
    if (!g_authenticated) return PM3_EFAILED;
    uint8_t answer[18];
    uint8_t par[3] = {0};
    int len = mfc_sendcmd_short(MFC_CRYPT_ALL, MFC_CMD_READBLOCK, block, answer, sizeof(answer), par);
    if (len != 18) return PM3_EFAILED;

    uint8_t crc_lo, crc_hi;
    compute_crc(CRC_14443_A, answer, 16, &crc_lo, &crc_hi);
    if (crc_lo != answer[16] || crc_hi != answer[17]) return PM3_EFAILED;

    memcpy(out16, answer, 16);
    return PM3_SUCCESS;
}

// Shared body of write/increment/decrement/restore/transfer: send `cmd
// block`, expect single-byte 0x0A ACK, then (for write/value) send an
// encrypted payload and expect a second 0x0A ACK. mifareutil.c:720
// (write) and :780 (value) duplicate this shape; kept as one helper here.
static int mfc_cmd_then_payload(uint8_t cmd, uint8_t block, const uint8_t *payload, uint8_t payload_len,
                                 bool expect_final_ack) {
    if (!g_authenticated) return PM3_EFAILED;

    uint8_t answer[4];
    uint8_t par[4] = {0};
    int len = mfc_sendcmd_short(MFC_CRYPT_ALL, cmd, block, answer, sizeof(answer), par);
    if (len != 1 || answer[0] != MFC_ACK) return PM3_EFAILED;

    if (payload_len == 0) return PM3_SUCCESS;

    uint8_t buf[18];
    memcpy(buf, payload, payload_len);
    mfc_add_crc(buf, payload_len);
    uint8_t total = payload_len + 2;

    uint8_t enc[18];
    uint8_t epar[3] = {0};
    for (uint8_t pos = 0; pos < total; pos++) {
        enc[pos] = crypto1_byte(&g_pcs, 0x00, 0) ^ buf[pos];
        epar[pos >> 3] |= ((filter(g_pcs.odd) ^ oddparity8(buf[pos])) & 0x01) << (7 - (pos & 7));
    }

    uint8_t rx[4];
    len = mfc_transceive(enc, total, epar, rx, sizeof(rx), NULL);

    if (!expect_final_ack) {
        // mifareutil.c:821 -- for INCREMENT/DECREMENT/RESTORE, no response
        // at all is the OK case (card is just holding the value pending a
        // TRANSFER); only decode+check if something *did* come back.
        if (len <= 0) return PM3_SUCCESS;
    }

    if (len < 1) return PM3_EFAILED;
    uint8_t res = 0;
    for (int b = 0; b < 4; b++) {
        res |= (crypto1_bit(&g_pcs, 0, 0) ^ ((rx[0] >> b) & 1)) << b;
    }
    if (len != 1 || res != MFC_ACK) return PM3_EFAILED;
    return PM3_SUCCESS;
}

int mfc_write_block(uint8_t block, const uint8_t data16[16]) {
    return mfc_cmd_then_payload(MFC_CMD_WRITEBLOCK, block, data16, 16, true);
}

int mfc_value_op(uint8_t block, mfc_value_action_t action, uint32_t delta) {
    uint8_t cmd = MFC_CMD_INC;
    if (action == MFC_VALUE_DEC) cmd = MFC_CMD_DEC;
    else if (action == MFC_VALUE_RESTORE) cmd = MFC_CMD_RESTORE;

    uint8_t payload[4] = {
        (uint8_t)(delta),
        (uint8_t)(delta >> 8),
        (uint8_t)(delta >> 16),
        (uint8_t)(delta >> 24),
    };
    uint8_t payload_len = (action == MFC_VALUE_RESTORE) ? 0 : 4;
    // RESTORE still needs *a* 4-byte payload on the wire per the MIFARE
    // spec (its value is ignored by the card) -- mifareutil.c:807 always
    // sends blockData regardless of action, so match that rather than
    // skip the payload for RESTORE.
    if (action == MFC_VALUE_RESTORE) payload_len = 4;

    return mfc_cmd_then_payload(cmd, block, payload, payload_len, false);
}

int mfc_transfer(uint8_t dest_block) {
    return mfc_cmd_then_payload(MFC_CMD_TRANSFER, dest_block, NULL, 0, false) == PM3_SUCCESS
           ? PM3_SUCCESS
           : PM3_EFAILED;
}

// --- hardnested acquisition --------------------------------------------

int mfc_nested_start(uint8_t block, mfc_key_type_t key_type, const uint8_t key6[6], uint32_t *cuid_out) {
    uint64_t key = 0;
    for (int i = 0; i < 6; i++) key = (key << 8) | key6[i];

    if (mfc_auth(block, key_type, key) != PM3_SUCCESS) {
        return PM3_EFAILED;
    }
    if (cuid_out) *cuid_out = g_uid;
    return PM3_SUCCESS;
}

int mfc_nested_capture_one(uint8_t trg_block, mfc_key_type_t trg_key_type, uint32_t *nt_enc_out, uint8_t *par_enc_out) {
    if (!g_authenticated) return PM3_EFAILED;

    uint8_t answer[4];
    uint8_t answer_par[1] = {0};
    uint8_t cmd = (trg_key_type == MFC_KEY_B) ? MFC_AUTH_KEYB : MFC_AUTH_KEYA;

    // Deliberately NOT going through mfc_auth_init()/crypto1_init(): we do
    // not want to switch the session's cipher state to the *unknown*
    // target key (we don't have it) -- we only want the raw encrypted nT
    // response, still under the CURRENT (known-key) session's keystream
    // for the command bytes themselves. This matches mifareutil.c's
    // isNested=AUTH_NESTED path in mifare_classic_authex_cmd(), stopping
    // right after receiving the tag's 4-byte answer.
    int len = mfc_sendcmd_short(MFC_CRYPT_CMD, cmd, trg_block, answer, sizeof(answer), answer_par);
    if (len != 4) return PM3_EFAILED;

    if (nt_enc_out) {
        *nt_enc_out = ((uint32_t)answer[0] << 24) | ((uint32_t)answer[1] << 16) |
                      ((uint32_t)answer[2] << 8) | answer[3];
    }
    if (par_enc_out) *par_enc_out = answer_par[0] >> 4;
    return PM3_SUCCESS;
}

void mfc_nested_stop(void) {
    mfc_deauth();
}

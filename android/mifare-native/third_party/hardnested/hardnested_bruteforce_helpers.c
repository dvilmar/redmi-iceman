//-----------------------------------------------------------------------------
// Copyright (C) 2016, 2017 by piwi
//
// This code is licensed to you under the terms of the GNU GPL, version 2 or,
// at your option, any later version. See the LICENSE.txt file for the text of
// the license.
//-----------------------------------------------------------------------------
// trailing_zeros() and verify_key() extracted verbatim from
// client/deps/hardnested/hardnested_bruteforce.c (RfidResearchGroup/iceman
// proxmark3, commit 3d0fa59). The rest of that file is the PM3-device
// orchestrator (talks to the reader over USB to request more nonces
// on-demand); these two functions are pure post-processing logic with no
// such dependency, so they're split out rather than pulled in whole.
//-----------------------------------------------------------------------------
#include "hardnested_bruteforce.h"
#include "crapto1/crapto1.h"
#include "parity.h"

uint8_t trailing_zeros(uint8_t byte) {
    static const uint8_t trailing_zeros_LUT[256] = {
        8, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        7, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0
    };

    return trailing_zeros_LUT[byte];
}

bool verify_key(uint32_t cuid, noncelist_t *nonces, const uint8_t *best_first_bytes, uint32_t odd, uint32_t even) {
    struct Crypto1State pcs;
    for (uint16_t test_first_byte = 1; test_first_byte < 256; test_first_byte++) {
        noncelistentry_t *test_nonce = nonces[best_first_bytes[test_first_byte]].first;
        while (test_nonce != NULL) {
            pcs.odd = odd;
            pcs.even = even;
            lfsr_rollback_byte(&pcs, (cuid >> 24) ^ best_first_bytes[0], true);
            for (int8_t byte_pos = 3; byte_pos >= 0; byte_pos--) {
                uint8_t test_par_enc_bit = (test_nonce->par_enc >> byte_pos) & 0x01;     // the encoded parity bit
                uint8_t test_byte_enc = (test_nonce->nonce_enc >> (8 * byte_pos)) & 0xff; // the encoded nonce byte
                uint8_t test_byte_dec = crypto1_byte(&pcs, test_byte_enc /* ^ (cuid >> (8*byte_pos)) */, true) ^ test_byte_enc; // decode the nonce byte
                uint8_t ks_par = filter(pcs.odd);                                        // the keystream bit to encode/decode the parity bit
                uint8_t test_par_enc2 = ks_par ^ evenparity8(test_byte_dec);             // determine the decoded byte's parity and encode it
                if (test_par_enc_bit != test_par_enc2) {
                    return false;
                }
            }
            test_nonce = test_nonce->next;
        }
    }
    return true;
}

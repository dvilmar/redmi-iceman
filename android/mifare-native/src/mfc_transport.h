// Transport seam this whole native module talks through. Implemented in
// jni_bridge.c, which calls back into Java's PtmTransport (a synchronous
// wrapper around the async com.tms.nfc.IHciAdapter Binder channel -- see
// docs/tms_protocol.md, section "Ruta B / PTM").
//
// Exact wire format is the one open empirical question flagged throughout
// this project: whether PTM expects/returns raw ISO14443-3 bytes with
// separate parity, bytes with parity already folded in, or something else.
// mfc_transceive() is the single choke point to adjust once that's known
// against real hardware -- nothing above this layer (mfc_proto.c,
// cmdhfmfhard.c, hardnested_bf_core.c) needs to change.
#ifndef MFC_TRANSPORT_H
#define MFC_TRANSPORT_H

#include <stdint.h>
#include <stdbool.h>

// Sends tx_len bytes of tx (already including the 2-byte CRC_A the caller
// appended) together with one parity bit per byte packed MSB-first into
// tx_par (same layout mifareutil.c uses: par[0] bit 7 = parity of tx[0],
// bit 6 = parity of tx[1], ...). tx_par may be NULL, meaning "let the
// transport compute/attach standard odd parity itself" -- used for
// cleartext frames sent before authentication.
//
// Receives into rx (caller-allocated, rx_cap bytes) and, if rx_par is
// non-NULL, the per-byte parity bits of the response in the same packed
// format. Returns the number of bytes received (0 on timeout/no response,
// which for MIFARE Classic legitimately means "NACK-less OK" in a few
// write-path cases the caller already handles), or a negative value on a
// hard transport error.
int mfc_transceive(const uint8_t *tx, uint16_t tx_len, const uint8_t *tx_par,
                    uint8_t *rx, uint16_t rx_cap, uint8_t *rx_par);

// Convenience for callers that don't need explicit parity control (that
// standard odd parity per byte is computed by the transport itself) -- used
// for the one cleartext frame in this project (REQA-equivalent is not sent
// here at all, see docs/tms_protocol.md on tag selection already being
// handled by Android's own NFC stack before PTM is entered).
static inline int mfc_transceive_plain(const uint8_t *tx, uint16_t tx_len, uint8_t *rx, uint16_t rx_cap) {
    return mfc_transceive(tx, tx_len, NULL, rx, rx_cap, NULL);
}

#endif

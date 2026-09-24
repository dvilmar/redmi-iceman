package com.redmi.iceman.probe;

/**
 * JNI entry points into android/mifare-native/ (libmfcbridge.so), which
 * wraps upstream Iceman's crapto1 + hardnested engine + a lightly patched
 * cmdhfmfhard.c (see third_party/client_src/cmdhfmfhard.c's
 * "ANDROID/THN31 PATCH" comment) plus this project's own MIFARE Classic
 * protocol layer (mfc_proto.c) driven over PTM.
 */
public class MfcNative {
    static {
        System.loadLibrary("mfcbridge");
    }

    /**
     * Must be called once per tag presentation, after PtmTransport.open()
     * succeeded, before any of the three commands below.
     *
     * @param ptmTransport   the open PtmTransport instance; native code
     *                       calls its transceive(byte[], long) method.
     * @param resourcesDir   directory holding resources/hardnested_tables/
     *                       (see mfc_resources.h) -- extracted from app
     *                       assets on first run.
     */
    public static native int nativeInit(PtmTransport ptmTransport, String resourcesDir);

    public static native void nativeSetUid(int uid);

    /** Dictionary attack for Key A of `block`. Returns a 12-hex-char key, or null. */
    public static native String nativeAutopwn(int block);

    /**
     * hardnested: recovers Key B of trgBlock given a known Key A of block.
     * Returns a 12-hex-char key, or null on failure.
     */
    public static native String nativeHardnested(int block, String keyAHex, int trgBlock);

    /** value --inc: authenticates with Key B, increments, TRANSFERs. 0 on success. */
    public static native int nativeValueIncrement(int block, String keyBHex, int delta);
}

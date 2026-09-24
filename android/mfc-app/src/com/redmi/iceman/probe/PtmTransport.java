package com.redmi.iceman.probe;

import android.os.RemoteException;
import android.util.Log;
import com.tms.nfc.IHciAdapter;
import com.tms.nfc.IHciCallback;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous wrapper around the async com.tms.nfc.IHciAdapter (PTM)
 * Binder channel: open() registers a callback, transceive() sends and
 * fires-and-forgets, the response arrives later via onHciDataReceive().
 * This turns that into a plain blocking transceive(tx, timeout) -> rx call,
 * which is what the native mfc_transceive() JNI bridge (jni_bridge.c)
 * expects.
 *
 * Every TX/RX is hex-dumped to logcat (tag "PtmTransport"): the exact bytes
 * on this channel are the one thing still needed to pin down the real PTM
 * wire format (see jni_bridge.c's file header and docs/tms_protocol.md §7)
 * -- without root there is no NCI/HCI snoop log available, so this is the
 * only way to capture live traffic. Pull it with:
 *   adb logcat -s PtmTransport:D
 */
public class PtmTransport {
    private static final String TAG = "PtmTransport";
    private final IHciAdapter adapter;
    private final ArrayBlockingQueue<byte[]> rxQueue = new ArrayBlockingQueue<>(4);

    private final IHciCallback.Stub callback = new IHciCallback.Stub() {
        @Override
        public void onHciDataReceive(byte[] data) {
            Log.d(TAG, "RX (" + (data == null ? 0 : data.length) + "B): " + toHex(data));
            rxQueue.offer(data);
        }
    };

    public PtmTransport(IHciAdapter adapter) throws RemoteException {
        this.adapter = adapter;
        adapter.open(callback);
    }

    public void close() {
        try {
            adapter.close();
        } catch (RemoteException ignored) {
        }
    }

    /** Returns the response bytes, or null on timeout. */
    public byte[] transceive(byte[] tx, long timeoutMs) {
        rxQueue.clear();
        Log.d(TAG, "TX (" + tx.length + "B): " + toHex(tx));
        try {
            adapter.transceive(tx);
        } catch (RemoteException e) {
            Log.d(TAG, "TX failed: " + e);
            return null;
        }
        try {
            byte[] rx = rxQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (rx == null) Log.d(TAG, "RX timeout after " + timeoutMs + "ms");
            return rx;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String toHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) sb.append(String.format("%02x ", b));
        return sb.toString().trim();
    }
}

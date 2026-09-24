package com.redmi.iceman.probe;

import android.os.RemoteException;
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
 */
public class PtmTransport {
    private final IHciAdapter adapter;
    private final ArrayBlockingQueue<byte[]> rxQueue = new ArrayBlockingQueue<>(4);

    private final IHciCallback.Stub callback = new IHciCallback.Stub() {
        @Override
        public void onHciDataReceive(byte[] data) {
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
        try {
            adapter.transceive(tx);
        } catch (RemoteException e) {
            return null;
        }
        try {
            return rxQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}

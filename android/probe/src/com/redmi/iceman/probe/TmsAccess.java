package com.redmi.iceman.probe;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import com.tms.nfc.ITmsNfcAdapter;
import java.lang.reflect.Method;

/**
 * Bootstraps a handle to ITmsNfcAdapter.
 *
 * android.os.ServiceManager is a hidden framework class not in the public
 * SDK, so getService() is reached via reflection (not hidden-API blocked at
 * targetSdkVersion 27). android.nfc.INfcAdapter#getNfcAdapterVendorInterface
 * is hidden-API blocklist tier (not bypassable via reflection or exemption
 * tricks on this Android version), so that one call is made as a raw Binder
 * transaction instead. Transaction code, descriptor and marshalling order
 * below were read directly out of the on-device framework.jar's
 * INfcAdapter$Stub/$Stub$Proxy smali. Everything past that point uses the
 * plain AIDL stub classes recovered from com.tms.nfc.jar (public-API-safe:
 * Binder, IBinder, IInterface, Parcel only).
 */
public class TmsAccess {
    private static final String TAG = "TmsProbe";
    private static final String NFC_ADAPTER_DESCRIPTOR = "android.nfc.INfcAdapter";
    private static final int TRANSACTION_getNfcAdapterVendorInterface = 6;

    public static ITmsNfcAdapter getAdapter() throws Exception {
        Class<?> serviceManagerCls = Class.forName("android.os.ServiceManager");
        Method getService = serviceManagerCls.getMethod("getService", String.class);
        IBinder nfcBinder = (IBinder) getService.invoke(null, "nfc");
        if (nfcBinder == null) {
            throw new IllegalStateException("ServiceManager.getService(\"nfc\") returned null");
        }
        Log.d(TAG, "nfc binder obtained: " + nfcBinder);

        IBinder tmsBinder = getNfcAdapterVendorInterface(nfcBinder, "nfc_tms");
        if (tmsBinder == null) {
            throw new IllegalStateException("getNfcAdapterVendorInterface(\"nfc_tms\") returned null");
        }
        Log.d(TAG, "TMS vendor binder obtained: " + tmsBinder);

        return ITmsNfcAdapter.Stub.asInterface(tmsBinder);
    }

    private static IBinder getNfcAdapterVendorInterface(IBinder nfcBinder, String serviceName) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(NFC_ADAPTER_DESCRIPTOR);
            data.writeString(serviceName);
            nfcBinder.transact(TRANSACTION_getNfcAdapterVendorInterface, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}

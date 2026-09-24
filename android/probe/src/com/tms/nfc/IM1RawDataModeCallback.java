package com.tms.nfc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface IM1RawDataModeCallback extends IInterface {
    public static final String DESCRIPTOR = "com.tms.nfc.IM1RawDataModeCallback";

    void onM1RawDataModeAuthResult(int i) throws RemoteException;

    public static class Default implements IM1RawDataModeCallback {
        @Override // com.tms.nfc.IM1RawDataModeCallback
        public void onM1RawDataModeAuthResult(int authStatus) throws RemoteException {
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IM1RawDataModeCallback {
        static final int TRANSACTION_onM1RawDataModeAuthResult = 1;

        public Stub() {
            attachInterface(this, IM1RawDataModeCallback.DESCRIPTOR);
        }

        public static IM1RawDataModeCallback asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(IM1RawDataModeCallback.DESCRIPTOR);
            if (iin != null && (iin instanceof IM1RawDataModeCallback)) {
                return (IM1RawDataModeCallback) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code >= 1 && code <= 16777215) {
                data.enforceInterface(IM1RawDataModeCallback.DESCRIPTOR);
            }
            switch (code) {
                case 1598968902:
                    reply.writeString(IM1RawDataModeCallback.DESCRIPTOR);
                    return true;
                default:
                    switch (code) {
                        case 1:
                            int _arg0 = data.readInt();
                            data.enforceNoDataAvail();
                            onM1RawDataModeAuthResult(_arg0);
                            reply.writeNoException();
                            return true;
                        default:
                            return super.onTransact(code, data, reply, flags);
                    }
            }
        }

        private static class Proxy implements IM1RawDataModeCallback {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return IM1RawDataModeCallback.DESCRIPTOR;
            }

            @Override // com.tms.nfc.IM1RawDataModeCallback
            public void onM1RawDataModeAuthResult(int authStatus) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IM1RawDataModeCallback.DESCRIPTOR);
                    _data.writeInt(authStatus);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}

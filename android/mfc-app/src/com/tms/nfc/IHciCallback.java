package com.tms.nfc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface IHciCallback extends IInterface {
    public static final String DESCRIPTOR = "com.tms.nfc.IHciCallback";

    void onHciDataReceive(byte[] bArr) throws RemoteException;

    public static class Default implements IHciCallback {
        @Override // com.tms.nfc.IHciCallback
        public void onHciDataReceive(byte[] data) throws RemoteException {
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IHciCallback {
        static final int TRANSACTION_onHciDataReceive = 1;

        public Stub() {
            attachInterface(this, IHciCallback.DESCRIPTOR);
        }

        public static IHciCallback asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(IHciCallback.DESCRIPTOR);
            if (iin != null && (iin instanceof IHciCallback)) {
                return (IHciCallback) iin;
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
                data.enforceInterface(IHciCallback.DESCRIPTOR);
            }
            switch (code) {
                case 1598968902:
                    reply.writeString(IHciCallback.DESCRIPTOR);
                    return true;
                default:
                    switch (code) {
                        case 1:
                            byte[] _arg0 = data.createByteArray();
                            data.enforceNoDataAvail();
                            onHciDataReceive(_arg0);
                            reply.writeNoException();
                            return true;
                        default:
                            return super.onTransact(code, data, reply, flags);
                    }
            }
        }

        private static class Proxy implements IHciCallback {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return IHciCallback.DESCRIPTOR;
            }

            @Override // com.tms.nfc.IHciCallback
            public void onHciDataReceive(byte[] data) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IHciCallback.DESCRIPTOR);
                    _data.writeByteArray(data);
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

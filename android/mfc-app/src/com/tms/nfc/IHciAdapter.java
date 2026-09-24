package com.tms.nfc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface IHciAdapter extends IInterface {
    public static final String DESCRIPTOR = "com.tms.nfc.IHciAdapter";

    void close() throws RemoteException;

    void open(IHciCallback iHciCallback) throws RemoteException;

    void transceive(byte[] bArr) throws RemoteException;

    public static class Default implements IHciAdapter {
        @Override // com.tms.nfc.IHciAdapter
        public void open(IHciCallback callback) throws RemoteException {
        }

        @Override // com.tms.nfc.IHciAdapter
        public void close() throws RemoteException {
        }

        @Override // com.tms.nfc.IHciAdapter
        public void transceive(byte[] data) throws RemoteException {
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IHciAdapter {
        static final int TRANSACTION_close = 2;
        static final int TRANSACTION_open = 1;
        static final int TRANSACTION_transceive = 3;

        public Stub() {
            attachInterface(this, IHciAdapter.DESCRIPTOR);
        }

        public static IHciAdapter asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(IHciAdapter.DESCRIPTOR);
            if (iin != null && (iin instanceof IHciAdapter)) {
                return (IHciAdapter) iin;
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
                data.enforceInterface(IHciAdapter.DESCRIPTOR);
            }
            switch (code) {
                case 1598968902:
                    reply.writeString(IHciAdapter.DESCRIPTOR);
                    return true;
                default:
                    switch (code) {
                        case 1:
                            IHciCallback _arg0 = IHciCallback.Stub.asInterface(data.readStrongBinder());
                            data.enforceNoDataAvail();
                            open(_arg0);
                            reply.writeNoException();
                            return true;
                        case 2:
                            close();
                            reply.writeNoException();
                            return true;
                        case 3:
                            byte[] _arg1 = data.createByteArray();
                            data.enforceNoDataAvail();
                            transceive(_arg1);
                            reply.writeNoException();
                            return true;
                        default:
                            return super.onTransact(code, data, reply, flags);
                    }
            }
        }

        private static class Proxy implements IHciAdapter {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return IHciAdapter.DESCRIPTOR;
            }

            @Override // com.tms.nfc.IHciAdapter
            public void open(IHciCallback callback) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IHciAdapter.DESCRIPTOR);
                    _data.writeStrongInterface(callback);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.IHciAdapter
            public void close() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IHciAdapter.DESCRIPTOR);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.IHciAdapter
            public void transceive(byte[] data) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IHciAdapter.DESCRIPTOR);
                    _data.writeByteArray(data);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}

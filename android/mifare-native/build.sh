#!/usr/bin/env bash
# Rebuilds libmfcnative.a (arm64-v8a) from third_party/ (verbatim upstream
# Iceman sources, lightly patched only where noted in-file) + src/ (this
# project's own code: protocol layer, transport seam, JNI bridge, resource
# loading). See docs/tms_protocol.md and research/ for the design.
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_NDK:=$HOME/Android/Sdk/ndk/26.3.11579264}"
TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$TOOLCHAIN/aarch64-linux-android26-clang"
AR="$TOOLCHAIN/llvm-ar"

INCLUDES="-Isrc -Ithird_party -Ithird_party/common -Ithird_party/crapto1 -Ithird_party/hardnested -Ithird_party/client_src -Ithird_party/lz4 -Ithird_party/stub"
CFLAGS="-O2 -Wall -fPIC"

rm -rf obj
mkdir -p obj

SOURCES="
third_party/crapto1/crapto1.c
third_party/crapto1/crypto1.c
third_party/hardnested/hardnested_bf_core.c
third_party/hardnested/hardnested_bitarray_core.c
third_party/hardnested_full/hardnested_bruteforce.c
third_party/common/bucketsort.c
third_party/common/crc16.c
third_party/common/commonutil.c
third_party/client_src/cmdhfmfhard.c
third_party/lz4/lz4.c
third_party/lz4/lz4hc.c
third_party/lz4/lz4frame.c
third_party/lz4/xxhash.c
third_party/stub/ui_and_globals_stub.c
src/mfc_proto.c
src/mfc_dict.c
src/mfc_resources.c
src/mfc_bz2_stub.c
"

for f in $SOURCES; do
    echo "CC $f"
    extra_flags=""
    # LZ4_HEAPMODE=1: lz4frame.c's compression path (LZ4F_createCDict etc,
    # never actually called -- we only ever decompress) references
    # LZ4_createStream/LZ4_freeStream, which lz4.c only compiles in under
    # this flag. We link the whole lz4.o either way (static archives pull
    # in a full .o once anything in it is referenced), so this just needs
    # to be defined, not actually exercised at runtime.
    case "$f" in
        third_party/lz4/lz4.c) extra_flags="-DLZ4_HEAPMODE=1" ;;
    esac
    "$CC" -c "$f" -o "obj/$(basename "$f" .c).o" $INCLUDES $CFLAGS -DXXH_NAMESPACE=LZ4_ $extra_flags
done

# Same NOSIMD_BUILD dispatch-glue pattern as hardnested_bf_core.c above
# (malloc_bitarray/bitarray_AND/bitarray_AND4/bitarray_OR/count_bitarray_*).
echo "CC third_party/hardnested/hardnested_bitarray_core.c (NOSIMD_BUILD dispatch variant)"
"$CC" -c third_party/hardnested/hardnested_bitarray_core.c -o obj/hardnested_bitarray_core_dispatch.o $INCLUDES $CFLAGS -DNOSIMD_BUILD

# hardnested_bf_core.c is compiled a 2nd time with -DNOSIMD_BUILD: that flag
# (counter-intuitively) selects the runtime SIMD-dispatch glue
# (GetSIMDInstrAuto/crack_states_bitsliced/bitslice_test_nonces) rather than
# a specific *_NEON/_AVX2/... implementation -- upstream's own build
# compiles this file multiple times per target ISA and links the results
# together the same way. No symbol overlap between the two (verified).
echo "CC third_party/hardnested/hardnested_bf_core.c (NOSIMD_BUILD dispatch variant)"
"$CC" -c third_party/hardnested/hardnested_bf_core.c -o obj/hardnested_bf_core_dispatch.o $INCLUDES $CFLAGS -DNOSIMD_BUILD

echo "AR libmfcnative.a"
rm -f libmfcnative.a # ar rcs on an existing archive doesn't drop stale members
"$AR" rcs libmfcnative.a obj/*.o

echo "CC src/jni_bridge.c"
mkdir -p obj_bridge
"$CC" -c src/jni_bridge.c -o obj_bridge/jni_bridge.o $INCLUDES $CFLAGS

echo "LINK libmfcbridge.so"
"$CC" -shared -o libmfcbridge.so obj_bridge/jni_bridge.o -Wl,--whole-archive libmfcnative.a -Wl,--no-whole-archive -llog -lm

echo "done: $(pwd)/libmfcbridge.so"

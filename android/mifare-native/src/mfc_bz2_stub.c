// Real bzip2 is never linked in: none of the bundled hardnested_tables/*
// files are .bz2 (verified against upstream's own resources directory --
// only .lz4), so this code path in cmdhfmfhard.c's init_bitflip_bitarrays()
// is reachable in theory (the compiler can't prove otherwise) but never
// taken in practice. These three stubs exist purely to satisfy the linker;
// returning "not OK" makes that dead branch behave as a clean failure if
// it's ever somehow reached.
#include "bzlib.h"

int BZ2_bzDecompressInit(bz_stream *strm, int verbosity, int small) {
    (void)strm; (void)verbosity; (void)small;
    return -1;
}
int BZ2_bzDecompress(bz_stream *strm) {
    (void)strm;
    return -1;
}
int BZ2_bzDecompressEnd(bz_stream *strm) {
    (void)strm;
    return -1;
}

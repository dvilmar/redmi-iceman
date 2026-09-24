// Minimal stand-in for bzlib.h. cmdhfmfhard.c's init_bunzip2()/the whole
// "open_bz2compressed" branch in init_bitflip_bitarrays() is dead code for
// us: none of the shipped hardnested_tables/*.lz4 files are .bz2 (verified
// against upstream's own resources directory), only .lz4. Declared here
// just so the file compiles; never actually called at runtime.
#ifndef MFC_STUB_BZLIB_H
#define MFC_STUB_BZLIB_H

#define BZ_OK 0
#define BZ_STREAM_END 4

typedef struct {
    char *next_in;
    unsigned int avail_in;
    unsigned int total_in_lo32;
    unsigned int total_in_hi32;
    char *next_out;
    unsigned int avail_out;
    unsigned int total_out_lo32;
    unsigned int total_out_hi32;
    void *state;
    void *(*bzalloc)(void *, int, int);
    void (*bzfree)(void *, void *);
    void *opaque;
} bz_stream;

int BZ2_bzDecompressInit(bz_stream *strm, int verbosity, int small);
int BZ2_bzDecompress(bz_stream *strm);
int BZ2_bzDecompressEnd(bz_stream *strm);

#endif

// Stand-in for client/src/util_posix.h providing real, working
// implementations of just the three symbols cmdhfmfhard.c uses
// (msclock/num_CPUs/get_my_executable_directory), backed by
// util_posix_stub.c. Not copied from upstream.
#ifndef MFC_STUB_UTIL_POSIX_H
#define MFC_STUB_UTIL_POSIX_H

#include <stdint.h>
#include <pthread.h> // real util_posix.h pulls this in too; cmdhfmfhard.c relies on it transitively

uint64_t msclock(void);
int num_CPUs(void);

// Returns the base directory mfc_resources_set_base_dir() was last called
// with (see mfc_resources.h) -- searchFile()/fileutils.h resolve
// hardnested_tables/*.lz4 relative to it. Never NULL (empty string if
// unset).
const char *get_my_executable_directory(void);

#endif

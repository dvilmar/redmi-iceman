// Real implementations backing the util_posix.h/fileutils.h stubs that
// cmdhfmfhard.c (third_party/client_src/, lightly patched) and
// hardnested_bruteforce.c (third_party/hardnested/) need. Own code, not
// copied from upstream.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <stdbool.h>
#include <sys/stat.h>

#include "mfc_resources.h"

static char g_base_dir[1024] = {0};

void mfc_resources_set_base_dir(const char *dir) {
    if (dir == NULL) {
        g_base_dir[0] = '\0';
        return;
    }
    snprintf(g_base_dir, sizeof(g_base_dir), "%s", dir);
}

const char *get_my_executable_directory(void) {
    return g_base_dir;
}

uint64_t msclock(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)(ts.tv_nsec / 1000000ULL);
}

int num_CPUs(void) {
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    return (n < 1) ? 1 : (int)n;
}

// fileutils.h's searchFile(): resolves <base_dir>/<pm3dir><searchname><suffix>
// and, if it exists, strdup()s the path into *foundpath (caller frees it,
// matching upstream's contract). No multi-directory search chain like
// upstream's real implementation -- we only ever have the one bundled
// resources tree.
int searchFile(char **foundpath, const char *pm3dir, const char *searchname, const char *suffix, bool silent) {
    (void)silent;
    if (foundpath == NULL || searchname == NULL) {
        return -2; // PM3_EINVARG
    }

    char path[2048];
    snprintf(path, sizeof(path), "%s/%s%s%s", g_base_dir, pm3dir ? pm3dir : "", searchname, suffix ? suffix : "");

    struct stat st;
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) {
        return -13; // PM3_EFILE (matches upstream's include/pm3_cmd.h value)
    }

    *foundpath = strdup(path);
    if (*foundpath == NULL) {
        return -12; // PM3_EMALLOC
    }
    return 0; // PM3_SUCCESS
}

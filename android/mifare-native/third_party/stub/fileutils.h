// Stand-in for client/src/fileutils.h providing only searchFile() (the one
// symbol cmdhfmfhard.c/hardnested_bruteforce.c actually call), backed by a
// real implementation in mfc_resources.c. Not copied from upstream --
// upstream's real fileutils.h pulls in jansson (JSON) for functions we
// don't use.
#ifndef MFC_STUB_FILEUTILS_H
#define MFC_STUB_FILEUTILS_H

#include <stdbool.h>

int searchFile(char **foundpath, const char *pm3dir, const char *searchname, const char *suffix, bool silent);

#endif

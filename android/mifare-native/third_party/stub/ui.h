// Minimal stand-in for client/src/ui.h, providing only the symbol
// hardnested_bf_core.c actually references (PrintAndLogEx + logLevel_t).
// Not copied from upstream: upstream's ui.h pulls in comms.h (full PM3
// device link), which this static lib has no use for.
#ifndef MIFARE_NATIVE_UI_STUB_H
#define MIFARE_NATIVE_UI_STUB_H

typedef enum logLevel {NORMAL, SUCCESS, INFO, FAILED, WARNING, ERR, DEBUG, INPLACE, HINT} logLevel_t;

void PrintAndLogEx(logLevel_t level, const char *fmt, ...);

// ANSI-color-wrapping macros upstream's ui.h provides for terminal output;
// no-op passthrough here since this project has no terminal to color.
#define _YELLOW_(x) x
#define _GREEN_(x) x
#define _RED_(x) x
#define _CYAN_(x) x

#endif

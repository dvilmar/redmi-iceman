// Own code, not from upstream. Two things hardnested_bf_core.c/common.h
// need that only exist as part of the full PM3 client app: a PrintAndLogEx
// implementation and the g_dbglevel/g_tearoff_* globals declared extern in
// common.h. Logging here is a plain stderr printf; the Android JNI layer
// can swap this file for one that calls __android_log_vprint if on-device
// logcat output is wanted instead.
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include "ui.h"

int g_dbglevel = 0;
uint16_t g_tearoff_delay_us = 0;
bool g_tearoff_enabled = false;

void PrintAndLogEx(logLevel_t level, const char *fmt, ...) {
    (void)level;
    va_list args;
    va_start(args, fmt);
    vfprintf(stderr, fmt, args);
    va_end(args);
    fputc('\n', stderr);
}

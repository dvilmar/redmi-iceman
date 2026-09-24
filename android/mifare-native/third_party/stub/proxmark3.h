// Empty stand-in for client/src/proxmark3.h (global client app state:
// current device handle, CLI context, etc). Not needed once acquire_nonces()
// no longer talks to a device directly -- kept only so the #include in
// cmdhfmfhard.c/hardnested_bruteforce.c resolves.
#ifndef MFC_STUB_PROXMARK3_H
#define MFC_STUB_PROXMARK3_H
#endif

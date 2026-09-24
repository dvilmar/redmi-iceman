// Empty stand-in for client/src/comms.h (the full USB/PM3-device link).
// cmdhfmfhard.c's only use of it (SendCommandNG/WaitForResponseTimeout/
// clearCommandBuffer/DropField, inside the original acquire_nonces()) was
// replaced by mfc_nested_*() -- see the ANDROID/THN31 PATCH comment in
// that file. Nothing here is ever called.
#ifndef MFC_STUB_COMMS_H
#define MFC_STUB_COMMS_H
#include "pm3_cmd.h" // real upstream header; only PM3_* status codes are actually used here
#endif

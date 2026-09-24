// Sets the base directory that searchFile() (fileutils stub) and
// get_my_executable_directory() (util_posix stub) resolve resources
// against. Must be called once, before mfnestedhard()/acquire_nonces()
// run, with the directory the app extracted the bundled
// third_party/hardnested_tables/ assets into. Expected layout:
//   <base_dir>/resources/hardnested_tables/bitflip_*_states.bin.lz4
// i.e. <base_dir> is what upstream would call the "executable directory";
// everything under resources/ is copied byte-for-byte from upstream's own
// client/resources/hardnested_tables/.
#ifndef MFC_RESOURCES_H
#define MFC_RESOURCES_H

void mfc_resources_set_base_dir(const char *dir);

#endif

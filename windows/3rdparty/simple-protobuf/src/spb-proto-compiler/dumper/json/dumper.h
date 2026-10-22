#pragma once

#include "ast/proto-file.h"
#include <filesystem>

void dump_json_header( const proto_file & file, std::ostream & stream );

void dump_json_cpp( const proto_file & file, const std::filesystem::path & header_file,
                    std::ostream & stream );

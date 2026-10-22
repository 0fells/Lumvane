#pragma once

#include "ast/proto-file.h"
#include <filesystem>

void dump_cpp_header( const proto_file & file, std::ostream & header_file );

void dump_cpp( const proto_file & file, const std::filesystem::path & header_file,
               std::ostream & file_stream );

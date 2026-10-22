#pragma once

#include "ast/proto-file.h"
#include <filesystem>

void dump_cpp_definitions( const proto_file & file, std::ostream & stream );

auto replace( std::string_view input, std::string_view what, std::string_view with ) -> std::string;

[[noreturn]] void throw_parse_error( const proto_file & file, std::string_view at,
                                     std::string_view message );

#pragma once

#include "ast/proto-file.h"
#include <filesystem>
#include <span>
#include <string_view>

[[nodiscard]] auto cpp_file_name_from_proto( const std::filesystem::path & proto_file_path,
                                             std::string_view extension ) -> std::filesystem::path;

auto parse_proto_file(
    const std::filesystem::path & file_path, std::span< const std::filesystem::path > import_paths,
    const std::filesystem::path & base_dir = std::filesystem::current_path( ) ) -> proto_file;

void parse_proto_file_content( proto_file & file );

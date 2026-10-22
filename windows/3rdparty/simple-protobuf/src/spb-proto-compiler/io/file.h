#pragma once

#include <filesystem>
#include <string>
#include <string_view>

auto load_file( const std::filesystem::path & file_path ) -> std::string;

void save_file( const std::filesystem::path & file_path, std::string_view file_content );

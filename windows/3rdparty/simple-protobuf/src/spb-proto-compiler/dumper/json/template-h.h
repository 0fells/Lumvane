#pragma once

#include <string_view>

constexpr std::string_view file_json_header_template = R"(
void serialize_value( ostream & stream, const $ & value );
void deserialize_value( istream & stream, $ & value );

)";

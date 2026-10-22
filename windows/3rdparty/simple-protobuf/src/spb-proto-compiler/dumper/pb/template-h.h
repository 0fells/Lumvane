#pragma once

#include <string_view>

constexpr std::string_view file_pb_header_template = R"(
void serialize( ostream & stream, const $ & value );
void deserialize_value( istream & stream, $ & value, uint32_t tag );

)";

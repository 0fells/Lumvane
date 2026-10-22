#pragma once

#include "proto-comment.h"
#include <cstdint>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

using proto_reserved_range = std::vector< std::pair< int32_t, int32_t > >;
using proto_reserved_name  = std::unordered_set< std::string_view >;
using proto_options        = std::unordered_map< std::string_view, std::string_view >;

struct proto_reserved
{
    proto_reserved_range reserved_range;
    proto_reserved_name reserved_name;
};

struct cpp_ident
{
    std::string_view proto_name{};

    std::string cpp_name{};

    auto get_name( ) const -> std::string_view
    {
        return cpp_name.empty( ) ? proto_name : std::string_view( cpp_name );
    }
};

struct proto_base
{
    cpp_ident name;

    int32_t number;

    proto_options options;
    proto_comment comment;
};

using proto_bases = std::vector< proto_base >;

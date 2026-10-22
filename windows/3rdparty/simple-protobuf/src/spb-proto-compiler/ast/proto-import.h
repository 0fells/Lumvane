#pragma once

#include "proto-comment.h"
#include <string_view>

struct proto_import
{
    std::string_view file_name;

    proto_comment comments;
};

using proto_imports = std::vector< proto_import >;
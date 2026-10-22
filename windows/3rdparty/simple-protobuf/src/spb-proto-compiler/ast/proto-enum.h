#pragma once

#include "proto-common.h"
#include <vector>

struct proto_enum : public proto_base
{
    proto_bases fields;
    proto_reserved reserved;
};

using proto_enums = std::vector< proto_enum >;

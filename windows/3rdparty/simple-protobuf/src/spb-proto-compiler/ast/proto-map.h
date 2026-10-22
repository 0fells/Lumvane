#pragma once

#include "ast/proto-field.h"
#include "proto-common.h"

struct proto_map : public proto_base
{
    proto_field key;
    proto_field value;
};

using proto_maps = std::vector< proto_map >;
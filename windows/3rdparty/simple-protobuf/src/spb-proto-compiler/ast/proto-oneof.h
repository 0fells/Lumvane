#pragma once

#include "proto-common.h"
#include "proto-field.h"
#include <vector>

struct proto_oneof : public proto_base
{
    proto_fields fields;
};

using proto_oneofs = std::vector< proto_oneof >;

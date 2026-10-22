#pragma once

#include "proto-common.h"
#include "proto-enum.h"
#include "proto-field.h"
#include "proto-map.h"
#include "proto-oneof.h"
#include <set>
#include <string_view>
#include <vector>

struct proto_message;
using proto_messages         = std::vector< proto_message >;
using forwarded_declarations = std::set< std::string_view >;

struct proto_message : public proto_base
{
    proto_fields fields;
    proto_reserved_range extensions;
    proto_messages messages;
    proto_maps maps;
    proto_oneofs oneofs;
    proto_enums enums;
    proto_reserved reserved;

    size_t resolved = 0;
    forwarded_declarations forwards;
};

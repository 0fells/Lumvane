#pragma once

#include "proto-common.h"
#include <spb/pb/wire-types.h>
#include <string_view>
#include <vector>

struct proto_field : public proto_base
{
    enum Type
    {
        NONE,
        BOOL,
        BYTES,
        DOUBLE,
        ENUM,
        FLOAT,
        FIXED32,
        FIXED64,
        INT32,
        INT64,
        MESSAGE,
        SFIXED32,
        SFIXED64,
        SINT32,
        SINT64,
        STRING,
        UINT32,
        UINT64,
    };

    enum class BitType
    {
        NONE,
        INT8,
        INT16,
        INT32,
        INT64,
        UINT8,
        UINT16,
        UINT32,
        UINT64,
    };

    enum class Label
    {
        NONE = 0,
        OPTIONAL = 1,
        REPEATED = 2,
        PTR = 3,
    };

    Type type   = Type::NONE;
    Label label = Label::OPTIONAL;

    cpp_ident type_name;

    BitType bit_type = BitType::NONE;
    std::string_view bit_field;
};

using proto_fields = std::vector< proto_field >;

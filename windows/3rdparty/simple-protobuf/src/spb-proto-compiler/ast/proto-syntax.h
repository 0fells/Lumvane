#pragma once

#include "proto-comment.h"
#include <cstdint>

struct proto_syntax
{
    uint32_t version = 2;
    proto_comment comments;
};

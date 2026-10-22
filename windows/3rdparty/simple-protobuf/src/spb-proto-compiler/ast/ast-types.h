#pragma once

#include "ast/proto-field.h"
#include "proto-file.h"

[[nodiscard]] auto is_scalar( const proto_field::Type & type ) -> bool;
[[nodiscard]] auto is_packed_array( const proto_file & file, const proto_field & field ) -> bool;

void resolve_types( proto_file & file );

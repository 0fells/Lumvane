#pragma once

#include "ast/proto-field.h"
#include "proto-file.h"

[[nodiscard]] auto get_encoder( const proto_field & field ) -> proto_field::Type;

void resolve_messages( proto_file & file );

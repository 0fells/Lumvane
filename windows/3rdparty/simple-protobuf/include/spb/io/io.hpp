#pragma once
#include "function_ref.hpp"
#include <cstdlib>

namespace spb::io
{
using writer = spb::detail::function_ref< void( const void * p_data, size_t size ) >;

using reader = spb::detail::function_ref< size_t( void * p_data, size_t size ) >;

}

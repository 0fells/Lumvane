#pragma once

#include <cstdint>
#include <type_traits>

namespace spb::pb::detail
{

enum class wire_type : uint8_t
{
    varint = 0,
    fixed64 = 1,
    length_delimited = 2,
    StartGroup = 3,
    EndGroup = 4,
    fixed32 = 5
};

enum class scalar_encoder : uint8_t
{
    varint = 0x01,
    svarint = 0x02,
    i32 = 0x03,
    i64 = 0x04,
    packed = 0x08
};

static constexpr scalar_encoder operator&( scalar_encoder lhs, scalar_encoder rhs ) noexcept
{
    return scalar_encoder( std::underlying_type_t< scalar_encoder >( lhs ) &
                           std::underlying_type_t< scalar_encoder >( rhs ) );
}

static constexpr scalar_encoder operator|( scalar_encoder lhs, scalar_encoder rhs ) noexcept
{
    return scalar_encoder( std::underlying_type_t< scalar_encoder >( lhs ) |
                           std::underlying_type_t< scalar_encoder >( rhs ) );
}

static constexpr auto scalar_encoder_combine( scalar_encoder type1, scalar_encoder type2 ) noexcept
    -> scalar_encoder
{
    return scalar_encoder( ( std::underlying_type_t< scalar_encoder >( type1 ) & 0x0f ) |
                           ( ( std::underlying_type_t< scalar_encoder >( type2 ) & 0x0f ) << 4 ) );
}

static constexpr auto scalar_encoder_is_packed( scalar_encoder a ) noexcept -> bool
{
    return ( a & scalar_encoder::packed ) == scalar_encoder::packed;
}

static constexpr auto scalar_encoder_type1( scalar_encoder a ) noexcept -> scalar_encoder
{
    return scalar_encoder( static_cast< std::underlying_type_t< scalar_encoder > >( a ) & 0x07 );
}

static constexpr auto scalar_encoder_type2( scalar_encoder a ) noexcept -> scalar_encoder
{
    return scalar_encoder( ( static_cast< std::underlying_type_t< scalar_encoder > >( a ) >> 4 ) &
                           0x07 );
}

static constexpr auto wire_type_from_scalar_encoder( scalar_encoder a ) noexcept -> wire_type
{
    switch( scalar_encoder_type1( a ) )
    {
    case scalar_encoder::i32:
        return wire_type::fixed32;
    case scalar_encoder::i64:
        return wire_type::fixed64;
    default:
        return wire_type::varint;
    }
}

}

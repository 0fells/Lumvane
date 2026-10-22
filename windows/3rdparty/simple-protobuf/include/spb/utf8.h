
#pragma once

#include <climits>
#include <cstdint>
#include <stdexcept>
#include <string_view>

namespace spb::detail::utf8
{

constexpr uint8_t ok = 0;

static auto inline decode_point( uint32_t * state, uint32_t * codep, uint8_t byte ) -> uint32_t
{
    static const uint8_t utf8d[] = {
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
        1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,
        9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,
        7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,
        7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,
        8,   8,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,
        2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,
        0xa, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x4, 0x3, 0x3,
        0xb, 0x6, 0x6, 0x6, 0x5, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8,
        0x0, 0x1, 0x2, 0x3, 0x5, 0x8, 0x7, 0x1, 0x1, 0x1, 0x4, 0x6, 0x1, 0x1, 0x1, 0x1,
        1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,
        1,   0,   1,   1,   1,   1,   1,   0,   1,   0,   1,   1,   1,   1,   1,   1,
        1,   2,   1,   1,   1,   1,   1,   2,   1,   2,   1,   1,   1,   1,   1,   1,
        1,   1,   1,   1,   1,   1,   1,   2,   1,   1,   1,   1,   1,   1,   1,   1,
        1,   2,   1,   1,   1,   1,   1,   1,   1,   2,   1,   1,   1,   1,   1,   1,
        1,   1,   1,   1,   1,   1,   1,   3,   1,   3,   1,   1,   1,   1,   1,   1,
        1,   3,   1,   1,   1,   1,   1,   3,   1,   3,   1,   1,   1,   1,   1,   1,
        1,   3,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,
    };

    uint32_t type = utf8d[ byte ];

    *codep = ( *state != ok ) ? ( byte & 0x3fu ) | ( *codep << 6 ) : ( 0xff >> type ) & ( byte );

    *state = utf8d[ 256 + *state * 16 + type ];
    return *state;
}

static inline auto encode_point( uint32_t unicode, char utf8[ 4 ] ) -> uint32_t
{
    if( unicode <= 0x7F )
    {
        utf8[ 0 ] = ( char ) unicode;
        return 1;
    }
    if( unicode <= 0x7FF )
    {
        utf8[ 0 ] = ( char ) ( ( unicode >> 6 ) | 0xC0 );
        utf8[ 1 ] = ( char ) ( ( unicode & 0x3F ) | 0x80 );
        return 2;
    }
    if( unicode >= 0xD800 && unicode < 0xE000 )
    {
        return 0;
    }
    if( unicode <= 0xFFFF )
    {
        utf8[ 0 ] = ( char ) ( ( unicode >> 12 ) | 0xE0 );
        utf8[ 1 ] = ( char ) ( ( ( unicode >> 6 ) & 0x3F ) | 0x80 );
        utf8[ 2 ] = ( char ) ( ( unicode & 0x3F ) | 0x80 );
        return 3;
    }
    if( unicode <= 0x10FFFF )
    {
        utf8[ 0 ] = ( char ) ( ( unicode >> 18 ) | 0xF0 );
        utf8[ 1 ] = ( char ) ( ( ( unicode >> 12 ) & 0x3F ) | 0x80 );
        utf8[ 2 ] = ( char ) ( ( ( unicode >> 6 ) & 0x3F ) | 0x80 );
        utf8[ 3 ] = ( char ) ( ( unicode & 0x3F ) | 0x80 );
        return 4;
    }
    return 0;
}

static inline auto is_valid( std::string_view str ) -> bool
{
    uint32_t codepoint;
    uint32_t state = ok;

    for( uint8_t c : str )
    {
        decode_point( &state, &codepoint, c );
    }

    return state == ok;
}

static inline void validate( std::string_view value )
{
    if( !spb::detail::utf8::is_valid( std::string_view( value.data( ), value.size( ) ) ) )
    {
        throw std::runtime_error( "invalid utf8 string" );
    }
}

}
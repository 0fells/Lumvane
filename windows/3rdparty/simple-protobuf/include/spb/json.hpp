#pragma once

#include "spb/io/io.hpp"
#include "json/deserialize.hpp"
#include "json/serialize.hpp"
#include <cstdlib>

namespace spb::json
{

static inline auto serialize( const auto & message, spb::io::writer on_write ) -> size_t
{
    return detail::serialize( message, on_write );
}

[[nodiscard]] static inline auto serialize_size( const auto & message ) -> size_t
{
    return serialize( message, spb::io::writer( nullptr ) );
}

template < typename Message, spb::resizable_container Container >
static inline auto serialize( const Message & message, Container & result ) -> size_t
{
    const auto size = serialize_size( message );
    result.resize( size );
    auto writer = [ ptr = result.data( ) ]( const void * data, size_t size ) mutable
    {
        memcpy( ptr, data, size );
        ptr += size;
    };

    serialize( message, writer );
    return size;
}

template < spb::resizable_container Container = std::string, typename Message >
[[nodiscard]] static inline auto serialize( const Message & message ) -> Container
{
    auto result = Container( );
    serialize< Message, Container >( message, result );
    return result;
}

static inline void deserialize( auto & result, spb::io::reader on_read )
{
    return detail::deserialize( result, on_read );
}

template < typename Message, spb::size_container Container >
static inline void deserialize( Message & message, const Container & json )
{
    auto reader = [ ptr = json.data( ), end = json.data( ) + json.size( ) ](
                      void * data, size_t size ) mutable -> size_t
    {
        size_t bytes_left = end - ptr;

        size = std::min( size, bytes_left );
        memcpy( data, ptr, size );
        ptr += size;
        return size;
    };
    return deserialize( message, reader );
}

template < typename Message, spb::size_container Container >
[[nodiscard]] static inline auto deserialize( const Container & json ) -> Message
{
    auto message = Message{ };
    deserialize( message, json );
    return message;
}

template < typename Message >
[[nodiscard]] static inline auto deserialize( spb::io::reader on_read ) -> Message
{
    auto message = Message{ };
    return deserialize( message, on_read );
}

}

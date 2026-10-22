#pragma once

#include "concepts.h"
#include "pb/deserialize.hpp"
#include "pb/serialize.hpp"
#include "spb/io/io.hpp"
#include <cstdlib>

namespace spb::pb
{

struct serialize_options
{
    bool delimited = false;
};

struct deserialize_options
{
    bool delimited = false;
};

static inline auto serialize( const auto & message, spb::io::writer on_write,
                              const serialize_options & options = { } ) -> size_t
{
    auto stream = detail::ostream{ on_write };
    if( options.delimited )
    {
        detail::serialize_varint( stream, detail::serialize_size( message ) );
    }
    serialize( stream, message );
    return stream.size( );
}

[[nodiscard]] static inline auto serialize_size( const auto & message,
                                                 const serialize_options & options = { } ) -> size_t
{
    return serialize( message, spb::io::writer( nullptr ), options );
}

template < typename Message, spb::resizable_container Container >
static inline auto serialize( const Message & message, Container & result,
                              const serialize_options & options = { } ) -> size_t
{
    const auto size = serialize_size( message, options );
    result.resize( size );
    auto writer = [ ptr = result.data( ) ]( const void * data, size_t size ) mutable
    {
        memcpy( ptr, data, size );
        ptr += size;
    };

    serialize( message, writer, options );
    return size;
}

template < spb::resizable_container Container = std::string, typename Message >
[[nodiscard]] static inline auto serialize( const Message & message,
                                            const serialize_options & options = { } ) -> Container
{
    auto result = Container( );
    serialize< Message, Container >( message, result, options );
    return result;
}

static inline void deserialize( auto & message, spb::io::reader reader,
                                const deserialize_options & options = { } )
{
    detail::istream stream{ reader };
    if( options.delimited )
    {
        const auto substream_length = read_varint< uint32_t >( stream );
        auto substream              = stream.sub_stream( substream_length );
        return deserialize_main( substream, message );
    }
    else
    {
        return deserialize_main( stream, message );
    }
}

template < typename Message, spb::size_container Container >
static inline void deserialize( Message & message, const Container & protobuf,
                                const deserialize_options & options = { } )
{
    auto reader = [ ptr = protobuf.data( ), end = protobuf.data( ) + protobuf.size( ) ](
                      void * data, size_t size ) mutable -> size_t
    {
        size_t bytes_left = end - ptr;

        size = std::min( size, bytes_left );
        memcpy( data, ptr, size );
        ptr += size;
        return size;
    };
    return deserialize( message, reader, options );
}

template < typename Message, spb::size_container Container >
[[nodiscard]] static inline auto deserialize( const Container & protobuf,
                                              const deserialize_options & options = { } ) -> Message
{
    auto message = Message{ };
    deserialize( message, protobuf, options );
    return message;
}

template < typename Message >
[[nodiscard]] static inline auto deserialize( spb::io::reader reader,
                                              const deserialize_options & options = { } ) -> Message
{
    auto message = Message{ };
    deserialize( message, reader, options );
    return message;
}

}
